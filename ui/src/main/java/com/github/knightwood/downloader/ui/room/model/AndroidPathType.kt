package com.github.knightwood.downloader.ui.room.model

/**
 * 下载文件存储位置的类型
 *
 * 对应 core-android 中的 `AndroidPath` 密封接口的三个实现，
 * 数据表中以“类型 + 值字符串”两列保存：
 * - [JVM]：值为文件路径字符串
 * - [MEDIA_STORE] / [DOCUMENT_FILE]：值为 `content://` uri 字符串
 */
enum class AndroidPathType {
    /** 未指定存储位置，使用 Downloader 提供的默认存储（应用外置缓存目录） */
    NONE,

    /** AndroidPath.Jvm，使用 java.io.File / okio 读写文件 */
    JVM,

    /** AndroidPath.MediaStore，使用 MediaStore 返回的 uri 读写文件 */
    MEDIA_STORE,

    /** AndroidPath.DocumentFile，使用 SAF 返回的 uri 读写文件 */
    DOCUMENT_FILE,
}
