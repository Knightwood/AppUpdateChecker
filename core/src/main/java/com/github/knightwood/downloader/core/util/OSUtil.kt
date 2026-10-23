package com.github.knightwood.downloader.core.util

object OSUtil {
    val isAndroid = System.getProperty("os.name").lowercase() == "android"
}
