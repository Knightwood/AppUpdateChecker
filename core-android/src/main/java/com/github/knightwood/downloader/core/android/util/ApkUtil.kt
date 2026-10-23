package com.github.knightwood.downloader.core.android.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.android.model.copyTo
import java.io.File
import java.util.Date


object ApkUtil {
    const val TAG = "ApkUtil"

    /** install package form file */
    fun installApk(context: Context, authorities: String, path: AndroidPath) {
        context.startActivity(createInstallIntent(context, authorities, path))
    }

    fun installApk(context: Context, authorities: String, apk: File) {
        context.startActivity(createInstallIntent(context, authorities, apk))
    }

    fun createInstallIntent(context: Context, authorities: String, path: AndroidPath): Intent {
        if (path is AndroidPath.Jvm) {
            return createInstallIntent(context, authorities, path.path.toFile())
        } else {
            //复制到缓存路径
            val dirPath = Constant.downloadCacheDirPath(context)
            val apk = dirPath.resolve(Constant.fileName("apk")).toFile()
            path.copyTo(apk, context)
            return createInstallIntent(context, authorities, apk)
        }
    }

    fun createInstallIntent(context: Context, authorities: String, apk: File): Intent {
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val uri: Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(context, authorities, apk)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            uri = Uri.fromFile(apk)
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        return intent
    }

    fun getVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            return packageInfo.versionCode.toLong()
        }
    }

    fun deleteOldApk(context: Context, oldApkPath: String): Boolean {
        val curVersionCode = getVersionCode(context)
        val apk = File(oldApkPath)
        if (apk.exists()) {
            val oldVersionCode = getVersionCodeByPath(context, oldApkPath)
            if (curVersionCode > oldVersionCode) {
                return apk.delete()
            }
        }
        return false
    }

    private fun getVersionCodeByPath(context: Context, path: String): Long {
        val packageInfo =
            context.packageManager.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 1
        } else {
            return packageInfo?.versionCode?.toLong() ?: 1
        }
    }

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

