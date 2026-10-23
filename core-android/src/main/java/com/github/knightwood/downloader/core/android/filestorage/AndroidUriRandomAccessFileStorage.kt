package com.github.knightwood.downloader.core.android.filestorage

import android.app.Application
import android.net.Uri
import com.github.knightwood.downloader.core.filestorage.FileDescriptorFileStorage
import com.github.knightwood.downloader.core.filestorage.FileStorage

class AndroidUriRandomAccessFileStorage(
    path: Uri,
    application: Application,
) : FileStorage {
    val fdFileStorage = FileDescriptorFileStorage(
        application.contentResolver.openFileDescriptor(path, "w")!!.fileDescriptor
    )

    override fun write(source: ByteArray, offset: Int, byteCount: Int) {
        fdFileStorage.write(source, offset, byteCount)
    }

    override fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int) {
        fdFileStorage.write(source, sourceOffset, byteCount)
    }

    override fun flush() {
        fdFileStorage.flush()
    }

    override fun close() {
        fdFileStorage.close()
    }
}
