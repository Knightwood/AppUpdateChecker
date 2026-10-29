package com.github.knightwood.downloader.ui.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.github.knightwood.downloader.ui.room.converter.DownloadConverters
import com.github.knightwood.downloader.ui.room.dao.DownloadTaskDao
import com.github.knightwood.downloader.ui.room.entity.DownloadChunkEntity
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity

/**
 *
 * 下载任务记录数据库
 *
 * 表结构：
 * - [DownloadTaskEntity]：一条记录对应一个下载任务，主键为任务id
 * - [DownloadChunkEntity]：分块下载进度，主键为 (任务id, 分块序号)，随任务级联删除
 *
 * 修改表结构时需要提升 [version]，并在 `room/migrations` 包下新增 `Migration`，
 * 加入 [MIGRATIONS] 列表；数据库 schema 导出目录为 `ui/schemas`（见 build.gradle.kts）。
 */
@Database(
    entities = [
        DownloadTaskEntity::class,
        DownloadChunkEntity::class,
    ],
    version = DownloadTaskDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(DownloadConverters::class)
abstract class DownloadTaskDatabase : RoomDatabase() {

    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        const val DATABASE_NAME = "download_task.db"
        const val VERSION = 1

        /**
         * 数据库升级迁移，新增版本时在此追加
         */
        private val MIGRATIONS = emptyArray<Migration>()

        @Volatile
        private var instance: DownloadTaskDatabase? = null

        /**
         * 获取数据库单例
         */
        fun getInstance(context: Context): DownloadTaskDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): DownloadTaskDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DownloadTaskDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*MIGRATIONS)
                .build()
        }
    }
}
