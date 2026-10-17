package com.magen.download.database.backup

import android.content.Context
import com.magen.download.App
import com.magen.download.R
import com.magen.download.database.objects.CommandTemplate
import com.magen.download.database.objects.DownloadedVideoInfo
import com.magen.download.database.objects.OptionShortcut
import com.magen.download.util.DatabaseUtil
import java.util.Date
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupUtil {
    private val format = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportTemplatesToJson() =
        exportTemplatesToJson(
            templates = DatabaseUtil.getTemplateList(),
            shortcuts = DatabaseUtil.getShortcutList(),
        )

    fun exportTemplatesToJson(
        templates: List<CommandTemplate>,
        shortcuts: List<OptionShortcut>,
    ): String {
        return format.encodeToString(Backup(templates = templates, shortcuts = shortcuts))
    }

    fun List<DownloadedVideoInfo>.toJsonString(): String {
        return format.encodeToString(Backup(downloadHistory = this))
    }

    fun List<DownloadedVideoInfo>.toURLListString(): String {
        return this.map { it.videoUrl }.joinToString(separator = "\n") { it }
    }

    fun String.decodeToBackup(): Result<Backup> {
        return format.runCatching { decodeFromString<Backup>(this@decodeToBackup) }
    }

    fun getDownloadHistoryExportFilename(context: Context): String {
        // SimpleDateFormat with an explicit locale: Date().toString() embeds
        // colons (rejected by FAT/exFAT and many SAF providers) and
        // locale-dependent month names.
        val timestamp =
            java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US)
                .format(java.util.Date())
        return listOf(
                context.getString(R.string.app_name),
                App.packageInfo.versionName.toString(),
                timestamp,
            )
            .joinToString(separator = "-") { it }
    }

    enum class BackupDestination {
        File,
        Clipboard,
    }

    enum class BackupType {
        DownloadHistory,
        URLList,
        CommandTemplate,
        CommandShortcut,
    }
}
