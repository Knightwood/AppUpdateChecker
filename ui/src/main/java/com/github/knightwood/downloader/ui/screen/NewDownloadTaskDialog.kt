package com.github.knightwood.downloader.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.ui.storage.DownloadTarget
import com.github.knightwood.downloader.ui.util.formatByteSize
import kotlin.math.roundToInt

/**
 * 新建下载任务弹窗
 *
 * 输入下载地址后可探测文件信息（文件名、大小、是否支持多线程下载），
 * 并选择文件保存位置：应用缓存目录、系统下载目录或 SAF 选取的任意文件夹。
 */
@Composable
fun NewDownloadTaskDialog(
    state: NewTaskUiState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onFileNameChange: (String) -> Unit,
    onProbe: () -> Unit,
    onTargetChange: (DownloadTarget) -> Unit,
    onPickDirectory: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onChunkEnabledChange: (Boolean) -> Unit,
    onChunkCountChange: (Int) -> Unit,
    onSubmit: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NewDownloadTaskContent(
            state = state,
            onDismiss = onDismiss,
            onUrlChange = onUrlChange,
            onFileNameChange = onFileNameChange,
            onProbe = onProbe,
            onTargetChange = onTargetChange,
            onPickDirectory = onPickDirectory,
            onRequestStoragePermission = onRequestStoragePermission,
            onChunkEnabledChange = onChunkEnabledChange,
            onChunkCountChange = onChunkCountChange,
            onSubmit = onSubmit,
        )
    }
}

/**
 * 新建任务弹窗的内容，供 [NewDownloadTaskDialog] 与 DialogFragment 入口复用
 */
@Composable
internal fun NewDownloadTaskContent(
    state: NewTaskUiState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onFileNameChange: (String) -> Unit,
    onProbe: () -> Unit,
    onTargetChange: (DownloadTarget) -> Unit,
    onPickDirectory: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onChunkEnabledChange: (Boolean) -> Unit,
    onChunkCountChange: (Int) -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
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

            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                label = { Text("下载地址") },
                placeholder = { Text("https://example.com/app.apk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onProbe,
                    enabled = state.url.isNotBlank() && state.probe !is ProbeState.Loading,
                ) {
                    Text(if (state.probe is ProbeState.Loading) "探测中..." else "探测文件信息")
                }
                ProbeSummary(state)
            }

            OutlinedTextField(
                value = state.fileName,
                onValueChange = onFileNameChange,
                label = { Text("文件名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = "保存位置", style = MaterialTheme.typography.titleSmall)

            TargetOption(
                title = "应用缓存目录",
                description = "externalCacheDir/Download，无需权限，卸载应用后文件被清除",
                selected = state.target == DownloadTarget.AppCache,
                onClick = { onTargetChange(DownloadTarget.AppCache) },
            )
            TargetOption(
                title = "系统下载目录",
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "文件管理器的 Download 文件夹，Android 10 及以上无需权限"
                } else {
                    "文件管理器的 Download 文件夹，需要存储权限"
                },
                selected = state.target == DownloadTarget.PublicDownloads,
                onClick = { onTargetChange(DownloadTarget.PublicDownloads) },
            )
            TargetOption(
                title = "自定义文件夹",
                description = when (val target = state.target) {
                    is DownloadTarget.SafDirectory -> "已选择: ${target.treeUri.lastPathSegment ?: target.treeUri}"
                    else -> "通过系统文件选择器指定任意目录"
                },
                selected = state.target is DownloadTarget.SafDirectory,
                onClick = onPickDirectory,
            )

            if (needStoragePermission(context, state.target)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Android 10 以下写入下载目录需要存储权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestStoragePermission) { Text("授权") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "分块（多线程）下载", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = chunkHint(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.useChunk,
                    onCheckedChange = onChunkEnabledChange,
                    enabled = state.chunkAvailable,
                )
            }

            if (state.chunkAvailable) {
                ChunkCountSlider(
                    count = state.chunkCount,
                    maxCount = state.maxChunkCount,
                    enabled = state.chunkEnabled,
                    onCountChange = onChunkCountChange,
                )
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = onSubmit, enabled = state.canSubmit) {
                    Text(if (state.submitting) "提交中..." else "开始下载")
                }
            }
        }
    }
}

/**
 * 分块数量滑块
 *
 * 范围固定为 [NewTaskUiState.MIN_CHUNK_COUNT] ~ [NewTaskUiState.maxChunkCount]，
 * 上限由“文件长度 / 最小分块大小”决定，保证每块不小于 `HttpConstant.MIN_CHUNK_SIZE`。
 */
@Composable
private fun ChunkCountSlider(
    count: Int,
    maxCount: Int,
    enabled: Boolean,
    onCountChange: (Int) -> Unit,
) {
    val minCount = NewTaskUiState.MIN_CHUNK_COUNT
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "分块数量",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (maxCount > minCount) {
                    "$count / 最多 $maxCount 个"
                } else {
                    "最多 $maxCount 个"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (maxCount > minCount) {
            Slider(
                value = count.coerceIn(minCount, maxCount).toFloat(),
                onValueChange = { onCountChange(it.roundToInt()) },
                valueRange = minCount.toFloat()..maxCount.toFloat(),
                steps = maxCount - minCount - 1,
                enabled = enabled,
            )
        } else {
            Text(
                text = "文件较小，只能分 $maxCount 块下载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProbeSummary(state: NewTaskUiState) {
    when (val probe = state.probe) {
        ProbeState.Idle -> Text(
            text = "点击探测可自动填充文件名",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ProbeState.Loading -> Text(
            text = "探测中...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is ProbeState.Failed -> Text(
            text = probe.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        is ProbeState.Success -> Text(
            text = buildString {
                append(formatByteSize(probe.meta.length))
                append(" · ")
                append(if (probe.meta.acceptRanges) "支持多线程" else "仅单线程")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TargetOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chunkHint(state: NewTaskUiState): String = when {
    !state.multiThreadSupported -> "服务器未声明 Accept-Ranges，仅支持单线程下载"
    !DownloadTarget.supportsParallel(state.target) -> "当前保存位置不支持随机写入，仅支持单线程下载"
    (state.meta?.length ?: 0L) < HttpConstant.MIN_CHUNK_SIZE -> "文件太小，分块下载无收益"
    else -> "并发拉取多个分块，可提升大文件下载速度"
}

private fun needStoragePermission(context: Context, target: DownloadTarget): Boolean {
    if (target != DownloadTarget.PublicDownloads) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    ) != PackageManager.PERMISSION_GRANTED
}
