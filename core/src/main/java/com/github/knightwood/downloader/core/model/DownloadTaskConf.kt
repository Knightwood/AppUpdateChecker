package com.github.knightwood.downloader.core.model

import com.github.knightwood.downloader.core.util.FileUtil
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.util.OSUtil
import okio.Path
import okio.Path.Companion.toPath
import java.util.UUID

/**
 * 下载任务配置
 */
data class DownloadTaskConf(
    val url: String,
    /**
     * md5
     */
    val md5: String? = null,
    /**
     * 下载文件保存路径
     *
     * android平台如果使用android.net.Uri读写文件, 此变量不可用
     */
    val dirPath: Path = FileUtil.systemDownloadDirPath(),
    /**
     * 下载文件名称
     */
    val name: String = UUID.randomUUID().toString(),
    /**
     * 是否支持分块下载
     */
    val isSupportChunkDownload: Boolean = false,
    /**
     * 分块数量
     */
    val chunkCount: Int = 0,
    /**
     * 最小分块大小,单位byte
     */
    val minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE,
) {
    /**
     * 文件路径
     *
     * 在Android平台，读写文件使用MediaStore或是SAF框架返回的android.net.Uri时此变量不可用
     */
    val path: Path = dirPath.resolve(name)
        get() {
            if (OSUtil.isAndroid) {
                throw IllegalCallerException("android platform does not support path")
            }
            return field
        }
}
