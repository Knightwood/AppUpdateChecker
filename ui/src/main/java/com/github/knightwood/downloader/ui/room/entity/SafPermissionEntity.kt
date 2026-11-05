package com.github.knightwood.downloader.ui.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SAF 文件夹授权记录表
 *
 * 一条记录对应用户通过 SAF 框架选取并持久化授权的一个目录：
 * - [treeUri]：目录的 tree uri（`content://com.android.externalstorage.documents/tree/...`），
 *   系统侧的持久化权限即绑定在该 uri 上
 * - [displayName]：目录展示名称，选取时从 DocumentProvider 查询得到，查不到时回退为 uri 的 lastPathSegment
 *
 * [treeUri] 建唯一索引并配合 `@Upsert` 使用，重复选取同一目录时只更新一条记录。
 */
@Entity(
    tableName = SafPermissionEntity.TABLE_NAME,
    indices = [
        Index(value = [SafPermissionEntity.COLUMN_TREE_URI], unique = true),
    ]
)
data class SafPermissionEntity(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = SafPermissionEntity.COLUMN_ID)
    val id: Long = 0,

    /** 目录 tree uri 字符串，权限与下载写入都以它为准 */
    @ColumnInfo(name = SafPermissionEntity.COLUMN_TREE_URI)
    val treeUri: String,

    /** 目录展示名称 */
    @ColumnInfo(name = SafPermissionEntity.COLUMN_DISPLAY_NAME)
    val displayName: String,

    /** 是否持有持久化写权限（takePersistableUriPermission 成功即为 true） */
    @ColumnInfo(name = SafPermissionEntity.COLUMN_WRITE_PERMISSION)
    val hasWritePermission: Boolean = true,

    /** 记录创建时间 */
    @ColumnInfo(name = SafPermissionEntity.COLUMN_CREATE_TIME)
    val createTime: Long = System.currentTimeMillis(),

    /** 最后使用时间（每次在弹窗中选中该目录时更新） */
    @ColumnInfo(name = SafPermissionEntity.COLUMN_UPDATE_TIME)
    val updateTime: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TABLE_NAME = "saf_permission_table"
        const val COLUMN_ID = "id"
        const val COLUMN_TREE_URI = "tree_uri"
        const val COLUMN_DISPLAY_NAME = "display_name"
        const val COLUMN_WRITE_PERMISSION = "write_permission"
        const val COLUMN_CREATE_TIME = "create_time"
        const val COLUMN_UPDATE_TIME = "update_time"
    }
}
