package com.github.knightwood.appupdate.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.github.knightwood.appupdate.core.R
import com.github.knightwood.appupdate.core.base.bean.DownloadStatus
import com.github.knightwood.appupdate.core.base.bean.DownloadTaskConf
import com.github.knightwood.appupdate.core.config.Constant
import com.github.knightwood.appupdate.core.manager.DownloadManager
import com.github.knightwood.appupdate.core.task.TaskCollaborator
import com.github.knightwood.appupdate.core.util.ApkUtil
import com.github.knightwood.appupdate.core.util.FileUtil
import com.github.knightwood.appupdate.core.util.LogUtil
import com.github.knightwood.appupdate.core.util.NotificationUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ProjectName: AppUpdate PackageName: com.azhon.appupdate.service
 * FileName: DownloadService CreateDate: 2022/4/7 on 11:42 Desc:
 *
 * 静默下载策略：启动静默下载配置，构造应用描述信息，启动服务。 服务启动后，查找是否有下载好的apk，如果有，则结束下载。 如果没有，则启动下载。
 * 调启下载界面后，如果已存在相同版本和md5的安装包，则直接可以显示下载完成并进行安装
 *
 * 静默下载的文件专门存放在一个目录下，以md5值为文件名。 普通下载的文件存在另一个文件夹下。
 *
 * @author knightwood
 * @author azhon
 */

class DownloadService : Service() {
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.IO) + SupervisorJob() + CoroutineName(Constant.COROUTINE_NAME)
    private val taskCollaborator: TaskCollaborator = TaskCollaborator(scope)
    private val conn = object : DownloadServiceConn() {
        override val service: DownloadService
            get() = this@DownloadService

        override fun submitTask(id: String, conf: DownloadTaskConf) {

        }

        override fun cancelTask(id: String) {
            taskCollaborator.cancelTask(id)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return conn
    }

    override fun onCreate() {
        taskCollaborator.run()
        scope.launch {
            withContext(Dispatchers.IO) {
                taskCollaborator.currentTask.filterNotNull().collect { currTask ->
                    currTask.stateFlow.collect { state ->
                        when (state) {
                            DownloadStatus.Cancel -> {
                                TODO()
                            }
                            is DownloadStatus.Done -> {
                                TODO()
                            }
                            is DownloadStatus.Downloading -> {
                                TODO()
                            }
                            DownloadStatus.End -> {
                                TODO()
                            }
                            is DownloadStatus.Error -> {
                                TODO()
                            }
                            DownloadStatus.IDLE -> {
                                TODO()
                            }
                            DownloadStatus.Paused -> {
                                TODO()
                            }
                            DownloadStatus.Start -> {
                                TODO()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        taskCollaborator.close()
        scope.cancel()
    }
}
