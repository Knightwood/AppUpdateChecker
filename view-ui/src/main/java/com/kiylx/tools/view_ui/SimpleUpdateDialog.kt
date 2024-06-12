package com.kiylx.tools.view_ui

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.listener.OnButtonClickListener
import com.f_libs.appupdate.listener.OnDownloadListener
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kiylx.tools.view_ui.databinding.ItemUpdateDialogBinding
import java.io.File

/**
 * @author KnightWood
 */
class SimpleUpdateDialog {
    companion object {
        /**
         * 展示弹窗，以及下载安装更新
         */
        fun openAlertDialog(
            activity: Activity, manager: DownloadManager,
            cacheFilePath: String? = ApkUtil.findBackDownloadApk(
                activity.applicationContext,
                manager.config.apkMD5
            )?.absolutePath,
        ) {
            val TAG = "SimpleUpdateDialog"
            var action = Action.downloading
            var apkFile: File? = null
            var mOnDownloadListener: OnDownloadListener? = null
            val mView = ItemUpdateDialogBinding.inflate(activity.layoutInflater)
            //弹窗
            val dialogBuilder = MaterialAlertDialogBuilder(
                activity,
                R.style.MyAlertTheme
            ).apply {
                setTitle(
                    String.format(
                        activity.getString(R.string.app_update_dialog_new),
                        manager.config.apkVersionName
                    )
                )
                setView(mView.root)
                setPositiveButton(R.string.update, null)
                val icon =
                    if (manager.config.smallIcon > 0) {
                        AppCompatResources.getDrawable(context, manager.config.smallIcon)
                    } else {
                        null
                    } ?: let {
                        val tmp = AppCompatResources.getDrawable(
                            context,
                            R.drawable.baseline_system_update_24
                        )!!
                        DrawableCompat.setTint(
                            tmp,
                            MaterialColors.getColor(
                                context,
                                com.google.android.material.R.attr.colorSecondary,
                                ""
                            )
                        )
                        tmp
                    }
                setIcon(icon)
                setCancelable(!manager.config.forcedUpgrade)
                if (!manager.config.forcedUpgrade) {
                    setNegativeButton(R.string.cancel, null)
                }
                mView.appUpdateTvSize.setText(
                    String.format(
                        activity.getString(R.string.update_size_tv),
                        manager.config.apkSize
                    )
                )
                mView.appUpdateTvDescription.text =
                    manager.config.apkDescription.replace("\\n", "\n")
            }

            fun showProgressUi(positiveButton: Button) {
                mView.appUpdateProgressBar.visibility = View.VISIBLE
                mView.appUpdateProgressBar.max = 100
                mView.appUpdateProgressBar.progress = 0
                positiveButton.isEnabled = false
            }
            dialogBuilder.show().apply {
                val window: Window = this.getWindow()!!
                //判断是否横屏
//                if (activity.resources.configuration.orientation != ORIENTATION_LANDSCAPE) {
//                    val width: Int = activity.getResources().getDisplayMetrics().widthPixels
//                    window.setLayout(
//                        (width * 0.9).toInt(),
//                        DensityUtil.dip2px(activity, 600f).toInt()
//                    )
//                } else {
//                    val height: Int = activity.getResources().getDisplayMetrics().heightPixels
//                    window.setLayout(
//                        DensityUtil.dip2px(activity, 450f).toInt(),
//                        (height * 0.95).toInt(),
//                    )
//                }
//                window.findViewById<TextView>(androidx.appcompat.R.id.alertTitle).run {
//                    textSize = 24f
//                }
                getButton(AlertDialog.BUTTON_POSITIVE).also { positiveButton ->
                    if (manager.downloading) {
                        showProgressUi(positiveButton)
                    }
                    if (cacheFilePath != null) {
                        Log.d(TAG, "openAlertDialog: $cacheFilePath")
                        action = Action.readyInstall
                        positiveButton.setText(R.string.install)
                    }
                    mOnDownloadListener = object : OnDownloadListener {
                        override fun cancel() {}

                        override fun done(apk: File) {
                            if (!manager.config.jumpInstallPage) {
                                apkFile = apk
                                positiveButton.isEnabled = true
                                action = Action.readyInstall
                                positiveButton.setText(R.string.install)
                            } else {
                                dismiss()
                            }
                        }

                        override fun downloading(max: Int, progress: Int) {
//                        Log.d(TAG, "downloading:max-> $max ,progress-> $progress")
                            val curr = (progress / max.toDouble() * 100.0).toInt()
                            mView.appUpdateProgressBar.progress = curr
                        }

                        override fun error(e: Throwable) {
                            Log.e(TAG, "error: 下载错误", e)
                        }

                        override fun start() {
                            showProgressUi(positiveButton)
                        }
                    }
                    mOnDownloadListener?.let {
                        manager.config.registerDownloadListener(it)
                    }
                    positiveButton.setOnClickListener {
                        manager.config.onButtonClickListener?.onButtonClick(OnButtonClickListener.UPDATE)
                        if (action == Action.readyInstall) {
                            if (cacheFilePath != null) {
                                apkFile = File(cacheFilePath)
                            }
                            apkFile?.let { it1 ->
                                dismiss()
                                ApkUtil.installApk(activity, Constant.AUTHORITIES!!, it1)
                            }
                        } else {
                            //下载
                            manager.directDownload()
                        }

                    }
                }
                getButton(AlertDialog.BUTTON_NEGATIVE).also { negativeButton ->
                    negativeButton.setOnClickListener {
                        dismiss()
                        manager.cancel()
                        manager.config.onButtonClickListener?.onButtonClick(OnButtonClickListener.CANCEL)
                        manager.config.onDownloadListeners.remove(mOnDownloadListener)
                    }
                }
            }
        }
    }
}