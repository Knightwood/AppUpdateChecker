package com.github.knightwood.downloader.ui.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.github.knightwood.downloader.ui.room.entity.DownloadChunkEntity
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskWithChunks
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType
import kotlinx.coroutines.flow.Flow

/**
 * 下载任务记录DAO
 *
 * 任务本身与分块进度分表存储，状态更新使用 [updateStatus]、[markStarted] 这类局部更新语句，
 * 避免整行覆盖导致并发写丢失字段。
 *
 * 下载速度不写入数据库，由界面直接读取运行时任务的
 * `DownloadTask.stateFlow` / `DownloadTask.currentDownloadingProgress`。
 *
 * 带 `@Transaction` 的 open 方法为组合操作（Room 只实现抽象方法，open 方法原样保留）。
 */
@Dao
abstract class DownloadTaskDao {

    //<editor-fold desc="任务查询">

    /**
     * 监听全部任务记录，按创建时间倒序（新任务在前）
     */
    @Query("SELECT * FROM download_task_table ORDER BY create_time DESC")
    abstract fun observeAll(): Flow<List<DownloadTaskEntity>>

    /**
     * 监听指定状态的任务记录
     */
    @Query("SELECT * FROM download_task_table WHERE status = :status ORDER BY create_time DESC")
    abstract fun observeAll(status: DownloadStatusType): Flow<List<DownloadTaskEntity>>

    /**
     * 监听单个任务记录，任务不存在时发射null
     */
    @Query("SELECT * FROM download_task_table WHERE task_id = :taskId")
    abstract fun observe(taskId: Int): Flow<DownloadTaskEntity?>

    /**
     * 监听全部任务记录（包含分块进度）
     */
    @Transaction
    @Query("SELECT * FROM download_task_table ORDER BY create_time DESC")
    abstract fun observeAllWithChunks(): Flow<List<DownloadTaskWithChunks>>

    /**
     * 获取全部任务记录
     */
    @Query("SELECT * FROM download_task_table ORDER BY create_time DESC")
    abstract suspend fun getAll(): List<DownloadTaskEntity>

    /**
     * 获取全部任务记录（包含分块进度），用于进程重启后整体恢复任务
     */
    @Transaction
    @Query("SELECT * FROM download_task_table ORDER BY create_time DESC")
    abstract suspend fun getAllWithChunks(): List<DownloadTaskWithChunks>

    /**
     * 查询单个任务记录
     */
    @Query("SELECT * FROM download_task_table WHERE task_id = :taskId")
    abstract suspend fun findById(taskId: Int): DownloadTaskEntity?

    /**
     * 查询单个任务记录（包含分块进度），用于断点续传恢复进度
     */
    @Transaction
    @Query("SELECT * FROM download_task_table WHERE task_id = :taskId")
    abstract suspend fun findWithChunks(taskId: Int): DownloadTaskWithChunks?

    /**
     * 查询等待队列中的任务，按排队顺序排列
     *
     * queue_order >= 0 表示任务处于 `TaskScheduler` 的等待队列中
     */
    @Query("SELECT * FROM download_task_table WHERE queue_order >= 0 ORDER BY queue_order ASC")
    abstract suspend fun getQueuedTasks(): List<DownloadTaskEntity>

    /**
     * 查询除指定状态外的任务，按排队顺序排列，用于恢复未结束的任务
     */
    @Query(
        "SELECT * FROM download_task_table WHERE status != :status " +
            "ORDER BY queue_order ASC, create_time ASC"
    )
    abstract suspend fun getTasksExcept(status: DownloadStatusType): List<DownloadTaskEntity>

    /**
     * 按状态名称查询任务（底层方法，供带枚举参数的便捷方法调用）
     */
    @Query("SELECT * FROM download_task_table WHERE status IN (:statusNames) ORDER BY create_time DESC")
    abstract suspend fun getAllByStatusNames(statusNames: List<String>): List<DownloadTaskEntity>

    /**
     * 按状态查询任务
     */
    suspend fun getAllIn(vararg status: DownloadStatusType): List<DownloadTaskEntity> =
        getAllByStatusNames(status.map { it.name })

    //</editor-fold>

    //<editor-fold desc="分块进度查询">

    /**
     * 查询某任务的全部分块进度，按分块序号排列
     */
    @Query("SELECT * FROM download_chunk_table WHERE task_id = :taskId ORDER BY chunk_index ASC")
    abstract suspend fun getChunks(taskId: Int): List<DownloadChunkEntity>

    /**
     * 查询全部任务的分块进度
     */
    @Query("SELECT * FROM download_chunk_table ORDER BY task_id ASC, chunk_index ASC")
    abstract suspend fun getAllChunks(): List<DownloadChunkEntity>

    //</editor-fold>

    //<editor-fold desc="统计">

    /**
     * 统计任务总数
     */
    @Query("SELECT COUNT(*) FROM download_task_table")
    abstract suspend fun count(): Int

