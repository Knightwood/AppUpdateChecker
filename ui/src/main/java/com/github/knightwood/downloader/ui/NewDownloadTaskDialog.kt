package com.github.knightwood.downloader.ui

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.knightwood.downloader.ui.screen.DownloadMgrScreenViewModel
import com.github.knightwood.downloader.ui.screen.NewDownloadTaskContent
import com.github.knightwood.downloader.ui.screen.NewTaskUiState
import com.github.knightwood.downloader.ui.screen.SAFSelectorDialog
import com.github.knightwood.downloader.ui.storage.DownloadTarget

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
            val showSafSelector by viewModel.showSafSelector.collectAsStateWithLifecycle()
            val safPermissions by viewModel.safPermissions.collectAsStateWithLifecycle()

            // 弹窗需要拉起系统选择器时在此响应
            LaunchedEffect(Unit) {
                viewModel.openSystemPicker.collect { pickDirectory.launch(null) }
            }

            NewDownloadTaskDialogView(
                state = state,
                onDismiss = {
                    viewModel.resetNewTaskForm()
                    dismissAllowingStateLoss()
                },
                onUrlChange = viewModel::onUrlChanged,
                onFileNameChange = viewModel::onFileNameChanged,
                onProbe = viewModel::probe,
                onTargetChange = viewModel::onTargetChanged,
                onPickDirectory = viewModel::onPickCustomFolder,
                onRequestStoragePermission = {
                    requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                },
                onChunkEnabledChange = viewModel::onChunkEnabledChanged,
                onChunkCountChange = viewModel::onChunkCountChanged,
                onSubmit = viewModel::submitNewTask,
            )

            if (showSafSelector) {
                SAFSelectorDialog(
                    permissions = safPermissions,
                    onDismiss = viewModel::dismissSafSelector,
                    onSelected = viewModel::onSafDirectoryChosen,
                    onDelete = viewModel::onDeleteSafPermission,
                    onAddNew = viewModel::onAddNewSafDirectory,
                )
            }
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

@Composable
private fun NewDownloadTaskDialogView(
    state: NewTaskUiState,
    onDismiss: (() -> Unit)? = null,
    onSubmit: (() -> Unit)? = null,
    onUrlChange: (String) -> Unit,
    onFileNameChange: (String) -> Unit,
    onProbe: () -> Unit,
    onTargetChange: (DownloadTarget) -> Unit,
    onPickDirectory: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onChunkEnabledChange: (Boolean) -> Unit,
    onChunkCountChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "新建下载任务", style = MaterialTheme.typography.titleLarge)
            NewDownloadTaskContent(
                modifier = Modifier.fillMaxWidth(),
                state = state,
                onUrlChange = onUrlChange,
                onFileNameChange = onFileNameChange,
                onProbe = onProbe,
                onTargetChange = onTargetChange,
                onPickDirectory = onPickDirectory,
                onRequestStoragePermission = onRequestStoragePermission,
                onChunkEnabledChange = onChunkEnabledChange,
                onChunkCountChange = onChunkCountChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
                if (onSubmit != null) {
                    Button(onClick = onSubmit, enabled = state.canSubmit) {
                        Text(if (state.submitting) "提交中..." else "开始下载")
                    }
                }
            }
        }
    }
}
