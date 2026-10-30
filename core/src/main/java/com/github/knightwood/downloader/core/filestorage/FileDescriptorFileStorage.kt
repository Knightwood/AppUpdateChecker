package com.github.knightwood.downloader.core.filestorage

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class FileDescriptorFileStorage(val fd: FileDescriptor) : RandomAccessFileStorage {
    val fos = FileOutputStream(fd)
    val channel: FileChannel = fos.channel

    override fun write(source: ByteArray, offset: Int, byteCount: Int) {
        channel.write(ByteBuffer.wrap(source, offset, byteCount))
    }

    override fun writeAt(pos: Long, source: ByteArray, sourceOffset: Int, byteCount: Int) {
        channel.write(ByteBuffer.wrap(source, sourceOffset, byteCount), pos)
    }

    override fun flush() {
        channel.force(false)
    }

    override fun close() {
        channel.close()
        fos.close()
        fd.sync()
    }

}
