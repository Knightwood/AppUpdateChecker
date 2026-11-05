package com.github.knightwood.downloader.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.knightwood.downloader.ui.room.entity.SafPermissionEntity

/**
 * SAF 文件夹选择弹窗
 *
 * 列出数据库中记录的、已通过 SAF 框架持久化授权的文件夹，支持：
 * - 点击某一项：通过 [onSelected] 回调选中该目录（宿主关闭本弹窗并回填下载位置）
 * - 删除某一项：通过 [onDelete] 回调移除授权记录
 * - 新增文件夹：通过 [onAddNew] 回调拉起系统选择器
 *
 * 弹窗只负责展示与回调，数据来自 [permissions]（宿主从 ViewModel 观测数据库流得到）。
 */
@Composable
fun SAFSelectorDialog(
    permissions: List<SafPermissionEntity>,
    onSelected: (SafPermissionEntity) -> Unit,
    onDelete: (SafPermissionEntity) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择自定义文件夹",
//                style = MaterialTheme.typography.titleLarge,
//                modifier = Modifier.padding(horizontal = 20.dp),
            )
        },
        text = {
            SAFSelectorDialogContent(
                permissions = permissions,
                onSelected = onSelected,
                onDelete = onDelete,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAddNew,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text("新增文件夹")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun SAFSelectorDialogContent(
    permissions: List<SafPermissionEntity>,
    onSelected: (SafPermissionEntity) -> Unit,
    onDelete: (SafPermissionEntity) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "以下是已授权的文件夹，点击选择，或新增其他文件夹",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (permissions.isEmpty()) {
            Text(
                text = "暂无已授权的文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(items = permissions, key = { it.id }) { permission ->
                    SafDirectoryItem(
                        permission = permission,
                        onClick = { onSelected(permission) },
                        onDelete = { onDelete(permission) },
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SafDirectoryItem(
    permission: SafPermissionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (permission.hasWritePermission) "已授权写入" else "授权可能已失效",
                style = MaterialTheme.typography.bodySmall,
                color = if (permission.hasWritePermission) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
