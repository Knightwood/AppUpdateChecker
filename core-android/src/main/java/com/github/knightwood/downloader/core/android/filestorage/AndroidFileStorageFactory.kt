package com.github.knightwood.downloader.core.android.filestorage

import android.app.Application
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.filestorage.FileStorageFactory

object AndroidFileStorageFactory {
    /**
     * @param androidPath 文件路径
     * @param application application
     * @param parallel 分块下载（仅使用AndroidPath.JavaFile时有用）
     */
    fun create(
        androidPath: AndroidPath,
        application: Application,
        parallel: Boolean = false,
    ): FileStorage {
        return when (androidPath) {
            is AndroidPath.MediaStoreFile -> {
                AndroidUriFileStorage(androidPath.uri, application)
            }

            is AndroidPath.DocumentFile -> {
                AndroidUriFileStorage(androidPath.uri, application)
            }

            is AndroidPath.JavaFile -> {
                FileStorageFactory.create(parallel, { androidPath.file.toFile() })
            }
        }
    }
}
