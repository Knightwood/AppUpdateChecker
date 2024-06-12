package com.f_libs.appupdate.util

import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.MessageDigest

/**
 * ProjectName: AppUpdate PackageName: com.azhon.appupdate.util FileName:
 * FileUtil CreateDate: 2022/4/7 on 11:52 Desc:
 *
 * @author azhon
 */

class FileUtil {
    companion object {
        fun createDirDirectory(path: String) {
            File(path).let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
        }

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
                e.printStackTrace()
            }
            return ""
        }


        fun rename(file: File, newName: String): Boolean {
            // file doesn't exist then return false
            if (!file.exists()) return false
            // the new name is space then return false
            if (newName.isEmpty() || newName.isBlank()) return false
            // the new name equals old name then return true
            if (newName == file.name) return true
            val newFile = File(file.parent + File.separator + newName)
            // the new name of file exists then return false
            return (!newFile.exists()
                    && file.renameTo(newFile))
        }

        /**
         * 将文件移动到另一个目录
         */
        fun moveTo(file: File, anotherFolder: String): File? {
            //将file移动到anotherFolder目录下
            if (file.exists()) {
                val newFile = File(anotherFolder + File.separator + file.name)
                file.renameTo(newFile)
                return newFile
            }
            return null
        }
    }
}