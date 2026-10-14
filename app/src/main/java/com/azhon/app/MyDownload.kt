package com.azhon.app

import com.github.knightwood.downloader.core.base.BaseDownloadTask
import com.github.knightwood.downloader.core.task.TaskState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


/**
 * ProjectName: AppUpdate
 * PackageName: com.azhon.app
 * FileName:    MyDownload
 * CreateDate:  2022/4/8 on 11:23
 * Desc:
 *
 * @author   azhon
 */

class MyDownload(path:String) : BaseDownloadTask(path) {


    override fun download(
        apkUrl: String, apkName: String
    ): Flow<TaskState> {
        return flow { }
    }

    override fun cancel() {
    }

    override fun release() {
    }
}
