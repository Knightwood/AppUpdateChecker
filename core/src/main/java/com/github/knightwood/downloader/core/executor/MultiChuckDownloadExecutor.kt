package com.github.knightwood.downloader.core.executor

import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.filestorage.RandomAccessFileStorageImpl
import com.github.knightwood.downloader.core.model.ChunkProgress
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.util.HttpConnectionUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import okio.buffer
import okio.source
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * 多线程分块下载执行器。
 *
 * - 若服务端支持 `Accept-Ranges: bytes` 且文件 >= [minChunkSize], 则并发分块。
 * - 否则退化为 [DownloadExecutor] 的单连接下载。
 * - 每个分块可独立报告进度, 支持断点续传 (通过 [ChunkProgress.downloaded])。
 */
open class MultiChunkDownloadExecutor(
    protected val chunkCount: Int = HttpConstant.DEFAULT_CHUNK_COUNT,
    protected val minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE,
    lastDownloadProgress: DownloadStatus.DOWNLOADING? = null,
) : DownloadExecutor(lastDownloadProgress) {
    override val isSupportRestoreState: Boolean = true
    private val logger = LoggerFactory.getLogger(MultiChunkDownloadExecutor::class.java)

    override fun download(fileStorage: FileStorage, url: String): Flow<DownloadStatus> {
        // 分块下载必须使用支持随机写的 storage
        if (fileStorage !is RandomAccessFileStorageImpl) {
            logger.warn( "fileStorage 不是 RandomAccessFileStorageImpl, 退化为单连接下载")
            return super.download(fileStorage, url)
        }
        return chunkFlow(fileStorage, url).flowOn(Dispatchers.IO)
    }

    private fun chunkFlow(
        fileStorage: RandomAccessFileStorageImpl,
        url: String,
    ): Flow<DownloadStatus> {
        return channelFlow {
            trustAllHosts()
            isShutdown = false
            send(DownloadStatus.STARTING)

            try {
                val meta = HttpConnectionUtils.probe(url) ?: run {
                    send(DownloadStatus.ERROR(IOException("无法获取文件元信息: $url")))
                    return@channelFlow
                }

                if (!meta.acceptRanges || meta.length < minChunkSize || chunkCount <= 1) {
                    logger.debug( "退化为单连接下载 (acceptRanges=${meta.acceptRanges}, length=${meta.length})")
                    // 直接复用父类逻辑: 需要把 storage 转成普通 FileStorage 语义即可
                    // 但这里已经在 channelFlow 里, 不能直接调 super.download
                    // 用 downloadSingle 把父类的 saveDownloadData 逻辑跑一遍
                    downloadSingle(fileStorage, url, this)
                    return@channelFlow
                }

                // 1) 规划分块
                val ranges = planChunks(meta.length)
                logger.debug( "分块下载: length=${meta.length}, chunks=${ranges.size}")

                // 2) 进度统计
                //记录此次下载的分块进度状态
                val chuckProgressMap = mutableMapOf<Int, ChunkProgress>()
                //记录此次下载的总进度状态
                var currentDownloadProgress: DownloadStatus.DOWNLOADING? = lastDownloadProgress
                fun emitDownloadProgress(progress: DownloadStatus.DOWNLOADING) {
                    currentDownloadProgress = progress
                    trySend(progress)
                }
                //记录当前每个分块下载量
                val chunkProgress = AtomicLongArray(ranges.size)
                val totalProgress = AtomicLong(0)
                val progressLock = Mutex()
                var lastEmitTime = 0L
                val startTime = System.nanoTime()
                var lastBytes = 0L
                var lastTime = startTime

                // 3) 并发下载
                coroutineScope<Unit> {
                    val jobs = ranges.mapIndexed { index, (start, end) ->
                        async {
                            //上次的分块进度
                            val last = currentDownloadProgress?.chunks?.find { it ->
                                //fixme: 仅判断index不太保险,万一索引相同,但下载起始位置不同呢?
                                it.index == index
                            }
                            if (last != null) {
                                chuckProgressMap[index] = last
                            } else {
                                chuckProgressMap[index] = ChunkProgress(index, 0, 0, 0)
                            }
                            downloadChunk(
                                storage = fileStorage,
                                url = url,
                                index = index,
                                start = last?.start ?: start,
                                end = last?.end ?: end,
                                onProgress = { delta ->
                                    val newTotal = totalProgress.addAndGet(delta.toLong())
                                    chunkProgress.set(index, chunkProgress.get(index) + delta)
                                    // 节流: 每 200ms emit 一次, 避免高频 emit 拖慢下载
                                    val now = System.nanoTime()
                                    if (now - lastEmitTime >= 200_000_000L) {
                                        lastEmitTime = now
                                        // channelFlow 的 send 是 suspend, 但 onProgress 是同步回调
                                        // 用 trySend 避免阻塞下载线程
                                        val elapsed = (now - lastTime) / 1e9
                                        val instant = if (elapsed > 0) (newTotal - lastBytes) / elapsed else 0.0
                                        val totalSec = (now - startTime) / 1e9
                                        val avg = if (totalSec > 0) newTotal / totalSec else 0.0
                                        lastBytes = newTotal
                                        lastTime = now
                                        val nowChuckProgress =
                                            ChunkProgress(index, start, end, start + chunkProgress.get(index))
                                        chuckProgressMap[index] = nowChuckProgress
                                        val chunks = chuckProgressMap.values.sortedBy { it.index }
                                        emitDownloadProgress(
                                            DownloadStatus.DOWNLOADING(
                                                length = meta.length,
                                                progress = newTotal,
                                                snapSpeed = instant,
                                                averageSpeed = avg,
                                                chunks = chunks,
                                            )
                                        )
                                    }
                                    if (isPaused) {
                                        trySend(DownloadStatus.PAUSED)
                                    }
                                },
                            )
                        }
                    }
                    jobs.awaitAll()
                    Unit
                }

                // 4) 完成
                if (isShutdown) {
                    send(DownloadStatus.CANCELED)
                } else {
                    fileStorage.flush()
                    send(DownloadStatus.FINISHED(fileStorage.path()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                send(DownloadStatus.ERROR(e))
            } finally {
                runCatching { fileStorage.close() }
            }
        }
    }

    /**
     * 单连接下载 (退化为父类逻辑, 但复用了 [RandomAccessFileStorageImpl])。
     */
    private suspend fun downloadSingle(
        fileStorage: FileStorage,
        url: String,
        flow: ProducerScope<DownloadStatus>,
        maxRedirects: Int = HttpConstant.MAX_REDIRECTS,
    ) {
        super.parseUrlExecDownload(fileStorage, url, maxRedirects) {
            flow.send(it)
        }
    }

    // ---------- 分块下载核心 ----------

    /**
     * 下载单个分块。
     *
     * @param start 绝对起始偏移 (含)
     * @param end   绝对结束偏移 (含)
     * @param onProgress 增量回调, 参数是本块新增字节数
     */
    protected open suspend fun downloadChunk(
        storage: RandomAccessFileStorageImpl,
        url: String,
        index: Int,
        start: Long,
        end: Long,
        onProgress: (Int) -> Unit,
    ) {
        val con = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=$start-$end")
            setRequestProperty("Accept-Encoding", "identity")
            readTimeout = HttpConstant.HTTP_TIME_OUT
            connectTimeout = HttpConstant.HTTP_TIME_OUT
        }
        try {
            val code = con.responseCode
            if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                throw IOException("分块 #$index 请求失败: HTTP $code")
            }
            // 如果服务端返回 200 (不支持 Range), 说明它忽略了 Range 头, 会把整个文件发回来。
            // 这种情况下我们只读前 (end-start+1) 字节并写入 start 偏移是错误的 (文件布局会乱),
            // 但为了容错, 这里只接受 206。若探测阶段 acceptRanges=true 而这里返回 200,
            // 说明服务端行为不一致, 直接报错让上层处理。
            if (code == HttpURLConnection.HTTP_OK && (start != 0L || end != con.contentLength - 1L)) {
                throw IOException("服务端不支持 Range, 分块 #$index 收到 200")
            }

            val buffer = ByteArray(HttpConstant.COPY_BUFFER_SIZE)
            var read: Int
            con.inputStream.source().buffer().use { source ->
                var pos = start
                while (source.read(buffer, 0, HttpConstant.COPY_BUFFER_SIZE).also { read = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    storage.writeAt(pos, buffer, 0, read)
                    pos += read
                    onProgress(read)
                    if (isPaused) pauseSignal.first { !it }
                    if (isShutdown) break
                }
                // 校验: 本块是否下完
                if (!isShutdown && pos != end + 1) {
                    throw IOException("分块 #$index 提前结束: 期望到 ${end + 1}, 实际到 $pos")
                }
            }
        } finally {
            con.disconnect()
        }
    }

    protected fun planChunks(totalLength: Long): List<Pair<Long, Long>> {
        val actualChunks = minOf(chunkCount.toLong(), (totalLength / minChunkSize).coerceAtLeast(1L)).toInt()
        val chunkSize = totalLength / actualChunks
        return (0 until actualChunks).map { i ->
            val start = i * chunkSize
            val end = if (i == actualChunks - 1) totalLength - 1 else (start + chunkSize - 1)
            start to end
        }
    }
}
