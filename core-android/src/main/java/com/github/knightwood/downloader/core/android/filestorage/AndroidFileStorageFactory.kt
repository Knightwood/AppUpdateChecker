package com.github.knightwood.downloader.core.android.filestorage

import android.app.Application
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.filestorage.FileStorageFactory

object AndroidFileStorageFactory {
    /**
     * @param androidPath 文件路径
     * @param application application
     * @param parallel 分块下载支持（仅使用AndroidPath.JavaFile时有用）
     */
    fun create(
        androidPath: AndroidPath,
        application: Application,
        parallel: Boolean = false,
    ): FileStorage {
        return when (androidPath) {
            is AndroidPath.MediaStore -> {
                if (parallel) {
                    AndroidUriRandomAccessFileStorage(androidPath.uri, application)
                } else {
                    AndroidUriFileStorage(androidPath.uri, application)
                }
            }

            is AndroidPath.DocumentFile -> {
                if (parallel) {
                    AndroidUriRandomAccessFileStorage(androidPath.uri, application)
                } else {
                    AndroidUriFileStorage(androidPath.uri, application)
                }
            }

            is AndroidPath.Jvm -> {
                FileStorageFactory.create(parallel, { androidPath.path.toFile() })
            }
        }
    }
}
