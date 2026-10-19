package com.github.knightwood.downloader.core.model

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
    val minChunkSize: Long = Constant.MIN_CHUNK_SIZE,
) {
    /**
     * 文件路径
     */
    val path: Path = dirPath.resolve(name)
}
