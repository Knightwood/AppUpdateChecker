package com.github.knightwood.downloader.updater.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.task.currentStatus
import com.github.knightwood.downloader.core.util.DownloadFormat
import com.github.knightwood.downloader.updater.ApkDownloadParams
import com.github.knightwood.downloader.updater.AppUpdateDialog
import com.github.knightwood.downloader.updater.AppUpdater
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

open class ColorfulUpdateDialog(
    apkDownloadParams: ApkDownloadParams,
) : AppUpdateDialog(apkDownloadParams), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (params.forceUpdate) {
            isCancelable = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_colorful_update, container, false)
        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(R.color.transparent)
            val attrs = requireContext().obtainStyledAttributes(
                intArrayOf(android.R.attr.dialogPreferredPadding)
            )
            val padding = attrs.getDimensionPixelSize(0, 0)
            attrs.recycle()
            val width = resources.displayMetrics.widthPixels - padding * 3
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setUpUI()
    }

    private fun View.setUpUI() {
        val ibClose = findViewById<View>(R.id.app_update_ib_close)
        val vLine = findViewById<View>(R.id.app_update_line)
        val ivBg = findViewById<ImageView>(R.id.app_update_iv_bg)
        val tvTitle = findViewById<TextView>(R.id.app_update_tv_title)
        val tvSize = findViewById<TextView>(R.id.app_update_tv_size)
        val tvDescription = findViewById<TextView>(R.id.app_update_tv_description)
        val progressBar = findViewById<NumberProgressBar>(R.id.app_update_progress_bar)
        val btnUpdate = findViewById<Button>(R.id.app_update_btn_update)

        btnUpdate.setOnClickListener(this@ColorfulUpdateDialog)
        ibClose.setOnClickListener(this@ColorfulUpdateDialog)
        tvTitle.text = "发现新版本!"
        if (params.size != null) {
            tvSize.text = "更新大小：${DownloadFormat.formatByteSize(params.size)}"
        }
        tvDescription.text = params.description
        progressBar.visibility = View.GONE
        progressBar.max = 100
        progressBar.progress = 0

        if (params.forceUpdate) {
            ibClose.visibility = View.GONE
            vLine.visibility = View.GONE
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
                                }

                                DownloadStatus.CANCELED,
                                is DownloadStatus.ERROR,
                                    -> {
                                    btnUpdate.text = "重新下载"
                                }

                                DownloadStatus.FINISHED -> {
                                    btnUpdate.text = "立即安装"
                                }

                                DownloadStatus.IDLE -> {
                                    btnUpdate.text = "立即更新"
                                }

                                DownloadStatus.PAUSED -> {
                                    btnUpdate.text = "继续更新"
                                }

                                DownloadStatus.STARTING -> {
                                    btnUpdate.text = "开始更新"
                                }
                            }
                        }
                    } else {
                        btnUpdate.visibility = View.VISIBLE
                        btnUpdate.text = "立即更新"
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
                        AppUpdater.secretDownload(requireContext(), serviceLinker!!, { params })
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
