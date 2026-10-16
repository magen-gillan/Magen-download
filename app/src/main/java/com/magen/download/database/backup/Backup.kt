package com.magen.download.database.backup

import com.magen.download.database.objects.CommandTemplate
import com.magen.download.database.objects.DownloadedVideoInfo
import com.magen.download.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
