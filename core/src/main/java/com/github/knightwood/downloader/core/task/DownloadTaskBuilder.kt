package com.github.knightwood.downloader.core.task

import com.github.knightwood.downloader.core.filestorage.FileStorageFactory
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.DownloadTaskConf
import com.github.knightwood.downloader.core.model.DownloadTaskSaveState
import com.github.knightwood.downloader.core.util.HttpConnectionUtils
import okio.Path
import org.slf4j.LoggerFactory

/**
 * 探测下载链接, 自动决定文件名称, 是否使用分块下载 并返回DownloadTask实例
 */
class DownloadTaskBuilder(
    private val id: Int,
    private var conf: DownloadTaskConf,
    private var downloadProgress: DownloadStatus.DOWNLOADING? = null,
) {
    private val logger = LoggerFactory.getLogger("DownloadTaskBuilder")
    private var dirPath: Path? = null

    constructor(saveState: DownloadTaskSaveState) : this(saveState.id, saveState.conf, saveState.progress)

    constructor(url: String, id: Int = url.hashCode()) : this(id, DownloadTaskConf(url))

    /**
     * 探测链接以自动填充文件名, 是否支持分块下载
     * @param multiChunkDownload 传入参数为true且url支持分块下载时启用分块下载功能,否则即使url支持分块下载也不使用分块下载功能
     */
    fun probe(multiChunkDownload: Boolean = false): DownloadTaskBuilder {
        HttpConnectionUtils.probe(url = conf.url)?.also {
            conf = conf.copy(isSupportChunkDownload = it.acceptRanges && multiChunkDownload)
            name(it.fileName)
        } ?: let {
            conf = conf.copy(isSupportChunkDownload = false)
            chunk(0)
        }
        return this
    }

    fun chunk(
        chunk: Int = HttpConstant.DEFAULT_CHUNK_COUNT,
        minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE,
    ): DownloadTaskBuilder {
        conf = conf.copy(
            chunkCount = chunk,
            minChunkSize = minChunkSize,
        )
        return this
    }

    fun saveDir(dirPath: Path): DownloadTaskBuilder {
        this.dirPath = dirPath
        return this
    }

    fun name(name: String): DownloadTaskBuilder {
        conf = conf.copy(name = name)
        return this
    }

    fun restoreProgress(state: DownloadStatus.DOWNLOADING): DownloadTaskBuilder {
        this.downloadProgress = state
        return this
    }

    fun build(): DownloadTask {
        return DownloadTask(
            id = id, conf = conf,
            fileStorageProvider = {
                FileStorageFactory.create(
                    parallel = conf.isSupportChunkDownload,
                    fileProvider = { dirPath!!.resolve(conf.name).toFile() })
            }
        ).also {
            runCatching {
                it.restoreProgress(downloadProgress)
            }.onFailure {
                logger.error("restoreProgress failed", it)
            }
        }
    }
}
