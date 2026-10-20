package com.github.knightwood.downloader.core.util

import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL

object HttpConnectionUtils {
    private val logger = LoggerFactory.getLogger(HttpConnectionUtils::class.java)

    fun isSupportMultiChuck(url: String): Boolean {
        return probe(url) != null
    }

    /**
     * 探测获取下载文件长度、是否支持分块下载
     */
    fun probe(url: String): UrlResponseMeta? {
        val con = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // 用 Range 探测而不是 HEAD, 因为很多服务器 HEAD 不返回 Accept-Ranges
            setRequestProperty("Range", "bytes=0-0")
            setRequestProperty("Accept-Encoding", "identity")
            readTimeout = HttpConstant.HTTP_TIME_OUT
            connectTimeout = HttpConstant.HTTP_TIME_OUT
        }
        return try {
            val code = con.responseCode
            val acceptRanges = con.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            val contentDisposition = con.getHeaderField("Content-Disposition")
            val filename = URLUtilCompat.guessFileName(url, contentDisposition, con.contentType)
            val length = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    // Content-Range: bytes 0-0/12345
                    val contentRange = con.getHeaderField("Content-Range")
                    val total = contentRange?.substringAfterLast('/')?.toLongOrNull()
                    total ?: con.contentLengthLong
                }

                HttpURLConnection.HTTP_OK -> con.contentLengthLong
                else -> return null
            }
            if (length <= 0) return null
            UrlResponseMeta(
                length = length,
                acceptRanges = acceptRanges || code == HttpURLConnection.HTTP_PARTIAL,
                fileName = filename,
            )
        } catch (e: Exception) {
            logger.error("probe error", e)
            null
        } finally {
            con.disconnect()
        }
    }
}

data class UrlResponseMeta(
    val length: Long,
    val acceptRanges: Boolean = false,
    val fileName: String,
)
