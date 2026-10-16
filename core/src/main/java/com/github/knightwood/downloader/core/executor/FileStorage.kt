package com.github.knightwood.downloader.core.executor

import okio.Path
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import java.io.Closeable
import java.io.File
import java.io.Flushable
import java.io.IOException
import java.io.RandomAccessFile

interface FileStorage : Closeable, Flushable {
    fun delete()

    fun path(): Path

    @Throws(IOException::class)
    fun write(source: ByteArray, byteCount: Int) = write(source, 0, byteCount)

    /**
     * Like OutputStream.write, this writes byteCount bytes of source, starting at offset.
     */
    @Throws(IOException::class)
    fun write(source: ByteArray, offset: Int, byteCount: Int)

    /**
     * 在文件指定位置(pos), 从source的sourceOffset位置读取byteCount字节写入文件
     *
     * @param pos 指定此参数,从文件指定位置写入source
     */
    @Throws(IOException::class)
    fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int)

    @Throws(IOException::class)
    override fun flush()

    @Throws(IOException::class)
    override fun close()
}

class FileStorageImpl(
    val fileProvider: () -> File,
) : FileStorage {
    val sink by lazy { fileProvider().sink().buffer() }

    override fun path(): Path {
        return fileProvider().toOkioPath()
    }

    override fun delete() {
        fileProvider().delete()
    }

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

    /**
     * 不支持文件随机位置写入
     */
    override fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int) {
        write(source, sourceOffset, byteCount)
    }

    override fun flush() {
        sink.flush()
    }

    override fun close() {
        sink.close()
    }

}


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
