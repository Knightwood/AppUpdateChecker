package com.github.knightwood.downloader.updater

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.github.knightwood.downloader.core.android.Downloader

import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.android.R as CoreR
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.service.DefaultDownloadNotifierImpl
import com.github.knightwood.downloader.updater.util.ApkUtil
import kotlin.math.roundToInt

/**
 * 提供和显示应用更新下载通知栏消息
 */
open class ApkUpdateNotifierImpl : DefaultDownloadNotifierImpl() {

    override fun notificationOf(
        taskId: Int,
        params: AndroidDownloadParams,
        state: DownloadStatus,
        context: Context,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(ApkUtil.getAppName(context))
            .setContentText(context.resources.getString(CoreR.string.download_service_foreground_desc))
            .setSmallIcon(ApkUtil.getAppIcon(context))

        return when (state) {
            DownloadStatus.IDLE, DownloadStatus.STARTING -> {
                builder.setContentText(
                    context.resources.getString(
                        CoreR.string.download_notify_start,
                        params.fileEntity.name
                    )
                )
                builder.build()
            }

            is DownloadStatus.DOWNLOADING -> {
                val progressRecord = state.progress
                builder.setContentText(
                    context.resources.getString(
                        CoreR.string.download_notify_running,
                        params.fileEntity.name,
                        "${progressRecord.averageSpeed.toDataUnit}/s",
                    )
                )
                builder.setProgress(100, (progressRecord.percentage * 100).roundToInt(), false)
                builder.build()
            }

            DownloadStatus.PAUSED -> {
                builder.setContentText(
                    context.resources.getString(
                        CoreR.string.download_notify_paused,
                        params.fileEntity.name
                    )
                )
                builder.build()
            }

            is DownloadStatus.ERROR -> {
                builder.setContentText(
                    context.resources.getString(
                        CoreR.string.download_notify_failed,
                        params.fileEntity.name
                    )
                )
                builder.build()
            }

            DownloadStatus.FINISHED -> {
                builder.setContentText(
                    context.resources.getString(
                        CoreR.string.download_notify_finish,
                        params.fileEntity.name
                    ) + "/n" + context.resources.getString(R.string.app_update_click_hint)
                )
                val intent = ApkUtil.createInstallIntent(
                    context,
                    Downloader.authorities(context),
                    params.taskParams.path!!
                )
                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                } else {
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT)
                }
                builder.setContentIntent(pendingIntent)
                builder.build().apply {
                    flags = this.flags or Notification.FLAG_AUTO_CANCEL
                }
            }

            DownloadStatus.CANCELED -> {
                builder.build()
            }
        }

    }
}
