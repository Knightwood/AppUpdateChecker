package com.github.knightwood.appupdate.core.util

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

object NotifyPermissionUtils {

    fun check(
        context: Activity,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                return true
            }
            ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return false
        } else {//api 33以下没有POST_NOTIFICATIONS，我们可以使用通知服务判断是否有权限
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.areNotificationsEnabled()) {
                return true
            } else {
                return false
            }
        }
    }
}
