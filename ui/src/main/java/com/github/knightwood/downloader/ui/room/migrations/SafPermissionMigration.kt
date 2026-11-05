package com.github.knightwood.downloader.ui.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.knightwood.downloader.ui.room.entity.SafPermissionEntity

/**
 * 1 -> 2：新增 SAF 文件夹授权记录表 [SafPermissionEntity]
 *
 * 仅建表与索引，不改动已有任务/分块表。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `${SafPermissionEntity.TABLE_NAME}` (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "tree_uri TEXT NOT NULL, " +
                "display_name TEXT NOT NULL, " +
                "write_permission INTEGER NOT NULL, " +
                "create_time INTEGER NOT NULL, " +
                "update_time INTEGER NOT NULL" +
                ")"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_${SafPermissionEntity.TABLE_NAME}_${SafPermissionEntity.COLUMN_TREE_URI}` " +
                "ON `${SafPermissionEntity.TABLE_NAME}` (`${SafPermissionEntity.COLUMN_TREE_URI}`)"
        )
    }
}
