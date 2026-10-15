package com.github.knightwood.downloader.core.model

import com.github.knightwood.downloader.core.executor.IDownloadExecutor

class TaskDesc() {
    var fileEntity: FileEntity = FileEntity()
    fun file(action: FileEntity.() -> Unit): TaskDesc {
        fileEntity = FileEntity().apply(action)
        return this
    }

    var taskImI: TaskImI = TaskImI()
    fun task(action: TaskImI.() -> Unit): TaskDesc {
        taskImI = TaskImI().apply(action)
        return this
    }

    var logConfig: LogConfig = LogConfig()
    fun log(action: LogConfig.() -> Unit): TaskDesc {
        logConfig = LogConfig().apply(action)
        return this
    }
}

/**
 * 文件信息
 */
class FileEntity(
    var name: String? = null,
    var url: String? = null,
    var description: String? = null,
    var version: String? = null,
    var versionCode: Int? = null,
    var date: String? = null,
    var md5: String? = null,
)

class LogConfig(
    var enable: Boolean = false,
    var prefix: String? = null,
)

class TaskImI {
    /**
     * 下载之后是否自动使用[com.github.knightwood.downloader.core.Downloader.postHandle]处理
     */
    var autoHandleAfterDownloaded = false

    /**
     * 相对于externalCacheDir路径, 用于存储apk文件的路径
     * 默认值[Constant.cacheDirName]
     */
    var saveRelativePath: String? = null

    /**
     * 提供自定义下载执行器
     */
    var downloadExecutor: IDownloadExecutor? = null
}
