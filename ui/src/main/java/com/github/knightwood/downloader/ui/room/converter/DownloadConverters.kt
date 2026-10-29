package com.github.knightwood.downloader.ui.room.converter

import androidx.room.TypeConverter
import com.github.knightwood.downloader.ui.room.model.AndroidPathType
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType

/**
 * Room 类型转换器
 *
 * 枚举统一以名称（TEXT）形式存储，避免依赖枚举声明顺序；
 * 读取时若遇到无法识别的值（例如后续版本删除/重命名了枚举项），
 * 回退到 [DownloadStatusType.IDLE] / [AndroidPathType.NONE]，避免直接崩溃。
 */
class DownloadConverters {

    @TypeConverter
    fun fromStatusType(value: DownloadStatusType): String = value.name

    @TypeConverter
    fun toStatusType(value: String): DownloadStatusType =
        runCatching { DownloadStatusType.valueOf(value) }
            .getOrDefault(DownloadStatusType.IDLE)

    @TypeConverter
    fun fromPathType(value: AndroidPathType): String = value.name

    @TypeConverter
    fun toPathType(value: String): AndroidPathType =
        runCatching { AndroidPathType.valueOf(value) }
            .getOrDefault(AndroidPathType.NONE)
}
