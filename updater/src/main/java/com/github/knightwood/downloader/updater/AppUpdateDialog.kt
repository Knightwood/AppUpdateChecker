package com.github.knightwood.downloader.updater

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.knightwood.downloader.core.android.service.DownloadService
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.updater.model.ApkDownloadParams
import com.github.knightwood.downloader.updater.model.DownloadStatusType
import com.github.knightwood.downloader.updater.util.NotificationPermissionHelper

open class AppUpdateDialog(
    val params: ApkDownloadParams,
) : DialogFragment() {
    private var isBound = false
    protected var serviceLinker: DownloadServiceLinker? = null

    private val downloadServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            (service as? DownloadServiceLinker)?.let { linker ->
                serviceLinker = linker
            }
        }


        override fun onServiceDisconnected(name: ComponentName) {
            serviceLinker = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (params.forceUpdate) {
            isCancelable = false
        }
        linkDownloadService()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            this.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currTask = AppUpdater.currTaskFlow.collectAsState(null)
                val taskState = currTask.value?.stateFlow?.collectAsState()
                val taskProgressState = currTask.value?.progressFlow?.collectAsState()
                AppUpdateDialogUI(
                    params = params,
                    stateProvider = { taskState?.value?.let { DownloadStatusType.of(it) } },
                    progressProvider = { taskProgressState?.value },
                    onDismissRequest = { dismiss() },
                    onCancelDownloadRequest = {
                        AppUpdater.TaskControl.cancel()
                        dismiss()
                    },
                    onDownloadRequest = {
                        runCatching {
                            checkPermission()
                            AppUpdater.secretDownload(
                                context = requireContext(),
                                linker = serviceLinker!!,
                                paramProvider = { params }
                            )
                        }.onFailure {
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onPauseRequest = {
                        AppUpdater.TaskControl.pause()
                    },
                    onResumeRequest = {
                        AppUpdater.TaskControl.resume()
                    },
                    onInstallRequest = {
                        AppUpdater.TaskControl.install(context = requireContext())
                    },
                    onRetryRequest = {
                        AppUpdater.TaskControl.retry()
                    },
                )
            }
        }
    }


    private fun checkPermission() {
        NotificationPermissionHelper.builder(requireActivity())
            .rationaleTitle(R.string.app_update_notify_permission_rationale_title)
            .rationaleMessage(R.string.app_update_notify_permission_rationale_message)
            .showRationaleDialog(true)
            .onResult { result ->
                when (result) {
                    NotificationPermissionHelper.Result.GRANTED -> {

                    }

                    NotificationPermissionHelper.Result.DENIED_PERMANENTLY ->
                        Toast.makeText(
                            requireContext(),
                            R.string.app_update_notify_permission_settings_title,
                            Toast.LENGTH_LONG
                        ).show()

                    else -> {}
                }
            }
            .request()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            runCatching { requireContext().unbindService(downloadServiceConnection) }
            isBound = false
        }
    }

    private fun linkDownloadService() {
        DownloadService.start(requireContext())
        isBound = requireContext().bindService(
            Intent(requireContext(), DownloadService::class.java),
            downloadServiceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    companion object {

        fun display(
            fragmentManager: FragmentManager,
            params: ApkDownloadParams,
        ) {
            val fragment = AppUpdateDialog(params)
            fragment.show(fragmentManager, "AppUpdateDialog#${params.updateId}")
        }
    }
}
