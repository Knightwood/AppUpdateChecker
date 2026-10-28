package com.github.knightwood.downloader.ui.room

import androidx.room.RoomDatabase
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity

@androidx.room.Database(
    entities = [DownloadTaskEntity::class],
    version = 1
)
abstract class DownloadTaskDatabase : RoomDatabase() {

}
