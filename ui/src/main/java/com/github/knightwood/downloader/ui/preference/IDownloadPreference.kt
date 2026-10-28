package com.github.knightwood.downloader.ui.preference

interface IDownloadPreference {
    val maxParallelDownloadCount: Int
    val minChunkSize: Long
    val maxChunkCount: Int
}

data class DownloadPreference(
    override val maxParallelDownloadCount: Int,
    override val minChunkSize: Long,
    override val maxChunkCount: Int
): IDownloadPreference
