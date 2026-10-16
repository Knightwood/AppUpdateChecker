package com.github.knightwood.downloader.core.executor

import android.annotation.SuppressLint
import com.github.knightwood.downloader.core.model.Constant
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.buffer
import okio.source
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


open class DownloadExecutor : IDownloadExecutor {
    protected open val TAG = "DownloadExecutor"

    @Volatile
    protected open var isShutdown = false

    @Volatile
    protected open var isPaused = false
    protected open val pauseSignal = Channel<Unit>(Channel.CONFLATED)

    override fun download(fileStorage: FileStorage, url: String): Flow<DownloadStatus> {
        trustAllHosts()
        isShutdown = false
        return flow {
            emit(DownloadStatus.STARTING)
            saveDownloadData(fileStorage, url, this)
        }.catch {
            emit(DownloadStatus.ERROR(it))
        }.flowOn(Dispatchers.IO)
    }

    @SuppressLint("NewApi")
    protected open suspend fun saveDownloadData(
        fileStorage: FileStorage,
        url: String,
        flow: FlowCollector<DownloadStatus>,
        maxRedirects: Int = Constant.MAX_REDIRECTS,
    ) {
        if (maxRedirects <= 0) {
            flow.emit(DownloadStatus.ERROR(IOException("Too many redirects")))
            return
        }
        val con = URL(url).openConnection() as HttpURLConnection
        con.apply {
            requestMethod = "GET"
            readTimeout = Constant.HTTP_TIME_OUT
            connectTimeout = Constant.HTTP_TIME_OUT
            setRequestProperty("Accept-Encoding", "identity")
        }
        if (con.responseCode == HttpURLConnection.HTTP_OK) {
            //下载
            val length = con.contentLengthLong
            val buffer = ByteArray(Constant.COPY_BUFFER_SIZE)
            var totalBytes = 0L
            var read: Int

            //速度统计
            val startTime = System.nanoTime()//开始下载时的时间
            var lastBytes = 0L //上次统计速度时已下载的字节数
            var lastTime = startTime//上次统计速度时的时间

            fileStorage.use { storage: FileStorage ->
                con.inputStream.source().buffer().use { source ->
                    while (source.read(buffer, 0, Constant.COPY_BUFFER_SIZE).also { read = it } != -1) {

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

                        flow.emit(DownloadStatus.DOWNLOADING(length, totalBytes))
                        if (isPaused) {
                            flow.emit(DownloadStatus.PAUSED)
                            pauseSignal.receive()
                        }
                        if (isShutdown) {
                            break
                        }
                    }
                }
            }

            if (isShutdown) {
                flow.emit(DownloadStatus.CANCELED)
                fileStorage.delete()
            } else {
                flow.emit(DownloadStatus.FINISHED(fileStorage.path()))
            }
        } else if (con.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            con.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            con.responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
            con.responseCode == 307 || con.responseCode == 308
        ) {
            val location = con.getHeaderField("Location")   // 先取
            con.disconnect()                                // 再断
            if (location.isNullOrBlank()) {
                flow.emit(DownloadStatus.ERROR(IOException("Redirect without Location")))
                return
            }
            val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
            LogUtil.d(
                TAG,
                "The current url is the redirect Url, the redirected url is $next"
            )
            saveDownloadData(fileStorage, next, flow, maxRedirects - 1)
//            if (isShutdown) { flow.emit(DownloadStatus.CANCELED); return }
        } else {
            val e = SocketTimeoutException("Error: Http response code = ${con.responseCode}")
            flow.emit(DownloadStatus.ERROR(e))
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
        pauseSignal.trySend(Unit)
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

}
