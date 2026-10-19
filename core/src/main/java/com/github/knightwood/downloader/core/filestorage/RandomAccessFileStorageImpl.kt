package com.github.knightwood.downloader.core.filestorage

import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.RandomAccessFile

class RandomAccessFileStorageImpl(
    val fileProvider: () -> File,
) : FileStorage {
    private var isInitializer = false
    // 用 RandomAccessFile 支撑随机写
    private val raf: RandomAccessFile by lazy {
        RandomAccessFile(fileProvider(), "rw").also {
            isInitializer = true
        }
    }

    override fun delete() {
        if (isInitializer) {
            raf.close()
        }
        fileProvider().delete()
    }

    override fun path(): Path {
        return fileProvider().toOkioPath()
    }

    override fun write(source: ByteArray, offset: Int, byteCount: Int) {
        raf.write(source, offset, byteCount)
    }

    override fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int) {
        raf.seek(pos)
        raf.write(source, sourceOffset, byteCount)
    }

    override fun flush() {
        raf.fd.sync()
    }

    override fun close() {
        raf.close()
    }

}
