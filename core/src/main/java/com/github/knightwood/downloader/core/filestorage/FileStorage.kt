package com.github.knightwood.downloader.core.filestorage

import com.github.knightwood.downloader.core.model.DownloadTaskConf
import com.github.knightwood.downloader.core.util.FileUtil
import okio.Path
import java.io.Closeable
import java.io.File
import java.io.Flushable
import java.io.IOException

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

    fun findCache(md5: String): Path? = FileUtil.findCache(path(), md5)
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
