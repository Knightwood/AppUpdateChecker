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
            // HttpURLConnection默认在请求中使用gzip压缩, 这会导致无法获取content-length.
            // 为可以获取content-length, 需将"Accept-Encoding"指定为"identity".
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
                    total ?: con.contentLength()
                }

                HttpURLConnection.HTTP_OK -> con.contentLength()
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

    /**
     * 计划分块
     *
     * @param chunkCount 分块数量
     * @param minChunkSize 最小分块大小，单位字节，totalLength小于该大小将不进行分块
     * @param totalLength 文件总长度
     * @return 分块列表, 每个分块是一个 Pair, 表示分块的起始偏移和结束偏移
     */
    fun planChunks(
        chunkCount: Int,
        minChunkSize: Long,
        totalLength: Long,
    ): List<Pair<Long, Long>> {
        assert(totalLength > minChunkSize) { "文件长度必须大于最小分块大小 $minChunkSize" }
        assert(chunkCount > 0) { "分块数量必须大于 0" }
        val singleChunkLength = totalLength / chunkCount
        return if (singleChunkLength >= minChunkSize) { // 按照chunkCount分块，每个分块长度≥最小分块长度
            (0 until chunkCount).map { i ->
                val start = i * singleChunkLength
                val end = if (i == chunkCount - 1) totalLength - 1 else (start + singleChunkLength - 1)
                start to end
            }
        } else {// 按照chunkCount分块，分块长度小于最小分块长度，则按照最小分块长度分块，最后 chunkCount - actualChunkCount 个分块起始- 终止位置将设置为-1
            val actualChunkCount = HttpConnectionUtils.solveChunkCount(totalLength, minChunkSize)
            (0 until actualChunkCount).map { i ->
                val start = i * minChunkSize
                val end = if (i == actualChunkCount - 1) totalLength - 1 else (start + minChunkSize - 1)
                start to end
            }//.plus(0 until chunkCount - actualChunkCount).map { -1L to -1L } // 追加剩余无效分块
        }
    }

    /**
     *
     * 计算分块数量
     *
     * 直接使用总长度除以最小分块大小，只要不能整除, 得到的商实际上是真实分块数量-1
     * 比如数据长度17，最小分块大小为3，那么分块数量为6，即[0,2],[3,5],[6,8],[9,11],[12,14],[15,16]
     *
     * @param totalLength 文件总长度
     * @param minChunkSize 最小分块大小
     * @return 分块数量
     */
    fun solveChunkCount(
        totalLength: Long,
        minChunkSize: Long,
    ): Int {
        // totalLength可能无法正好被minChunkSize整除，需要计算余数，如果余数大于0，则分块数量加1
        val mod = totalLength % minChunkSize
        val actualChunkCount = if (mod > 0) totalLength / minChunkSize + 1 else totalLength / minChunkSize
        return actualChunkCount.toInt()
    }
}

data class UrlResponseMeta(
    val length: Long,
    val acceptRanges: Boolean = false,
    val fileName: String,
)

/**
 * 获取 content-length，用于兼容 SDK 24 以下的 Android 模块.
 *
 * [HttpURLConnection.getContentLengthLong] JVM平台全版本可用; Android平台仅SDK 24+ 可用.
 */
fun HttpURLConnection.contentLength(): Long {
    return getHeaderFieldLong("content-length", -1);
}
