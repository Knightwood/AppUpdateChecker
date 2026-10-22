package com.github.knightwood.downloader.core.android.filestorage

import android.app.Application
import android.net.Uri
import com.github.knightwood.downloader.core.filestorage.FileStorage
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import kotlin.getValue

class AndroidUriFileStorage(
    private val path: Uri,
    private val application: Application,
) : FileStorage {
    val outStream by lazy { application.contentResolver.openOutputStream(path)!!.sink().buffer() }

    override fun delete() {
        application.contentResolver.delete(path, null, null)
    }

    override fun findCache(md5: String): Path? {
        return null
    }

    override fun path(): Path {
        return path.path!!.toPath()
    }

    override fun write(source: ByteArray, offset: Int, byteCount: Int) {
        outStream.write(source, offset, byteCount)
    }

    override fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int) {
        write(source, sourceOffset, byteCount)
    }

    override fun flush() {
        outStream.flush()
    }

    override fun close() {
        outStream.close()
    }
}
