package com.github.knightwood.downloader.core.filestorage

import okio.Path
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import java.io.File

class FileStorageImpl(
    val fileProvider: () -> File,
) : FileStorage {
    val sink by lazy { fileProvider().sink().buffer() }

    private fun file(): File {
        return fileProvider().apply {
            if (!exists()) {
                this.createNewFile()
            }
        }
    }

    override fun write(source: ByteArray, offset: Int, byteCount: Int) {
        sink.write(source, offset, byteCount)
    }

    override fun flush() {
        sink.flush()
    }

    override fun close() {
        sink.close()
    }

}
