package com.github.knightwood.downloader.ui.preference

import com.github.knightwood.downloader.core.util.HttpConstant

interface IDownloadPreference {
    val httpTimeOut: Int
    val maxParallelDownloadCount: Int
    val maxChunkCount: Int
    val minChunkSize: Long
    val maxRedirects: Int
}

data class DownloadPreference(
    override val httpTimeOut: Int = HttpConstant.HTTP_TIME_OUT,
    override val maxParallelDownloadCount: Int = HttpConstant.MAX_PARALLEL_DOWNLOAD_COUNT,
    override val maxChunkCount: Int = HttpConstant.MAX_CHUNK_COUNT,
    override val minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE,
    override val maxRedirects: Int = HttpConstant.MAX_REDIRECTS,
) : IDownloadPreference
