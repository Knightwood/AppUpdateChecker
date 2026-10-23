package com.github.knightwood.downloader.core.executor

import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.util.contentLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.buffer
import okio.source
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


open class DownloadExecutor(
    protected var lastDownloadProgress: DownloadStatus.DOWNLOADING? = null,
) : IDownloadExecutor {
    private val logger = LoggerFactory.getLogger(DownloadExecutor::class.java)
    override val isSupportRestoreState: Boolean = false

    @Volatile
    protected open var isShutdown = false

    protected open val pauseSignal = MutableStateFlow(false)
    protected open var isPaused
        set(value) {
            pauseSignal.value = value
        }
        get() = pauseSignal.value

    override fun restoreState(saveState: DownloadStatus.DOWNLOADING): IDownloadExecutor {
        lastDownloadProgress = saveState
        return this
    }

    override fun download(fileStorage: FileStorage, url: String): Flow<DownloadStatus> {
        trustAllHosts()
        isShutdown = false
        return flow {
            emit(DownloadStatus.STARTING)
            parseUrlExecDownload(fileStorage, url) {
                emit(it)
            }
        }.catch {
            emit(DownloadStatus.ERROR(it))
        }.flowOn(Dispatchers.IO)
    }

    protected open suspend fun parseUrlExecDownload(
        fileStorage: FileStorage,
        url: String,
        maxRedirects: Int = HttpConstant.MAX_REDIRECTS,
        onStatusChanged: suspend (DownloadStatus) -> Unit,
    ) {
        if (maxRedirects <= 0) {
            onStatusChanged(DownloadStatus.ERROR(IOException("Too many redirects")))
            return
        }
        val con = URL(url).openConnection() as HttpURLConnection
        con.apply {
            requestMethod = "GET"
            readTimeout = HttpConstant.HTTP_TIME_OUT
            connectTimeout = HttpConstant.HTTP_TIME_OUT
            setRequestProperty("Accept-Encoding", "identity")
        }
        if (con.responseCode == HttpURLConnection.HTTP_OK) {
            //下载
            val length = con.contentLength()
            val buffer = ByteArray(HttpConstant.COPY_BUFFER_SIZE)
            var totalBytes = 0L
            var read: Int

            //速度统计
            val startTime = System.nanoTime()//开始下载时的时间
            var lastBytes = 0L //上次统计速度时已下载的字节数
            var lastTime = startTime//上次统计速度时的时间

            fileStorage.use { storage: FileStorage ->
                con.inputStream.source().buffer().use { source ->
                    while (source.read(buffer, 0, HttpConstant.COPY_BUFFER_SIZE).also { read = it } != -1) {

                        storage.write(buffer, read)
                        totalBytes += read

                        val now = System.nanoTime()
                        val elapsed = (now - lastTime) / 1_000_000_000.0
                        if (elapsed >= 1.0) {//每隔1s统计一次
                            //瞬时速度
                            val instant = (totalBytes - lastBytes) / elapsed
                            logger.info("瞬时速度: ${"%.2f".format(instant / 1024 / 1024)} MB/s")
                            lastBytes = totalBytes
                            lastTime = now

                            //平均速度
                            val totalSec = (System.nanoTime() - startTime) / 1_000_000_000.0
                            val avg = totalBytes / totalSec
                            logger.info("完成: $totalBytes 字节, 平均 ${"%.2f".format(avg / 1024 / 1024)} MB/s")
                            onStatusChanged(DownloadStatus.DOWNLOADING(length, totalBytes, instant, avg))
                        }

                        if (isPaused) {
                            onStatusChanged(DownloadStatus.PAUSED)
                            pauseSignal.first { !it }
                        }
                        if (isShutdown) break
                    }
                }
            }

            if (isShutdown) {
                onStatusChanged(DownloadStatus.CANCELED)
            } else {
                onStatusChanged(DownloadStatus.FINISHED)
            }
        } else if (con.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            con.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            con.responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
            con.responseCode == 307 || con.responseCode == 308
        ) {
            val location = con.getHeaderField("Location")
            con.disconnect()
            if (location.isNullOrBlank()) {
                onStatusChanged(DownloadStatus.ERROR(IOException("Redirect without Location")))
                return
            }
            val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
            logger.debug("The current url is the redirect Url, the redirected url is $next")
            parseUrlExecDownload(fileStorage, next, maxRedirects - 1, onStatusChanged)
//            if (isShutdown) { provideStatus(DownloadStatus.CANCELED); return }
        } else {
            val e = SocketTimeoutException("Error: Http response code = ${con.responseCode}")
            onStatusChanged(DownloadStatus.ERROR(e))
        }
        con.disconnect()
    }

    override fun pause() {
        isPaused = true
    }

    override fun cancel() {
        isShutdown = true;
        resume()// 唤醒被 pause 卡住的协程
    }

    override fun resume() {
        isPaused = false
    }

    /**
     * fix https url (SSLHandshakeException) exception
     */
    protected open fun trustAllHosts() {
        val manager: TrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                logger.debug("checkClientTrusted")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                logger.debug("checkServerTrusted")
            }
        }
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(manager), SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        } catch (e: Exception) {
            logger.error("trustAllHosts error: ${e.message}", e)
        }
    }

}
