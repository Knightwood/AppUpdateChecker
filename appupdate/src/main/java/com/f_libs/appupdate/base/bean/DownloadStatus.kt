package com.f_libs.appupdate.base.bean

import java.io.File


/**
 * ProjectName: AppUpdate PackageName: com.azhon.appupdate.base.bean
 * FileName: DownloadStatus CreateDate: 2022/4/14 on 11:18 Desc:
 *
 * @author azhon
 */


sealed class DownloadStatus {
    /**
     * 此状态标志一个下载流程的开始，即初始状态
     */
    data object IDLE : DownloadStatus()

    data object Start : DownloadStatus()

    data class Downloading(val max: Int, val progress: Int) : DownloadStatus()

    data class Done(val apk: File) : DownloadStatus()

    data object Cancel : DownloadStatus()

    data class Error(val e: Throwable) : DownloadStatus()

    /**
     * 此状态标志一个下载流程的结束
     */
    data object End : DownloadStatus()
}
