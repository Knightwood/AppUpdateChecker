package com.github.knightwood.downloader.core.model

data class UrlResponseMeta(
    val length: Long,
    val acceptRanges: Boolean = false,
    val fileName: String,
)
