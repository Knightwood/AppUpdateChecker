package com.github.knightwood.downloader.updater.example

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.task.currentStatus
import com.github.knightwood.downloader.core.util.DownloadFormat
import com.github.knightwood.downloader.updater.model.ApkDownloadParams
import com.github.knightwood.downloader.updater.AppUpdateDialog
import com.github.knightwood.downloader.updater.AppUpdater
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

open class FullScreenUpdateDialog(
    apkDownloadParams: ApkDownloadParams,
) : AppUpdateDialog(apkDownloadParams), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (params.forceUpdate) {
            isCancelable = false
        }
        // DialogFragment 的 Dialog 主题是在 onCreate → onCreateDialog/onGetLayoutInflater 阶段确定的。
        // 在 onCreateView 里调 setStyle，Dialog 的 window 已经用默认主题（Theme.Material3.DayNight.Dialog，默认 windowIsFloating=true）创建完了，
        // windowIsFloating=false、windowBackground=transparent 统统没生效。
        // 必须在 super.onCreate 之前或至少 onCreate 内调用
        setStyle(STYLE_NORMAL, R.style.BaseFullscreenDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_full_screen_update, container, false)
        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // 1. 打开 edge-to-edge（内部会 setDecorFitsSystemWindows(false) 并把系统栏设为透明）
            //    Android 15+ 已强制 edge-to-edge，这里对老系统同样有效；注意它会把
            //    isNavigationBarContrastEnforced 设回 true，所以下面对比度设置必须放在它之后
            WindowCompat.enableEdgeToEdge(window)
            // 2. 让 Dialog 窗口铺满整个屏幕（含状态栏、导航栏区域）
//            window.decorView.setPadding(0, 0, 0, 0)
//            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
//            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            // 3. 关闭导航栏“对比度保护”，否则三键导航底部会叠一层 80% 的黑
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = false
            }

            // 4. 窗口背景保持透明，由根布局的 ?android:attr/colorBackground 负责铺满
            window.setBackgroundDrawableResource(R.color.transparent)

            // 5. 系统栏图标颜色：根据背景色自动调整为浅色或深色
            val isLight =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
            WindowInsetsControllerCompat(window, window.decorView).apply {
                // 背景是浅色，系统栏图标用深色
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
            // 6. 监听系统栏 insets 变化并更新内容区域 padding
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView.findViewById(android.R.id.content)) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    left = bars.left,
                    top = bars.top,
                    right = bars.right,
                    bottom = bars.bottom
                )
                insets
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.requestApplyInsets()
        view.setUpUI()
    }

    @SuppressLint("WrongViewCast")
    private fun View.setUpUI() {
        val btnCancel = findViewById<Button>(R.id.app_update_ib_close)
        val ivBg = findViewById<ImageView>(R.id.app_update_iv_bg)
        val tvTitle = findViewById<TextView>(R.id.app_update_tv_title)
        val tvSize = findViewById<TextView>(R.id.app_update_tv_size)
        val tvDescription = findViewById<TextView>(R.id.app_update_tv_description)
        val progressBar = findViewById<ProgressBar>(R.id.app_update_progress_bar)
        val btnUpdate = findViewById<Button>(R.id.app_update_btn_update)

        btnUpdate.setOnClickListener(this@FullScreenUpdateDialog)
        btnCancel.setOnClickListener(this@FullScreenUpdateDialog)
        tvTitle.text = "发现新版本!"
        if (params.size != null) {
            tvSize.text = "更新大小：${DownloadFormat.formatByteSize(params.size)}"
        }
        tvDescription.text = params.description
        progressBar.visibility = View.GONE
        progressBar.max = 100
        progressBar.progress = 0

        if (params.forceUpdate) {
            btnCancel.visibility = View.GONE
        }

        val currTask = AppUpdater.currTaskFlow
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                currTask.collect { currTask ->
                    if (currTask != null) {
                        progressBar.visibility = View.VISIBLE
                        val taskState = currTask.stateFlow
                        val taskProgressState = currTask.progressFlow
                        combine(taskState, taskProgressState) { state, progress ->
                            state to progress
                        }.collect { (state, progress) ->
                            if (progress != null) {
                                progressBar.progress = (progress.percentage * 100).toInt()
                            }
                            when (state) {
                                is DownloadStatus.DOWNLOADING -> {
                                    btnUpdate.text = "暂停"
                                    btnCancel.text = "取消"
                                }

                                DownloadStatus.CANCELED,
                                is DownloadStatus.ERROR,
                                    -> {
                                    btnUpdate.text = "重新下载"
                                    btnCancel.text = "关闭"
                                }

                                DownloadStatus.FINISHED -> {
                                    btnUpdate.text = "立即安装"
                                    btnCancel.text = "关闭"
                                }

                                DownloadStatus.IDLE -> {
                                    btnUpdate.text = "立即更新"
                                    btnCancel.text = "关闭"
                                }

                                DownloadStatus.PAUSED -> {
                                    btnUpdate.text = "继续更新"
                                    btnCancel.text = "取消"
                                }

                                DownloadStatus.STARTING -> {
                                    btnUpdate.text = "开始更新"
                                    btnCancel.text = "关闭"
                                }
                            }
                        }
                    } else {
                        btnUpdate.visibility = View.VISIBLE
                        btnUpdate.text = "立即更新"
                        btnCancel.text = "关闭"
                    }
                }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.app_update_btn_update -> {
                val currTaskState = AppUpdater.currTask?.currentStatus ?: DownloadStatus.IDLE
                when (currTaskState) {
                    DownloadStatus.IDLE -> {
                        runCatching {
                            AppUpdater.secretDownload(requireContext(), serviceLinker!!, { params })
                        }.onFailure {
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                        }
                    }

                    is DownloadStatus.DOWNLOADING -> {
                        AppUpdater.TaskControl.pause()
                    }

                    DownloadStatus.FINISHED -> {
                        AppUpdater.TaskControl.install(requireContext())
                    }

                    is DownloadStatus.ERROR,
                    DownloadStatus.CANCELED,
                        -> {
                        AppUpdater.TaskControl.retry()
                    }

                    DownloadStatus.PAUSED -> {
                        AppUpdater.TaskControl.resume()
                    }

                    DownloadStatus.STARTING -> {}
                }
            }

            R.id.app_update_ib_close -> {
                val currTaskState = AppUpdater.currTask?.currentStatus ?: DownloadStatus.IDLE
                when (currTaskState) {
                    is DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
                        if (!params.forceUpdate) {
                            AppUpdater.TaskControl.cancel()
                        }
                    }

                    else -> {}
                }
                dismiss()
            }
        }
    }
}

