package com.github.knightwood.downloader.core.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.R
import com.github.knightwood.downloader.core.android.model.Constant
import com.github.knightwood.downloader.core.android.util.ApkUtil
import com.github.knightwood.downloader.core.android.util.Constant

interface INotifier {
    val NOTIFY_ID: Int
    val CHANNEL_ID: String
    val CHANNEL_NAME: String
    val CHANNEL_DESC: String

    fun foregroundNotification(context: Context): Notification

    fun notificationOf(taskId: Int, state: DownloadStatus, context: Context): Notification

    fun show(notificationManager: NotificationManager, taskId: Int, notification: Notification)
    fun createNotificationChannel(context: Context)
}

open class ApkUpdateNotifierImpl : INotifier {
    override val CHANNEL_ID: String = Constant.DEFAULT_CHANNEL_ID
    override val CHANNEL_NAME: String = Constant.DEFAULT_CHANNEL_NAME
    override val CHANNEL_DESC: String = Constant.DEFAULT_CHANNEL_DESC
    override val NOTIFY_ID: Int = Constant.DEFAULT_NOTIFY_ID

    override fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
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
            .setContentTitle(ApkUtil.getAppName(context))
            .setContentText(context.resources.getString(R.string.download_service_foreground_desc))
            .setSmallIcon(ApkUtil.getAppIcon(context))
            .build()
    }

    override fun show(notificationManager: NotificationManager, taskId: Int, notification: Notification) {
        notificationManager.notify(taskId, notification)
    }

    override fun notificationOf(taskId: Int, state: DownloadStatus, context: Context): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(ApkUtil.getAppName(context))
            .setContentText(context.resources.getString(R.string.download_service_foreground_desc))
            .setSmallIcon(ApkUtil.getAppIcon(context))

        when (state) {
            is DownloadStatus.DOWNLOADING -> {
                builder.setProgress(state.length.toInt(), state.progress.toInt(), true)
            }

            is DownloadStatus.ERROR -> {
                builder.setContentText(context.resources.getString(R.string.app_update_download_error))
            }

            is DownloadStatus.FINISHED -> {
                builder.setContentText(
                    context.resources.getString(R.string.app_update_download_completed) + "\n" +
                            context.resources.getString(R.string.app_update_click_hint)
                )
            }

            DownloadStatus.CANCELED, DownloadStatus.IDLE, DownloadStatus.PAUSED, DownloadStatus.STARTING -> {
                builder.setContentText(context.resources.getString(R.string.app_update_start_download))
            }
        }

        return builder.build()
    }
}
