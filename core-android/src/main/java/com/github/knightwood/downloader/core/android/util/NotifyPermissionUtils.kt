package com.github.knightwood.downloader.core.android.util

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotifyPermissionUtils {

    fun check(
        context: Activity,
    ): Boolean {
        //其实就等同于NotificationManager.areNotificationsEnabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
            return false
        } else {//api 33以下没有POST_NOTIFICATIONS
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun requestPermission(context: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
}
