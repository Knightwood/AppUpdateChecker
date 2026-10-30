package com.github.knightwood.downloader.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType
import com.github.knightwood.downloader.ui.util.formatByteSize
import com.github.knightwood.downloader.ui.util.formatSpeed

/**
 * 下载任务管理器界面
 *
 * - 任务列表来自数据库（应用重启后仍可看到历史记录）
 * - 运行中的任务由下载服务持续把状态/进度写回数据库，界面随之刷新
 * - 支持暂停、继续、停止、重新下载、删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadMgrScreen(viewModel: DownloadMgrScreenViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val newTaskState by viewModel.newTask.collectAsStateWithLifecycle()
    val showNewTaskDialog by viewModel.showNewTaskDialog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pickDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.onSafTreeSelected(uri)
        }
    }
    val requestStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onStoragePermissionResult(granted)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载管理器") },
                actions = {
                    TextButton(
                        onClick = viewModel::clearFinished,
                        enabled = tasks.any { it.state == DownloadStatusType.FINISHED },
                    ) {
                        Text("清空已完成")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = viewModel::openNewTaskDialog) {
                Text("新建下载")
            }
        },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            EmptyTaskHint(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = tasks, key = { it.taskId }) { task ->
                    DownloadTaskCard(task = task, viewModel = viewModel)
                }
            }
        }
    }

    if (showNewTaskDialog) {
        NewDownloadTaskDialog(
            state = newTaskState,
            onDismiss = viewModel::dismissNewTaskDialog,
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

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskItem,
    viewModel: DownloadMgrScreenViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = task.state.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = task.state.labelColor(),
                )
            }

            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = task.summaryText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (task.state == DownloadStatusType.ERROR && !task.errorMessage.isNullOrBlank()) {
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                when (task.state) {
                    DownloadStatusType.STARTING, DownloadStatusType.DOWNLOADING -> {
                        TextButton(onClick = { viewModel.pause(task.taskId) }) { Text("暂停") }
                        TextButton(onClick = { viewModel.cancel(task.taskId) }) { Text("停止") }
                    }

                    DownloadStatusType.PAUSED -> {
                        TextButton(onClick = { viewModel.resume(task.taskId) }) { Text("继续") }
                        TextButton(onClick = { viewModel.cancel(task.taskId) }) { Text("停止") }
                    }

                    DownloadStatusType.IDLE -> {
                        TextButton(onClick = { viewModel.cancel(task.taskId) }) { Text("停止") }
                    }

                    else -> {
                        TextButton(onClick = { viewModel.retry(task.taskId) }) { Text("重新下载") }
                    }
                }
                TextButton(onClick = { viewModel.delete(task.taskId) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun EmptyTaskHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "暂无下载记录", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "点击右下角“新建下载”添加任务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DownloadTaskItem.summaryText(): String = buildString {
    when {
        totalSize <= 0L -> append("文件大小未知")
        // 进度只存在于运行时任务中, 进程重启后的历史记录没有进度可展示
        downloadedSize <= 0L -> append(formatByteSize(totalSize))
        else -> {
            append(formatByteSize(downloadedSize))
            append(" / ")
            append(formatByteSize(totalSize))
            append(" (")
            append((progress * 100).toInt())
            append("%)")
        }
    }
    if (state == DownloadStatusType.DOWNLOADING) {
        append(" · ")
        append(formatSpeed(speed))
    }
    if (chunked) {
        append(" · ")
        append("$chunkCount 分块")
    }
    append(" · ")
    append(targetText)
}

private fun DownloadStatusType.label(): String = when (this) {
    DownloadStatusType.IDLE -> "排队中"
    DownloadStatusType.STARTING -> "准备中"
    DownloadStatusType.DOWNLOADING -> "下载中"
    DownloadStatusType.PAUSED -> "已暂停"
    DownloadStatusType.FINISHED -> "已完成"
    DownloadStatusType.CANCELED -> "已停止"
    DownloadStatusType.ERROR -> "下载失败"
}

@Composable
private fun DownloadStatusType.labelColor() = when (this) {
    DownloadStatusType.STARTING, DownloadStatusType.DOWNLOADING -> MaterialTheme.colorScheme.primary
    DownloadStatusType.FINISHED -> MaterialTheme.colorScheme.tertiary
    DownloadStatusType.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
