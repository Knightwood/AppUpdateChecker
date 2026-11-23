package com.github.knightwood.downloader.core.android.util

import android.content.Context
import android.content.pm.PackageManager


object AppUtil {
    const val TAG = "AppUtil"

    fun getAppIcon(context: Context): Int {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        return appInfo.icon
    }

    fun getAppName(context: Context): String {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val appName = appInfo.loadLabel(pm).toString() + ""
        return appName
    }
}

