package com.github.knightwood.appupdate.core.task

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Sink
import okio.Timeout
import okio.buffer
import okio.sink
import java.io.File

interface FileStorage : Sink {
    fun delete()
    fun path(): Path
}

class FileStorageImpl(
    val fileProvider: () -> File,
    val fs: FileSystem = FileSystem.SYSTEM,
) : FileStorage {
    val sink by lazy { fileProvider().sink().buffer() }

    override fun path(): Path {
        return fileProvider().toOkioPath()
    }

    override fun delete() {
        fs.delete(path())
    }

    private fun file(): File {
        return fileProvider().apply {
            if (!exists()) {
                this.createNewFile()
            }
        }
    }

    override fun write(source: Buffer, byteCount: Long) {
        sink.write(source, byteCount)
    }

    override fun flush() {
        sink.flush()
    }

    override fun timeout(): Timeout {
        return sink.timeout()
    }

    override fun close() {
        sink.close()
    }

}
