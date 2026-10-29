package com.github.knightwood.downloader.core.android.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.time.Duration.Companion.seconds

import com.github.knightwood.downloader.core.android.Downloader
import com.github.knightwood.downloader.core.android.filestorage.AndroidFileStorageFactory
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.AndroidDownloadTaskSaveState
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.android.util.Constant
import com.github.knightwood.downloader.core.model.DownloadTaskConf
import com.github.knightwood.downloader.core.executor.DownloadExecutorFactory
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.taskmgr.TaskScheduler

class DownloadService : Service() {
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.IO) + SupervisorJob() + CoroutineName(Constant.COROUTINE_NAME)
    private var bomb: Job? = null
    private val paramsMap: MutableMap<Int, AndroidDownloadParams> = mutableMapOf()
    private val taskMgr: TaskScheduler = TaskScheduler(scope, Downloader.maxParallel)
    private val conn = object : DownloadServiceLinker() {

        override val service: DownloadService
            get() = this@DownloadService

        override fun submitTask(id: Int, conf: AndroidDownloadParams): DownloadTask {
            this@DownloadService.paramsMap[id] = conf
            val task = generateTask(id, conf)
            scope.launch {
                taskMgr.submit(task)
            }
            return task
        }

        override fun findTask(id: Int): DownloadTask? {
            return taskMgr.findTaskById(id)
        }

        override fun cancelTask(id: Int) {
            scope.launch {
                // 排队中的任务需要从等待队列移除, 只调用 task.cancel() 的话任务仍会开始下载
                taskMgr.cancel(id)
            }
        }

        override fun stopService() {
            stopSelf()
        }

        override suspend fun saveState(): AndroidDownloadTaskSaveState {
            return AndroidDownloadTaskSaveState(
                infoMap = paramsMap,
                taskSchedulerSaveState = taskMgr.saveState()
            )
        }

        override suspend fun restoreState(state: AndroidDownloadTaskSaveState) {
            paramsMap.clear()
            paramsMap.putAll(state.infoMap)
            taskMgr.restoreState(state.taskSchedulerSaveState)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return conn
    }

    override fun onCreate() {
        Downloader.mNotifier.createNotificationChannel(applicationContext)
        taskMgr.run()
        taskMgr.setActiveTaskProcessor { currTask ->
            scope.launch {
                processTaskState(currTask)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = Downloader.mNotifier.NOTIFY_ID
        val notification = Downloader.mNotifier.foregroundNotification(this)
        intent?.let {
            if (it.getBooleanExtra("foreground", false)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        /* service = */ this,
                        /* id = */ id,
                        /* notification = */ notification,
                        /* foregroundServiceType = */ ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(id, notification)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        taskMgr.close()
        scope.cancel()
    }

    private fun generateTask(
        id: Int,
        params: AndroidDownloadParams,
    ): DownloadTask {
        val downloadTaskConf = DownloadTaskConf(
            url = params.fileEntity.url,
            md5 = params.fileEntity.md5,
            name = params.fileEntity.name,
            dirPath = Constant.emptyPath,
            isSupportChunkDownload = params.taskParams.isSupportChunkDownload,
            chunkCount = params.taskParams.maxChunkCount,
            minChunkSize = params.taskParams.minChunkSize,
        )
        return DownloadTask(
            id = id,
            conf = downloadTaskConf,
            executorProvider = {
                params.taskParams.downloadExecutor ?: DownloadExecutorFactory.create(downloadTaskConf)
            },
            fileStorageProvider = {
                params.taskParams.path?.let { androidPath: AndroidPath ->
                    AndroidFileStorageFactory.create(
                        androidPath = androidPath,
                        application = application,
                        parallel = downloadTaskConf.isSupportChunkDownload
                    )
                } ?: Downloader.defaultFileStorage(
                    context = application,
                    filename = downloadTaskConf.name,
                    parallel = downloadTaskConf.isSupportChunkDownload
                )
            }
        )
    }

    private suspend fun processTaskState(currTask: DownloadTask) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val params = paramsMap[currTask.id]!!
        currTask.stateFlow.collect { state ->
            if (params.taskParams.enableNotification == true) {
                val notifier = params.taskParams.mNotifier!!
                val notification = notifier.notificationOf(
                    taskId = currTask.id,
                    params = params,
                    state = state,
                    context = this@DownloadService
                )
                notifier.show(notificationManager, currTask.id, notification)
            }
            if (state is DownloadStatus.STARTING) {
                //任务开始执行, 取消停止服务
                bomb?.cancel()
                bomb = null
            }
            if (state is DownloadStatus.FINISHED) {
                params.taskParams.postHandler?.process(
                    context = this@DownloadService,
                    path = params.taskParams.path,
                    taskId = currTask.id,
                    params = params,
                )

                if (Downloader.shutdownAfterDownloaded && taskMgr.isEmpty()) {
                    //当前任务结束且没有其他任务,10s后停止服务
                    bomb = scope.launch {
                        delay(10.seconds)
                        stopSelf()
                    }
                }
            }
        }
    }

    companion object {

        fun start(context: Context, foreground: Boolean = false) {
            val intent = Intent(context, DownloadService::class.java)
            intent.putExtra("foreground", foreground)
            if (foreground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        }

        fun bind(context: Context, conn: ServiceConnection) {
            val intent = Intent(context, DownloadService::class.java)
            context.bindService(intent, conn, BIND_AUTO_CREATE)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}

