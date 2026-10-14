package com.github.knightwood.downloader.core.executor

import com.github.knightwood.downloader.core.model.Constant
import com.github.knightwood.downloader.core.model.TaskState
import com.github.knightwood.downloader.core.util.LogUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.Buffer
import okio.buffer
import okio.source
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class DownloadExecutor : IDownloadExecutor {
    private val TAG = "DownloadExecutor"
    private var isShutdown: Boolean = false
    private var isPaused = false
    private val paused = CompletableDeferred(Unit)

    override fun download(fileStorage: FileStorage, url: String): Flow<TaskState> {
        trustAllHosts()
        isShutdown = false
        return flow {
            emit(TaskState.STARTING)
            saveDownloadData(fileStorage, url, this)
        }.catch {
            emit(TaskState.ERROR(it))
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun saveDownloadData(
        fileStorage: FileStorage,
        url: String,
        flow: FlowCollector<TaskState>,
    ) {
        val con = URL(url).openConnection() as HttpURLConnection
        con.apply {
            requestMethod = "GET"
            readTimeout = Constant.HTTP_TIME_OUT
            connectTimeout = Constant.HTTP_TIME_OUT
            setRequestProperty("Accept-Encoding", "identity")
        }
        if (con.responseCode == HttpURLConnection.HTTP_OK) {
            //下载
            val length = con.contentLength
            val buffer = Buffer()
            var totalBytes = 0L
            var read: Long

            //速度统计
            val startTime = System.nanoTime()//开始下载时的时间
            var lastBytes = 0L //上次统计速度时已下载的字节数
            var lastTime = startTime//上次统计速度时的时间

            fileStorage.use { storage: FileStorage ->
                con.inputStream.source().buffer().use { source ->
                    while (source.read(buffer, 8192L).also { read = it } != -1L && !isShutdown) {
                        if (isPaused) {
                            paused.await()
                        }
                        storage.write(buffer, read)
                        totalBytes += read

                        val now = System.nanoTime()
                        val elapsed = (now - lastTime) / 1_000_000_000.0
                        if (elapsed >= 1.0) {//每隔1s统计一次
                            //瞬时速度
                            val instant = (totalBytes - lastBytes) / elapsed
                            println("瞬时速度: ${"%.2f".format(instant / 1024 / 1024)} MB/s")
                            lastBytes = totalBytes
                            lastTime = now

                            //平均速度
                            val totalSec = (System.nanoTime() - startTime) / 1_000_000_000.0
                            val avg = totalBytes / totalSec
                            println("完成: $totalBytes 字节, 平均 ${"%.2f".format(avg / 1024 / 1024)} MB/s")
                        }

                        flow.emit(TaskState.DOWNLOADING(length, totalBytes))
                    }
                }
            }
            if (isShutdown) {
                flow.emit(TaskState.DROPPED)
                fileStorage.delete()
            } else {
                flow.emit(TaskState.FINISHED(fileStorage.path()))
            }
        } else if (con.responseCode == HttpURLConnection.HTTP_MOVED_PERM
            || con.responseCode == HttpURLConnection.HTTP_MOVED_TEMP
        ) {
            con.disconnect()
            val locationUrl = con.getHeaderField("Location")
            LogUtil.d(
                TAG,
                "The current url is the redirect Url, the redirected url is $locationUrl"
            )
            saveDownloadData(fileStorage, locationUrl, flow)
        } else {
            val e = SocketTimeoutException("Error: Http response code = ${con.responseCode}")
            flow.emit(TaskState.ERROR(e))
        }
        con.disconnect()
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
        paused.complete(Unit)
    }

    /**
     * fix https url (SSLHandshakeException) exception
     */
    private fun trustAllHosts() {
        val manager: TrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                LogUtil.d(TAG, "checkClientTrusted")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                LogUtil.d(TAG, "checkServerTrusted")
            }
        }
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(manager), SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        } catch (e: Exception) {
            LogUtil.e(TAG, "trustAllHosts error: $e")
        }
    }

    override fun cancel() {
        isShutdown = true
    }

    override fun release() {
        cancel()
    }
}
