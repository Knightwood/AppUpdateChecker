package com.github.knightwood.downloader.core.android.model

import android.content.Context
import android.net.Uri
import okio.Path
import java.io.File

sealed interface AndroidPath {
    @JvmInline
    value class Jvm(val path: Path) : AndroidPath

    @JvmInline
    value class MediaStore(val uri: Uri) : AndroidPath

    @JvmInline
    value class DocumentFile(val uri: Uri) : AndroidPath
}

fun AndroidPath.copyTo(cacheFile: File, context: Context) {
    when (this) {
        is AndroidPath.DocumentFile -> {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        is AndroidPath.Jvm -> {
            this.path.toFile().inputStream().use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        is AndroidPath.MediaStore -> {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
