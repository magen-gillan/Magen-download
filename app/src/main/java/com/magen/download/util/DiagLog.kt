package com.magen.download.util

import com.magen.download.App
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny on-device diagnostic log (timestamps of task state changes and app foreground/background
 * transitions). Written to Download/Magen download/diag.log, falling back to the app's own
 * external files dir. Capped at ~256 KB. Used to find out why a download stalls in the background.
 */
object DiagLog {
    private const val MAX_BYTES = 256 * 1024L
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(message: String) {
        val line = "${format.format(Date())} $message\n"
        runCatching { append(File(FileUtil.getExternalDownloadDirectory(), "diag.log"), line) }
            .onFailure {
                runCatching {
                    App.context.getExternalFilesDir(null)?.let { append(File(it, "diag.log"), line) }
                }
            }
    }

    private fun append(file: File, line: String) {
        if (file.length() > MAX_BYTES) file.writeText("")
        file.appendText(line)
    }
}
