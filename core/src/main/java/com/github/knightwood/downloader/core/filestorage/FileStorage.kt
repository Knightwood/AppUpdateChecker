package com.github.knightwood.downloader.core.filestorage

import com.github.knightwood.downloader.core.model.DownloadTaskConf
import java.io.Closeable
import java.io.File
import java.io.Flushable
import java.io.IOException

interface FileStorage : Closeable, Flushable {

    @Throws(IOException::class)
    fun write(source: ByteArray, byteCount: Int) = write(source, 0, byteCount)

    /**
     * Like OutputStream.write, this writes byteCount bytes of source, starting at offset.
     */
    @Throws(IOException::class)
    fun write(source: ByteArray, offset: Int, byteCount: Int)

    @Throws(IOException::class)
    override fun flush()

    @Throws(IOException::class)
    override fun close()

}

interface RandomAccessFileStorage : FileStorage {
    @Throws(IOException::class)
    fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int)
}

object FileStorageFactory {

    fun create(parallel: Boolean = false, fileProvider: () -> File): FileStorage {
        return if (parallel) {
            RandomAccessFileStorageImpl(fileProvider)
        } else {
            FileStorageImpl(fileProvider)
        }
    }

    fun create(conf: DownloadTaskConf): FileStorage {
        return create(conf.isSupportChunkDownload, { conf.path.toFile() })
    }
}
