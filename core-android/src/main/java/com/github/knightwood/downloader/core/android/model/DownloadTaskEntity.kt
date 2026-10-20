package com.github.knightwood.downloader.core.android.model

import okio.Path

/**
 * 下载实体信息
 */
class DownloadTaskEntity(
    val url: String,
    /**
     * 下载文件存储路径,例如:
     * "/home/username/Downloads"
     * "C:\\User\\username\\Downloads"
     */
    val storeDirPath: Path? = null,
    /**
     * 下载文件的文件名
     */
    val fileName: String? = null,
    /**
     * 下载文件的分类标签列表
     */
    val labels: List<String>? = null,
    /**
     * md5
     */
    val md5: String? = null,
    /**
     * true: 执行下载之前删除残留文件
     */
    var deleteLastFile: Boolean = false,
    /**
     * 分块数量
     */
    var chuckCount: Int = 0,

    ) {
}
