package com.github.knightwood.downloader.core.model

import com.github.knightwood.downloader.core.util.HttpConstant
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
     */
    val dirPath: Path = System.getProperty("user.home")!!.toPath().resolve("Downloads"),
    /**
     * 下载文件名称
     */
    val name: String = UUID.randomUUID().toString(),
    /**
     * true: 执行下载之前删除残留文件
     */
    var isDeleteLastFile: Boolean = false,
    /**
     * 是否支持分块下载
     */
    val isSupportChunkDownload :Boolean = false,
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
     * 尽量减少依赖此变量，在Android平台，路径很可能是MediaStore或是SAF框架返回的android.net.Uri
     */
    val path: Path = dirPath.resolve(name)
}
