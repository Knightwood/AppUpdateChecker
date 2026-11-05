package com.github.knightwood.downloader.ui.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.knightwood.downloader.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 通知权限工具类：判断、申请，以及申请前展示 Material3 风格的说明弹窗
 *
 * 支持的系统版本与处理方式:
 * 1. Android 13(API 33)及以上: 申请 `POST_NOTIFICATIONS` 运行时权限
 * 2. Android 13 以下: 没有该运行时权限, 通知被关闭时只能引导用户去系统设置页开启
 *
 * 说明弹窗、按钮文案、图标等均可通过 [Builder] 配置, 不配置时使用内置文案
 *
 * ```
 * NotificationPermissionHelper.builder(activity)
 *     .showRationaleDialog(true) // 申请前是否显示说明弹窗
 *     .rationaleMessage("开启通知后才能在下拉通知栏查看下载进度")
 *     .onResult { result ->
 *         when (result) {
 *             NotificationPermissionHelper.Result.GRANTED -> startDownload()
 *             NotificationPermissionHelper.Result.DENIED_PERMANENTLY -> showSettingsHint()
 *             else -> {}
 *         }
 *     }
 *     .request()
 * ```
 */
class NotificationPermissionHelper private constructor(
    private val config: Builder,
) {

    private val activity: ComponentActivity
        get() = config.activity

    /**
     * 是否正在申请中(说明弹窗或系统权限弹窗正在展示), 用于防止重复申请
     */
    private var requesting = false

    /**
     * 通过 activity 的 [androidx.activity.result.ActivityResultRegistry] 申请权限,
     * 这样调用方无需在 onCreate 中提前注册 launcher, 也无需重写 onRequestPermissionsResult
     */
    private var launcher: ActivityResultLauncher<String>? = null

    /**
     * 当前是否已获得通知权限(或通知开关处于开启状态)
     */
    fun isGranted(): Boolean {
        return isNotificationEnabled(activity)
    }

    /**
     * 判断并申请通知权限
     *
     * 已获得权限时直接回调 [Result.GRANTED];
     * 未获得权限时视配置先展示说明弹窗, 再发起申请
     */
    fun request() {
        if (requesting) {
            return
        }
        if (isGranted()) {
            deliver(Result.GRANTED)
            return
        }
        if (!supportsRuntimePermission()) {
            // Android 13 以下没有 POST_NOTIFICATIONS, 通知被关闭时只能引导用户去设置页开启
            onNotificationDisabled()
            return
        }
        if (config.showRationale) {
            showRationaleDialog()
        } else {
            requestPermission()
        }
    }

    /**
     * 展示"为何需要通知权限"的说明弹窗, 用户确认后再发起权限申请
     */
    private fun showRationaleDialog() {
        requesting = true
        MaterialAlertDialogBuilder(activity, config.dialogTheme).apply {
            if (config.iconRes != 0) {
                setIcon(config.iconRes)
            }
            setTitle(config.rationaleTitle)
            setMessage(config.rationaleMessage)
            setPositiveButton(config.rationalePositiveText) { _, _ ->
                requestPermission()
            }
            setNegativeButton(config.rationaleNegativeText) { _, _ ->
                deliver(Result.CANCELED)
            }
            setOnCancelListener {
                deliver(Result.CANCELED)
            }
        }.show()
    }

    /**
     * 通知被系统关闭(或 Android 13 以下不支持运行时权限)时的引导弹窗
     */
    private fun onNotificationDisabled() {
        if (!config.showRationale) {
            deliver(Result.DENIED_PERMANENTLY)
            return
        }
        requesting = true
        MaterialAlertDialogBuilder(activity, config.dialogTheme).apply {
            if (config.iconRes != 0) {
                setIcon(config.iconRes)
            }
            setTitle(config.settingsTitle)
            setMessage(config.settingsMessage)
            setPositiveButton(config.settingsPositiveText) { _, _ ->
                openNotificationSettings()
            }
            setNegativeButton(config.rationaleNegativeText) { _, _ ->
                deliver(Result.DENIED_PERMANENTLY)
            }
            setOnCancelListener {
                deliver(Result.DENIED_PERMANENTLY)
            }
        }.show()
    }

    private fun requestPermission() {
        requesting = true
        val launcher = obtainLauncher()
        try {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } catch (e: Exception) {
            // activity 已销毁等异常情况, 按当前实际状态回调
            deliver(if (isGranted()) Result.GRANTED else Result.DENIED)
        }
    }

    private fun obtainLauncher(): ActivityResultLauncher<String> {
        launcher?.let { return it }
        val key = "$LAUNCHER_KEY_PREFIX${System.identityHashCode(this)}"
        val launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            onPermissionResult(granted)
        }
        this.launcher = launcher
        return launcher
    }

    private fun onPermissionResult(granted: Boolean) {
        when {
            granted || isGranted() -> deliver(Result.GRANTED)
            // 仍可再次申请: 用户只是拒绝了本次申请
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) -> deliver(Result.DENIED)
            // 用户拒绝且不再询问, 只能去设置页开启
            else -> deliver(Result.DENIED_PERMANENTLY)
        }
    }

    /**
     * 打开系统通知设置页, 用户返回时重新判断一次结果
     */
    private fun openNotificationSettings() {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                activity.lifecycle.removeObserver(this)
                deliver(if (isGranted()) Result.GRANTED else Result.DENIED_PERMANENTLY)
            }
        }
        activity.lifecycle.addObserver(observer)
        try {
            activity.startActivity(notificationSettingsIntent(activity))
        } catch (e: ActivityNotFoundException) {
            activity.lifecycle.removeObserver(observer)
            deliver(Result.DENIED_PERMANENTLY)
        }
    }

    private fun deliver(result: Result) {
        requesting = false
        config.onResult?.invoke(result)
    }

    /**
     * 通知权限申请结果
     */
    enum class Result {
        /** 已获得通知权限 */
        GRANTED,

        /** 用户拒绝了本次申请, 之后仍可再次申请 */
        DENIED,

        /** 用户拒绝且不再询问, 或系统通知已被关闭, 只能引导用户去设置页开启 */
        DENIED_PERMANENTLY,

        /** 用户在说明弹窗中点击了取消按钮或关闭了弹窗 */
        CANCELED,
    }

    /**
     * 通知权限工具类构造器
     */
    class Builder internal constructor(
        internal val activity: ComponentActivity,
    ) {
        /**
         * 申请权限前是否展示 Material3 风格说明弹窗
         */
        internal var showRationale: Boolean = true

        @DrawableRes
        internal var iconRes: Int = 0

        @StyleRes
        internal var dialogTheme: Int =
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog

        internal var rationaleTitle: CharSequence =
            activity.getString(R.string.app_update_notify_permission_rationale_title)

        internal var rationaleMessage: CharSequence =
            activity.getString(R.string.app_update_notify_permission_rationale_message)

        internal var rationalePositiveText: CharSequence =
            activity.getString(R.string.app_update_notify_permission_rationale_positive)

        internal var rationaleNegativeText: CharSequence =
            activity.getString(R.string.app_update_notify_permission_rationale_negative)

        internal var settingsTitle: CharSequence =
            activity.getString(R.string.app_update_notify_permission_settings_title)

        internal var settingsMessage: CharSequence =
            activity.getString(R.string.app_update_notify_permission_settings_message)

        internal var settingsPositiveText: CharSequence =
            activity.getString(R.string.app_update_notify_permission_settings_positive)

        internal var onResult: ((Result) -> Unit)? = null

        /**
         * 申请权限前是否展示说明弹窗, 默认 true
         *
         * false 时直接发起系统权限申请(Android 13 以下直接回调 [Result.DENIED_PERMANENTLY])
         */
        fun showRationaleDialog(show: Boolean): Builder = apply {
            showRationale = show
        }

        /**
         * 弹窗图标, 默认不显示
         */
        fun icon(@DrawableRes resId: Int): Builder = apply {
            iconRes = resId
        }

        /**
         * 弹窗主题, 默认 Material3 风格
         *
         * 如需跟随宿主主题的弹窗样式, 可传入宿主的 materialAlertDialogTheme
         */
        fun dialogTheme(@StyleRes themeResId: Int): Builder = apply {
            dialogTheme = themeResId
        }

        /**
         * 说明弹窗标题, 默认使用内置文案
         */
        fun rationaleTitle(title: CharSequence): Builder = apply {
            rationaleTitle = title
        }

        /**
         * 说明弹窗标题, 默认使用内置文案
         */
        fun rationaleTitle(@StringRes resId: Int): Builder = apply {
            rationaleTitle = activity.getString(resId)
        }

        /**
         * 说明弹窗内容, 用于解释为何需要通知权限, 默认使用内置文案
         */
        fun rationaleMessage(message: CharSequence): Builder = apply {
            rationaleMessage = message
        }

        /**
         * 说明弹窗内容, 用于解释为何需要通知权限, 默认使用内置文案
         */
        fun rationaleMessage(@StringRes resId: Int): Builder = apply {
            rationaleMessage = activity.getString(resId)
        }

        /**
         * 说明弹窗确认按钮文案, 默认使用内置文案
         */
        fun rationalePositiveText(text: CharSequence): Builder = apply {
            rationalePositiveText = text
        }

        /**
         * 说明弹窗取消按钮文案, 默认使用内置文案
         */
        fun rationaleNegativeText(text: CharSequence): Builder = apply {
            rationaleNegativeText = text
        }

        /**
         * "通知已被关闭, 去系统设置开启"弹窗标题, 默认使用内置文案
         */
        fun settingsTitle(title: CharSequence): Builder = apply {
            settingsTitle = title
        }

        /**
         * "通知已被关闭"弹窗内容, 默认使用内置文案
         */
        fun settingsMessage(message: CharSequence): Builder = apply {
            settingsMessage = message
        }

        /**
         * "通知已被关闭"弹窗确认按钮文案, 默认使用内置文案
         */
        fun settingsPositiveText(text: CharSequence): Builder = apply {
            settingsPositiveText = text
        }

        /**
         * 申请结果回调
         */
        fun onResult(listener: (Result) -> Unit): Builder = apply {
            onResult = listener
        }

        fun build(): NotificationPermissionHelper {
            return NotificationPermissionHelper(this)
        }

        /**
         * 构建并立即判断、申请
         */
        fun request(): NotificationPermissionHelper {
            return build().apply { request() }
        }
    }

    companion object {
        private const val LAUNCHER_KEY_PREFIX = "app_update_notification_permission_"

        @JvmStatic
        fun builder(activity: ComponentActivity): Builder {
            return Builder(activity)
        }

        /**
         * 通知是否可用(权限已授予且通知开关处于开启状态)
         */
        @JvmStatic
        fun isNotificationEnabled(context: Context): Boolean {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /**
         * 系统是否支持 POST_NOTIFICATIONS 运行时权限(Android 13 及以上)
         */
        @JvmStatic
        fun supportsRuntimePermission(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }

        private fun notificationSettingsIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            }
        }
    }
}
