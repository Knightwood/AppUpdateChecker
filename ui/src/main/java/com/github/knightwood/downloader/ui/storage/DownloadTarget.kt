package com.github.knightwood.downloader.ui.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.android.util.Constant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * 下载文件的保存位置
 *
 * core-android 使用 [AndroidPath] 表示文件位置，界面层只关心用户选择“存到哪里”，
 * 因此抽象出此类型，由 [DownloadTargetResolver] 转换为具体的 [AndroidPath]。
 */
sealed interface DownloadTarget {

    /**
     * 应用在外置存储中的专属缓存目录（externalCacheDir/Download）
     *
     * 无需任何权限，但缓存可能被系统清理、卸载应用时一并删除。
     */
    data object AppCache : DownloadTarget

    /**
     * 系统“下载”目录（即文件管理器中可见的 Download 文件夹）
     *
     * - Android 10 及以上通过 MediaStore 写入，无需权限
     * - Android 10 以下直接以文件路径写入，需要存储权限
     */
    data object PublicDownloads : DownloadTarget

    /**
     * 用户通过 SAF 框架选择的任意目录
     *
     * 需要先取得该目录的持久化读写权限，下载前在目录中创建文件并拿到 uri 再写入。
     */
    data class SafDirectory(val treeUri: Uri) : DownloadTarget

    companion object {

        /**
         * 是否支持分块（多线程）下载
         * 之前使用uri的FileStorage实现有误，不支持随机写入。现在修好了，方法直接返回true。
         */
        fun supportsParallel(target: DownloadTarget): Boolean {
            return true
        }
    }
}

/**
 * 将界面选择的位置转换为 core-android 的 [AndroidPath]
 *
 * 需要创建文件（MediaStore / SAF）的动作会一并在此完成，
 * 失败时抛出带提示信息的异常，由界面层展示。
 */
object DownloadTargetResolver {

    suspend fun resolve(
        context: Context,
        target: DownloadTarget,
        fileName: String,
        mimeType: String,
    ): AndroidPath = withContext(Dispatchers.IO) {
        when (target) {
            DownloadTarget.AppCache -> createInCache(context, fileName)
            DownloadTarget.PublicDownloads -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    insertIntoMediaStore(context, fileName, mimeType)
                } else {
                    createInPublicDownloads(fileName)
                }
            }

            is DownloadTarget.SafDirectory -> createInDocumentTree(context, target.treeUri, fileName, mimeType)
        }
    }

    private fun createInCache(context: Context, fileName: String): AndroidPath {
        // externalCacheDir 在少数设备上可能为空，退回到内部缓存目录
        val base = context.externalCacheDir ?: context.cacheDir
        val dir = File(base, Constant.CACHE_DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) {
            error("无法创建缓存目录: ${dir.absolutePath}")
        }
        return AndroidPath.Jvm(dir.toOkioPath().resolve(fileName))
    }

    private fun createInPublicDownloads(fileName: String): AndroidPath {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) {
            error("无法访问系统下载目录，请检查存储权限")
        }
        return AndroidPath.Jvm(dir.toOkioPath().resolve(fileName))
    }

    /**
     * 在系统下载目录插入一条记录并取得可写入的 uri
     *
     * 不设置 IS_PENDING，保证应用被杀或任务失败时文件不会被“挂起”而不可见，
     * 代价是下载过程中文件管理器中可以看到尚未下完的文件。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertIntoMediaStore(context: Context, fileName: String, mimeType: String): AndroidPath {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: error("无法在系统下载目录创建文件: $fileName")
        return AndroidPath.MediaStore(uri)
    }

    /**
     * 在用户选择的 SAF 目录中创建文件
     */
    private fun createInDocumentTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        mimeType: String,
    ): AndroidPath {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            mimeType,
            fileName,
        ) ?: error("无法在所选文件夹中创建文件: $fileName")
        return AndroidPath.DocumentFile(documentUri)
    }
}
