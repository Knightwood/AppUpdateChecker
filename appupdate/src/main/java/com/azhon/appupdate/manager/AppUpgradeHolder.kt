package com.azhon.appupdate.manager

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.azhon.appupdate.R
import com.azhon.appupdate.listener.LifecycleCallbacksAdapter
import com.azhon.appupdate.view.DefaultUpdateDialogFragment
import com.azhon.appupdate.view.SimpleUpdateDialog
import com.azhon.appupdate.view.UpdateDialogActivity

/**
 * @author KnightWood
 *
 */
class UpdateDialogType {
    companion object {
        const val None = 0
        const val Colorful = 1
        const val SimpleDialog = 2
        @Deprecated("未来将会移除")
        const val ColorfulOld = 3
    }
}

//<editor-fold desc="非builder方式">
/**
 * @author KnightWood
 * @receiver FragmentActivity
 * @param downloadManager DownloadManager
 * @return DownloadManager
 */
fun FragmentActivity.downloadApp(downloadManager: DownloadManager): DownloadManager {
    return downloadApp(downloadManager.config)
}


/**
 * @author KnightWood
 * @receiver FragmentActivity
 * @param config DownloadConfig
 * @return DownloadManager
 */
fun FragmentActivity.downloadApp(config: DownloadManager.DownloadConfig): DownloadManager {
    return DownloadManager.getInstance(config, application).let {
        if (it.canDownload()) {
            showUi(it, this)
        } else {
            if (it.config.viewConfig.showNewerToast) {
                Toast.makeText(
                    application, R.string.app_update_latest_version, Toast.LENGTH_SHORT
                ).show()
            }
        }
        it
    }
}

/**
 * @author KnightWood
 * @receiver FragmentActivity
 * @param block [@kotlin.ExtensionFunctionType] Function1<DownloadConfig, Unit>
 * @return DownloadManager
 */
fun FragmentActivity.downloadApp(block: DownloadManager.DownloadConfig.() -> Unit): DownloadManager {
    val config = DownloadManager.DownloadConfig(application)
    config.block()
    return downloadApp(config)
}
//</editor-fold>

//<editor-fold desc="builder方式">
fun DownloadManager.download(activity: FragmentActivity) {
    if (canDownload()) {
        showUi(this, activity)
    } else {
        if (config.viewConfig.showNewerToast) {
            Toast.makeText(
                application, R.string.app_update_latest_version, Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * 这个方法将会忽略配置中的viewType，使用默认UpdateDialogActivity进行下载
 * @receiver DownloadManager
 */
@Deprecated("未来将会移除")
fun DownloadManager.download() {
    application.startActivity(
        Intent(application, UpdateDialogActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    // Fix memory leak
    application.registerActivityLifecycleCallbacks(object :
        LifecycleCallbacksAdapter() {
        override fun onActivityDestroyed(activity: Activity) {
            super.onActivityDestroyed(activity)
            if (this.javaClass.name == activity.javaClass.name) {
                clearListener()
            }
        }
    })

    if (!canDownload()) {
        if (config.viewConfig.showNewerToast) {
            Toast.makeText(
                application, R.string.app_update_latest_version, Toast.LENGTH_SHORT
            ).show()
        }
    }
}


//</editor-fold>

//<editor-fold desc="内置ui显示">
/**
 * @author KnightWood
 * @param downloadManager DownloadManager
 * @param activity FragmentActivity
 */
fun showUi(downloadManager: DownloadManager, activity: FragmentActivity) {
    val application = activity.application

    when (downloadManager.config.updateDialogType) {
        UpdateDialogType.ColorfulOld -> {
            application.startActivity(
                Intent(application, UpdateDialogActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            // Fix memory leak
            application.registerActivityLifecycleCallbacks(object :
                LifecycleCallbacksAdapter() {
                override fun onActivityDestroyed(activity: Activity) {
                    super.onActivityDestroyed(activity)
                    if (this.javaClass.name == activity.javaClass.name) {
                        downloadManager.clearListener()
                    }
                }
            })
        }
        UpdateDialogType.Colorful->{
            DefaultUpdateDialogFragment.open(activity)
        }

        UpdateDialogType.None -> {
            //自定义界面，自行决定何时下载，此处不做额外处理
            //it.startService2Download()
        }

        UpdateDialogType.SimpleDialog -> {
            SimpleUpdateDialog.openAlertDialog(activity, downloadManager)
        }
    }

}

//</editor-fold>
