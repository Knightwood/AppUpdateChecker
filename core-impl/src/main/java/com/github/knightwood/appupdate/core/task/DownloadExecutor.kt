package com.github.knightwood.appupdate.core.task

import com.github.knightwood.appupdate.core.base.bean.DownloadStatus
import com.github.knightwood.appupdate.core.config.Constant
import com.github.knightwood.appupdate.core.util.LogUtil
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
    private val TAG = "HttpDownloadManager"
    private var shutdown: Boolean = false

    override fun download(fileStorage: FileStorage, apkUrl: String): Flow<DownloadStatus> {
        trustAllHosts()
        shutdown = false
        return flow {
            emit(DownloadStatus.Start)
            saveDownloadData(fileStorage, apkUrl, this)
        }.catch {
            emit(DownloadStatus.Error(it))
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun saveDownloadData(
        fileStorage: FileStorage,
        apkUrl: String,
        flow: FlowCollector<DownloadStatus>,
    ) {
        val con = URL(apkUrl).openConnection() as HttpURLConnection
        con.apply {
            requestMethod = "GET"
            readTimeout = Constant.HTTP_TIME_OUT
            connectTimeout = Constant.HTTP_TIME_OUT
            setRequestProperty("Accept-Encoding", "identity")
        }
        if (con.responseCode == HttpURLConnection.HTTP_OK) {
            val inStream = con.inputStream
            val length = con.contentLength
            var len: Long
            var progress = 0L
            val buffer = Buffer()
            fileStorage.use {
                inStream.source().buffer().use { source ->
                    while (
                        source.read(buffer, 2048L).also { len = it } != -1L && !shutdown
                    ) {
                        it.write(buffer, len)
                        progress += len
                        flow.emit(DownloadStatus.Downloading(length, progress))
                    }
                }
            }
            if (shutdown) {
                flow.emit(DownloadStatus.Cancel)
                fileStorage.delete()
            } else {
                flow.emit(DownloadStatus.Done(fileStorage.path()))
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
            flow.emit(DownloadStatus.Error(e))
        }
        con.disconnect()
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
                LogUtil.Companion.d(TAG, "checkClientTrusted")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                LogUtil.Companion.d(TAG, "checkServerTrusted")
            }
        }
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(manager), SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        } catch (e: Exception) {
            LogUtil.Companion.e(TAG, "trustAllHosts error: $e")
        }
    }

    override fun cancel() {
        shutdown = true
    }

    override fun release() {
        cancel()
    }
}
