package com.azhon.app

import com.github.knightwood.appupdate.core.base.BaseHttpDownloadManager
import com.github.knightwood.appupdate.core.base.bean.DownloadStatus
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

class MyDownload(path:String) : BaseHttpDownloadManager(path) {


    override fun download(
        apkUrl: String, apkName: String
    ): Flow<DownloadStatus> {
        return flow { }
    }

    override fun cancel() {
    }

    override fun release() {
    }
}