    /**
     * 统计指定状态的任务数量
     */
    @Query("SELECT COUNT(*) FROM download_task_table WHERE status = :status")
    abstract suspend fun count(status: DownloadStatusType): Int

    //</editor-fold>

    //<editor-fold desc="写入/更新">

    /**
     * 插入或更新任务记录（按 [DownloadTaskEntity.taskId] 判断）
     */
    @Upsert
    abstract suspend fun upsert(task: DownloadTaskEntity)

    /**
     * 批量插入或更新任务记录
     */
    @Upsert
    abstract suspend fun upsertAll(tasks: List<DownloadTaskEntity>)

    /**
     * 插入或更新分块进度（按 task_id + chunk_index 判断）
     */
    @Upsert
    abstract suspend fun upsertChunks(chunks: List<DownloadChunkEntity>)

    /**
     * 更新任务状态
     *
     * @param errorMessage 仅 [DownloadStatusType.ERROR] 状态需要传入异常信息，其余状态传null
     */
    @Query(
        "UPDATE download_task_table SET status = :status, error_message = :errorMessage, " +
            "update_time = :updateTime WHERE task_id = :taskId"
    )
    abstract suspend fun updateStatus(
        taskId: Int,
        status: DownloadStatusType,
        errorMessage: String?,
        updateTime: Long,
    )

    /**
     * 更新下载进度（下载过程中高频调用，只写入进度相关列, 暂时不写入速度信息）
     */
    @Query(
        "UPDATE download_task_table SET status = :status, total_size = :totalSize, " +
            "downloaded_size = :downloadedSize, " +
            "update_time = :updateTime WHERE task_id = :taskId"
    )
    abstract suspend fun updateProgress(
        taskId: Int,
        status: DownloadStatusType,
        totalSize: Long,
        downloadedSize: Long,
//        snapSpeed: Double,
//        averageSpeed: Double,
        updateTime: Long,
    )

    /**
     * 更新任务在等待队列中的顺序，传-1表示移出等待队列
     */
    @Query("UPDATE download_task_table SET queue_order = :queueOrder, update_time = :updateTime WHERE task_id = :taskId")
    abstract suspend fun updateQueueOrder(taskId: Int, queueOrder: Int, updateTime: Long)

    /**
     * 记录任务开始下载时间
     */
    @Query(
        "UPDATE download_task_table SET status = :status, start_time = :startTime, " +
            "update_time = :updateTime WHERE task_id = :taskId"
    )
    abstract suspend fun markStarted(
        taskId: Int,
        status: DownloadStatusType,
        startTime: Long,
        updateTime: Long,
    )

    /**
     * 记录任务结束时间（完成/取消/失败）
     */
    @Query(
        "UPDATE download_task_table SET status = :status, error_message = :errorMessage, " +
            "end_time = :endTime, queue_order = -1, update_time = :updateTime WHERE task_id = :taskId"
    )
    abstract suspend fun markEnded(
        taskId: Int,
        status: DownloadStatusType,
        errorMessage: String?,
        endTime: Long,
        updateTime: Long,
    )

    //</editor-fold>

    //<editor-fold desc="删除">

    /**
     * 删除任务记录，分块进度随外键级联删除
     */
    @Query("DELETE FROM download_task_table WHERE task_id = :taskId")
    abstract suspend fun deleteById(taskId: Int)

    /**
     * 删除指定状态的任务记录
     */
    @Query("DELETE FROM download_task_table WHERE status IN (:statusNames)")
    abstract suspend fun deleteByStatusNames(statusNames: List<String>)

    /**
     * 删除指定状态的任务记录
     */
    suspend fun deleteIn(vararg status: DownloadStatusType) =
        deleteByStatusNames(status.map { it.name })

    /**
     * 删除某任务的全部分块进度
     */
    @Query("DELETE FROM download_chunk_table WHERE task_id = :taskId")
    abstract suspend fun deleteChunks(taskId: Int)

    /**
     * 清空全部任务记录（分块进度随外键级联删除）
     */
    @Query("DELETE FROM download_task_table")
    abstract suspend fun clearAll()

    //</editor-fold>

    //<editor-fold desc="组合操作">

    /**
     * 保存任务及其分块进度
     *
     * 新任务或从记录恢复的任务，直接调用此方法即可；
     * 分块进度会先清空再写入，避免分块数量变化后残留旧分块。
     */
    @Transaction
    open suspend fun saveTask(
        task: DownloadTaskEntity,
        chunks: List<DownloadChunkEntity> = emptyList(),
    ) {
        upsert(task)
        replaceChunks(task.taskId, chunks)
    }

    /**
     * 批量保存任务记录
     */
    @Transaction
    open suspend fun saveTasks(tasks: List<DownloadTaskEntity>) {
        upsertAll(tasks)
    }

    /**
     * 覆盖写入任务的分块进度
     */
    @Transaction
    open suspend fun replaceChunks(taskId: Int, chunks: List<DownloadChunkEntity>) {
        deleteChunks(taskId)
        if (chunks.isNotEmpty()) {
            upsertChunks(chunks)
        }
    }

    //</editor-fold>
}
