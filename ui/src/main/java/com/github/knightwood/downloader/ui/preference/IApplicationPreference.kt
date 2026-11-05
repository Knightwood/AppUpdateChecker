package com.github.knightwood.downloader.ui.preference

interface IApplicationPreference {
    val hadRequestNotificationPermission: Boolean
}

data class ApplicationPreference(
    override val hadRequestNotificationPermission: Boolean = false
) : IApplicationPreference
