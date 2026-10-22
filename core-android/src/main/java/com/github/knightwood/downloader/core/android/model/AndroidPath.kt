package com.github.knightwood.downloader.core.android.model

import android.net.Uri
import okio.Path

sealed interface AndroidPath {
    @JvmInline
    value class JavaFile(val file: Path) : AndroidPath

    @JvmInline
    value class MediaStoreFile(val uri: Uri) : AndroidPath

    @JvmInline
    value class DocumentFile(val uri: Uri) : AndroidPath
}
