package com.github.knightwood.downloader.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.knightwood.downloader.core.util.DownloadFormat
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType

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
    val showSafSelector by viewModel.showSafSelector.collectAsStateWithLifecycle()
    val safPermissions by viewModel.safPermissions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 当前展示详情的任务id：只存 id，详情从 tasks 取最新项，保证进度/状态实时刷新
    var detailTaskId by remember { mutableStateOf<Int?>(null) }
    val detailTask = tasks.find { it.taskId == detailTaskId }
    val sheetState = rememberModalBottomSheetState()

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

    // ViewModel 判定需要拉起系统选择器时（首次选取 / 弹窗内新增）在此响应
    LaunchedEffect(viewModel) {
        viewModel.openSystemPicker.collect { pickDirectory.launch(null) }
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
                Row{
                    Icon(imageVector = Icons.Default.Add, contentDescription = "新建下载")
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("新建下载")
                }
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
                    DownloadTaskCard(
                        task = task,
                        viewModel = viewModel,
                        onClick = { detailTaskId = task.taskId },
                    )
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
            onPickDirectory = viewModel::onPickCustomFolder,
            onRequestStoragePermission = {
                requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            },
            onChunkEnabledChange = viewModel::onChunkEnabledChanged,
            onChunkCountChange = viewModel::onChunkCountChanged,
            onSubmit = viewModel::submitNewTask,
        )
    }

    if (showSafSelector) {
        SAFSelectorDialog(
            permissions = safPermissions,
            onDismiss = viewModel::dismissSafSelector,
            onSelected = viewModel::onSafDirectoryChosen,
            onDelete = viewModel::onDeleteSafPermission,
            onAddNew = viewModel::onAddNewSafDirectory,
        )
    }

    // 点击卡片后从屏幕底部弹出任务详情；任务被删除（detailTask 为空）时自动收起
    if (detailTask != null) {
        ModalBottomSheet(
            onDismissRequest = { detailTaskId = null },
            sheetState = sheetState,
        ) {
            TaskDetailSheet(task = detailTask, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadTaskCard(
    task: DownloadTaskItem,
    viewModel: DownloadMgrScreenViewModel,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Column(modifier = Modifier) {
                        Text(
                            text = task.name,
//                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.size(4.dp, 12.dp))
//                    Text(
//                        text = task.url,
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                        maxLines = 1,
//                        overflow = TextOverflow.Ellipsis,
//                    )
                    }
                },
                supportingContent = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            text = task.summaryText(),
//                            style = MaterialTheme.typography.bodySmall,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (task.state == DownloadStatusType.ERROR && !task.errorMessage.isNullOrBlank()) {
                            Text(
                                text = task.errorMessage,
//                                style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.error,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                trailingContent = {
                    Text(
                        text = task.state.label(),
//                        style = MaterialTheme.typography.labelLarge,
                        color = task.state.labelColor(),
                    )
                }
            )
            TaskActionRow(
                modifier = Modifier.align(Alignment.End),
                task = task, viewModel = viewModel
            )
        }
    }
}

/**
 * 任务操作按钮行，卡片与详情弹窗复用
 *
 * 根据任务状态展示暂停/继续/停止/重试等操作，删除按钮始终可见。
 * 内部消费点击，不会触发外层卡片的详情弹窗。
 */
@Composable
private fun TaskActionRow(
    modifier: Modifier = Modifier,
    task: DownloadTaskItem,
    viewModel: DownloadMgrScreenViewModel,
) {
    Row(
        modifier = modifier,
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

            DownloadStatusType.CANCELED -> {
                TextButton(onClick = { viewModel.retry(task.taskId) }) { Text("恢复") }
            }

            DownloadStatusType.ERROR -> {
                TextButton(onClick = { viewModel.retry(task.taskId) }) { Text("重试") }
            }

            DownloadStatusType.FINISHED -> {
                // 不处理
            }
        }
        TextButton(onClick = { viewModel.delete(task.taskId) }) { Text("删除") }
    }
}

/**
 * 任务详情底部弹窗内容
 *
 * 完整展示任务名称、状态、进度、速度、分块、保存位置与下载地址，
 * 底部复用 [TaskActionRow] 提供操作入口。
 */
@Composable
private fun TaskDetailSheet(
    task: DownloadTaskItem,
    viewModel: DownloadMgrScreenViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = task.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        LinearProgressIndicator(
            progress = { task.progress },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        DetailRow(label = "状态", value = task.state.label(), valueColor = task.state.labelColor())
        DetailRow(label = "进度", value = task.progressText())
        if (task.state == DownloadStatusType.DOWNLOADING) {
            DetailRow(label = "下载速度", value = DownloadFormat.formatSpeed(task.speed))
        }
        DetailRow(label = "分块下载", value = if (task.chunked) "${task.chunkCount} 个分块" else "单线程")
        DetailRow(label = "保存位置", value = task.targetText, singleLine = false)
        DetailRow(label = "下载地址", value = task.url, singleLine = false)

        if (task.state == DownloadStatusType.ERROR && !task.errorMessage.isNullOrBlank()) {
            DetailRow(
                label = "错误信息",
                value = task.errorMessage,
                valueColor = MaterialTheme.colorScheme.error,
                singleLine = false
            )
        }

        HorizontalDivider()

        TaskActionRow(modifier = Modifier.align(Alignment.End), task = task, viewModel = viewModel)
    }
}

/**
 * 详情弹窗中的一行键值信息，[singleLine] 为 false 时允许长文本（如下载地址）换行
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    singleLine: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 12.dp)
                .widthIn(min = 64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1f),
            maxLines = if (singleLine) 1 else 4,
            overflow = TextOverflow.Ellipsis,
        )
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

/**
 * 详情弹窗中的进度文本：已下载 / 总大小 (百分比)
 */
private fun DownloadTaskItem.progressText(): String = when {
    totalSize <= 0L -> "大小未知"
    downloadedSize <= 0L -> DownloadFormat.formatByteSize(totalSize)
    else -> buildString {
        append(DownloadFormat.formatByteSize(downloadedSize))
        append(" / ")
        append(DownloadFormat.formatByteSize(totalSize))
        append(" (")
        append((progress * 100).toInt())
        append("%)")
    }
}

private fun DownloadTaskItem.summaryText(): String = buildString {
    when {
        totalSize <= 0L -> append("文件大小未知")
        // 没有进度快照时只展示文件总大小
        downloadedSize <= 0L -> append(DownloadFormat.formatByteSize(totalSize))
        else -> {
            append(DownloadFormat.formatByteSize(downloadedSize))
            append(" / ")
            append(DownloadFormat.formatByteSize(totalSize))
            append(" (")
            append((progress * 100).toInt())
            append("%)")
        }
    }
    if (state == DownloadStatusType.DOWNLOADING) {
        append(" · ")
        append(DownloadFormat.formatSpeed(speed))
    }
    if (chunked) {
        append(" · ")
        append("$chunkCount 分块")
    }
//    append(" · ")
//    append(targetText)
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
