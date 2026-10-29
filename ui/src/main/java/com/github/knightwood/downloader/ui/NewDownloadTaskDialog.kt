package com.github.knightwood.downloader.ui

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreenViewModel
import com.github.knightwood.downloader.ui.screen.NewDownloadTaskContent

/**
 * 新建下载任务弹窗（DialogFragment 入口）
 *
 * 与界面内嵌的新建任务弹窗共用同一个 [DownloadMgrScreenViewModel]，
 * 便于宿主以 fragment 方式单独弹出新建任务界面。
 *
 * SAF 目录选择、存储权限申请注册在 fragment 上，避免 Compose 在 Dialog 窗口内
 * 找不到 `ActivityResultRegistryOwner`。
 */
class NewDownloadTaskDialog : DialogFragment() {

    private val viewModel: DownloadMgrScreenViewModel by lazy {
        ViewModelProvider(requireActivity())[DownloadMgrScreenViewModel::class.java]
    }

    private val pickDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.onSafTreeSelected(uri)
        }
    }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onStoragePermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(inflater.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val state by viewModel.newTask.collectAsStateWithLifecycle()
            NewDownloadTaskContent(
                state = state,
                onDismiss = {
                    viewModel.resetNewTaskForm()
                    dismissAllowingStateLoss()
                },
                onUrlChange = viewModel::onUrlChanged,
                onFileNameChange = viewModel::onFileNameChanged,
                onProbe = viewModel::probe,
                onTargetChange = viewModel::onTargetChanged,
                onPickDirectory = { pickDirectory.launch(null) },
                onRequestStoragePermission = {
                    requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                },
                onChunkEnabledChange = viewModel::onChunkEnabledChanged,
                onChunkCountChange = viewModel::onChunkCountChanged,
                onSubmit = viewModel::submitNewTask,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
}
