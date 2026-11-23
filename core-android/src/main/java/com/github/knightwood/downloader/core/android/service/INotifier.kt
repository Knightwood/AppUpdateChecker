package com.github.knightwood.downloader.core.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.android.R
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.util.AppUtil
import com.github.knightwood.downloader.core.android.util.Constant
import com.github.knightwood.downloader.core.util.DownloadFormat
import kotlin.math.roundToInt

interface INotifier {
    /**
     * 前台服务通知id/默认通知id
     */
    val NOTIFY_ID: Int
    /**
     * 通知渠道id
     */
    val CHANNEL_ID: String
    /**
     * 通知渠道名称
     */
    val CHANNEL_NAME: String
    /**
     * 通知渠道描述
     */
    val CHANNEL_DESC: String

    fun foregroundNotification(context: Context): Notification

    fun notificationOf(
        taskId: Int,
        params: AndroidDownloadParams,
        state: DownloadStatus,
        context: Context,
    ): Notification

    fun show(notificationManager: NotificationManager, taskId: Int, notification: Notification)
    fun createNotificationChannel(context: Context)
}

/**
 * 提供和显示普通下载任务通知栏消息
 */
open class DefaultDownloadNotifierImpl : INotifier {
    override val CHANNEL_ID: String = Constant.DEFAULT_CHANNEL_ID
    override val CHANNEL_NAME: String = Constant.DEFAULT_CHANNEL_NAME
    override val CHANNEL_DESC: String = Constant.DEFAULT_CHANNEL_DESC
    override val NOTIFY_ID: Int = Constant.DEFAULT_NOTIFY_ID
    val TAG: String = "DownloadNotifier"
    val Double?.toDataUnit: String?
        get() {
            return DownloadFormat.formatSpeed(this)
        }
    override fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(false)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun foregroundNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(AppUtil.getAppName(context))
            .setContentText(context.resources.getString(R.string.download_service_foreground_desc))
            .setSmallIcon(AppUtil.getAppIcon(context))
            .build()
    }

    override fun show(notificationManager: NotificationManager, taskId: Int, notification: Notification) {
        notificationManager.notify(taskId, notification)
    }

    override fun notificationOf(
        taskId: Int,
        params: AndroidDownloadParams,
        state: DownloadStatus,
        context: Context,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(AppUtil.getAppName(context))
            .setContentText(context.resources.getString(R.string.download_service_foreground_desc))
            .setSmallIcon(AppUtil.getAppIcon(context))

        when (state) {
            DownloadStatus.IDLE, DownloadStatus.STARTING -> {
                builder.setContentText(
                    context.resources.getString(
                        R.string.download_notify_start,
                        params.fileEntity.name
                    )
                )
            }

            is DownloadStatus.DOWNLOADING -> {
                val progressRecord = state.progress
                builder.setContentText(
                    context.resources.getString(
                        R.string.download_notify_running,
                        params.fileEntity.name,
                        "${progressRecord.averageSpeed.toDataUnit}/s",
                    )
                )
                Log.d(TAG,"已下载:${state.progress.downloaded}; 总长度:${state.progress.length}; 进度:${progressRecord.percentage.roundToInt()}/100")
                builder.setProgress(100, progressRecord.percentage.roundToInt() * 100, false)
            }

            DownloadStatus.PAUSED -> {
                builder.setContentText(
                    context.resources.getString(
                        R.string.download_notify_paused,
                        params.fileEntity.name
                    )
                )
            }

            is DownloadStatus.ERROR -> {
                builder.setContentText(
                    context.resources.getString(
                        R.string.download_notify_failed,
                        params.fileEntity.name
                    )
                )
            }

            DownloadStatus.FINISHED -> {
                builder.setContentText(
                    context.resources.getString(
                        R.string.download_notify_finish,
                        params.fileEntity.name
                    )
                )
            }

            DownloadStatus.CANCELED -> {}
        }

        return builder.build()
    }
}

