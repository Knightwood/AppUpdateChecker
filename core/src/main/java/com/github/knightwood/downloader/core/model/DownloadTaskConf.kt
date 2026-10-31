package com.github.knightwood.downloader.core.model

import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.util.FileUtil
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.util.OSUtil
import okio.Path
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
     * 建议分块数量，最终分块数量会根据文件大小、minChunkSize和此值进行调整
     */
    val chunkCount: Int = 0,
    /**
     * 最小分块大小,单位byte
     * 文件总大小小于此值则不进行分块
     */
    val minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE,
    /**
     * 上次的下载进度，用于执行旧任务时恢复进度，断点续传
     * 为了恢复任务进度，除了传递此参数，还可以创建任务后使用[DownloadTask.restoreProgress]恢复进度
     */
    var lastDownloadProgress: DownloadStatus.DOWNLOADING? = null,
) {
    /**
     * 文件路径
     *
     * 在Android平台，读写文件使用MediaStore或是SAF框架返回的android.net.Uri时此变量不可用
     */
    val path: Path = dirPath.resolve(name)
        get() {
            if (OSUtil.isAndroid) {
                throw IllegalArgumentException("android platform does not support path")
            }
            return field
        }
}
