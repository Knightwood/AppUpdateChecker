package com.github.knightwood.downloader.core.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.github.knightwood.downloader.core.Downloader
import com.github.knightwood.downloader.core.model.TaskDesc
import com.github.knightwood.downloader.core.model.TaskParams
import com.github.knightwood.downloader.core.model.Constant
import com.github.knightwood.downloader.core.executor.DownloadExecutor
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.task.TaskRepository
import com.github.knightwood.downloader.core.task.TaskScheduler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import kotlin.time.Duration.Companion.seconds

class DownloadService : Service() {
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.IO) + SupervisorJob() + CoroutineName(Constant.COROUTINE_NAME)
    private var bomb: Job? = null
    private val confMap: MutableMap<Int, TaskDesc> = mutableMapOf()
    private val repo: TaskRepository = TaskRepository()
    private val taskCollaborator: TaskScheduler = TaskScheduler(scope,repo)
    private val conn = object : DownloadServiceLinker() {

        override val service: DownloadService
            get() = this@DownloadService

        override fun submitTask(id: Int, conf: TaskDesc): DownloadTask {
            this@DownloadService.confMap[id] = conf

            val task = generateTask(id, conf)
            repo.add(task)
            taskCollaborator.enqueue(task.id)
            return task
        }

        override fun findTask(id: Int): DownloadTask? {
            return taskCollaborator.findTask(id)
        }

        override fun cancelTask(id: Int) {
            taskCollaborator.cancelTask(id)
        }

        override fun stopService() {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return conn
    }

    override fun onCreate() {
        Downloader.mNotifier.createNotificationChannel(applicationContext)
        taskCollaborator.run()
        taskCollaborator.setTaskProcessor { currTask ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    processTaskState(currTask)
                }
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
        taskCollaborator.close()
        scope.cancel()
    }

    private fun generateTask(
        id: Int,
        conf: TaskDesc,
    ): DownloadTask {
        return DownloadTask(
            id = id,
            executor = conf.taskImI.downloadExecutor ?: DownloadExecutor(),
            conf = TaskParams(
                md5 = conf.fileEntity.md5,
                url = conf.fileEntity.url!!
            ),
            produceFile = {
                val path = this@DownloadService.externalCacheDir!!.toOkioPath()
                    .resolve(conf.taskImI.saveRelativePath ?: Constant.CACHE_DIR_NAME)
                path.toFile()
            }
        )
    }

    private suspend fun processTaskState(currTask: DownloadTask) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val conf = confMap[currTask.id]!!
        currTask.stateFlow.collect { state ->
            if (!Downloader.disableNotification) {
                val notification =
                    Downloader.mNotifier.notificationOf(currTask.id, state, this@DownloadService)
                Downloader.mNotifier.show(notificationManager, currTask.id, notification)
            }
            if (state is DownloadStatus.STARTING) {
                bomb?.cancel()
                bomb = null
            }
            if (state is DownloadStatus.FINISHED) {
                if (conf.taskImI.autoHandleAfterDownloaded) {
                    Downloader.postHandle.process(
                        context = this@DownloadService,
                        path = state.path,
                        taskId = currTask.id,
                        conf = conf,
                    )
                }
                if (Downloader.shutdownAfterDownloaded && taskCollaborator.isEmpty()) {
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
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}

