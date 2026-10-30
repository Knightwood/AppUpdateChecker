package com.github.knightwood.downloader.core.filestorage

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.locks.ReentrantLock

class RandomAccessFileStorageImpl(
    val fileProvider: () -> File,
) : RandomAccessFileStorage {
    private var isInitializer = false
    private val lock = ReentrantLock()

    // 用 RandomAccessFile 支撑随机写
    private val raf: RandomAccessFile by lazy {
        RandomAccessFile(fileProvider(), "rw").also {
            isInitializer = true
        }
    }

    override fun write(source: ByteArray, offset: Int, byteCount: Int) {
        raf.write(source, offset, byteCount)
    }

    override fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int) {
        try {
            lock.lock()
            raf.seek(pos)
            raf.write(source, sourceOffset, byteCount)
        } finally {
            lock.unlock()
        }
    }

    override fun flush() {
        raf.fd.sync()
    }

    override fun close() {
        raf.close()
    }

}
