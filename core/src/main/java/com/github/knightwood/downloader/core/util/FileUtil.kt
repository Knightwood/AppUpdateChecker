package com.github.knightwood.downloader.core.util

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.MessageDigest

object FileUtil {
    private val logger = LoggerFactory.getLogger("FileUtil")

    fun systemDownloadDirPath() = System.getProperty("user.home")!!.toPath().resolve("Downloads")

    fun md5(file: File): String {
        try {
            val buffer = ByteArray(1024)
            var len: Int
            val digest = MessageDigest.getInstance("MD5")
            val inStream = FileInputStream(file)
            while (inStream.read(buffer).also { len = it } != -1) {
                digest.update(buffer, 0, len)
            }
            inStream.close()
            val bigInt = BigInteger(1, digest.digest())
            return bigInt.toString(16).padStart(32, '0').uppercase()
        } catch (e: Exception) {
            logger.error("calc md5 failed", e)
        }
        return ""
    }


    /**
     * 从下载文件保存路径的父路径查找与下载文件具有相同md5的文件
     */
    fun findCache(from: Path, target: String): Path? {
        return runCatching {
            from.parent?.let { parent ->
                FileSystem.SYSTEM.list(parent).first { path ->
                    val f = path.toFile()
                    if (f.exists() && f.isFile) {
                        checkFileMd5(f, target)
                    } else {
                        false
                    }
                }
            }
        }.onFailure { e ->
            logger.error("findCache failed", e)
        }.getOrNull()
    }

    /**
     * Check whether the Apk has been downloaded, don't download again
     */
    fun checkFileMd5(file: File, target: String): Boolean {
        if (target.isBlank()) {
            return false
        }
        if (file.exists()) {
            return md5(file).equals(target, ignoreCase = true)
        }
        return false
    }
}


/**
 * 根据字节数转换成易阅读的单位格式
 */
val Long?.toDataUnit: String?
    get() {
        if (this == null) {
            return null
        }
        if (this < 1024) {
            return "${this}B"
        }
        if (this < 1024 * 1024) {
            return "${this / 1024}KB"
        }
        return "${this / 1024 / 1024}MB"
    }

val Double?.toDataUnit: String?
    get() {
        if (this == null) {
            return null
        }
        if (this < 1024) {
            return "${this}B"
        }
        if (this < 1024 * 1024) {
            return "${this / 1024}KB"
        }
        return "${this / 1024 / 1024}MB"
    }
