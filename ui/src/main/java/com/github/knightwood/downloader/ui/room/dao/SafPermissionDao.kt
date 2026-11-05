package com.github.knightwood.downloader.ui.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.github.knightwood.downloader.ui.room.entity.SafPermissionEntity
import kotlinx.coroutines.flow.Flow

/**
 * SAF 文件夹授权记录DAO
 *
 * 提供授权记录的增删改查：
 * - 新增/更新使用 [upsert]（按 [SafPermissionEntity.treeUri] 唯一索引判断），重复选取同一目录只保留一条
 * - 删除使用 [deleteById] 或按 uri 删除 [deleteByTreeUri]
 * - 查询提供监听流 [observeAll] 与一次性读取 [getAll]、[findByTreeUri]
 */
@Dao
interface SafPermissionDao {

    //<editor-fold desc="查询">

    /**
     * 监听全部授权记录，按最后使用时间倒序（最近使用在前）
     */
    @Query("SELECT * FROM ${SafPermissionEntity.TABLE_NAME} ORDER BY update_time DESC")
    fun observeAll(): Flow<List<SafPermissionEntity>>

    /**
     * 获取全部授权记录
     */
    @Query("SELECT * FROM ${SafPermissionEntity.TABLE_NAME} ORDER BY update_time DESC")
    suspend fun getAll(): List<SafPermissionEntity>

    /**
     * 按 tree uri 查询单条授权记录
     */
    @Query("SELECT * FROM ${SafPermissionEntity.TABLE_NAME} WHERE tree_uri = :treeUri")
    suspend fun findByTreeUri(treeUri: String): SafPermissionEntity?

    /**
     * 统计授权记录数量，界面用于判断“是否已有授权记录”
     */
    @Query("SELECT COUNT(*) FROM ${SafPermissionEntity.TABLE_NAME}")
    suspend fun count(): Int

    //</editor-fold>

    //<editor-fold desc="写入/更新">

    /**
     * 插入或更新单条授权记录（按 tree_uri 判断）
     */
    @Upsert
    suspend fun upsert(permission: SafPermissionEntity)

    /**
     * 批量插入或更新授权记录
     */
    @Upsert
    suspend fun upsertAll(permissions: List<SafPermissionEntity>)

    /**
     * 更新记录的最后使用时间，用于选中目录时置顶
     */
    @Query("UPDATE ${SafPermissionEntity.TABLE_NAME} SET update_time = :updateTime WHERE id = :id")
    suspend fun touch(id: Long, updateTime: Long)

    //</editor-fold>

    //<editor-fold desc="删除">

    /**
     * 按主键删除授权记录
     */
    @Query("DELETE FROM ${SafPermissionEntity.TABLE_NAME} WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 按 tree uri 删除授权记录
     */
    @Query("DELETE FROM ${SafPermissionEntity.TABLE_NAME} WHERE tree_uri = :treeUri")
    suspend fun deleteByTreeUri(treeUri: String)

    /**
     * 删除整条记录对象
     */
    @Delete
    suspend fun delete(permission: SafPermissionEntity)

    /**
     * 清空全部授权记录
     */
    @Query("DELETE FROM ${SafPermissionEntity.TABLE_NAME}")
    suspend fun clearAll()

    //</editor-fold>
}
