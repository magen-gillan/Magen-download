package com.magen.download.util

import android.content.Context
import android.util.Patterns
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.text.isDigitsOnly
import com.magen.download.App
import com.magen.download.App.Companion.applicationScope
import com.magen.download.App.Companion.context
import com.magen.download.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Deprecated("Use extension functions of Context to show a toast")
object ToastUtil {
    fun makeToast(text: String) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    fun makeToastSuspend(text: String) {
        applicationScope.launch(Dispatchers.Main) { makeToast(text) }
    }

    fun makeToast(stringId: Int) {
        Toast.makeText(context.applicationContext, context.getString(stringId), Toast.LENGTH_SHORT)
            .show()
    }
}

@MainThread
fun Context.makeToast(stringId: Int) {
    Toast.makeText(applicationContext, getString(stringId), Toast.LENGTH_SHORT).show()
}

@MainThread
fun Context.makeToast(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
}

private const val GIGA_BYTES = 1024f * 1024f * 1024f
private const val MEGA_BYTES = 1024f * 1024f

@Composable
fun Number?.toFileSizeText(): String {
    if (this == null) return stringResource(id = R.string.unknown)

    return this.toFloat().run {
        if (this > GIGA_BYTES) stringResource(R.string.filesize_gb).format(this / GIGA_BYTES)
        else stringResource(R.string.filesize_mb).format(this / MEGA_BYTES)
    }
}

/** Convert time in **seconds** to `hh:mm:ss` or `mm:ss` */
fun Int.toDurationText(): String =
    this.run {
        if (this >= 3600) "%d:%02d:%02d".format(this / 3600, (this % 3600) / 60, this % 60)
        else "%02d:%02d".format(this / 60, this % 60)
    }

fun String.isNumberInRange(start: Int, end: Int): Boolean {
    return this.isNotEmpty() &&
        this.isDigitsOnly() &&
        this.length < 10 &&
        this.toInt() >= start &&
        this.toInt() <= end
}

/**
 * Stable, non-negative notification id derived from a task key.
 *
 * The raw hashCode() can be negative (notification ids accept negatives, but
 * tools that treat them as unsigned get confused); deriving the id explicitly
 * and deterministically from the task key keeps "one id per task" true across
 * restarts while guaranteeing a sane value.
 */
fun String.toStableNotificationId(): Int =
    hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) }

private const val URL_TRAILING_PUNCTUATION =
    ".,;:!?)]}>\"'\u2019\u00bb\u203a\u3001\uff0c\u3002\uff01\uff1f\uff1a\uff1b\u060c"

fun findURLsFromString(input: String, firstMatchOnly: Boolean = false): List<String> {
    val result = mutableListOf<String>()

    with(Patterns.WEB_URL.matcher(input)) {
        while (find()) {
            val candidate = group()?.trim() ?: continue
            // Links shared from browsers/chat apps arrive glued to sentence
            // punctuation ("check https://youtu.be/x."); trim it so yt-dlp
            // receives a clean URL.
            val url = candidate.trimEnd(*URL_TRAILING_PUNCTUATION.toCharArray())
            // WEB_URL also matches bare domains ("example.com"); only accept
            // fully-scheme'd http(s) links that yt-dlp can actually fetch.
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            result += url
            if (firstMatchOnly) break
        }
    }
    return result
}

fun String.isNumberInRange(range: IntRange): Boolean = this.isNumberInRange(range.first, range.last)

fun ClosedFloatingPointRange<Float>.toIntRange() =
    IntRange(start.roundToInt(), endInclusive.roundToInt())

fun String?.toHttpsUrl(): String =
    this?.run { if (matches(Regex("^(http:).*"))) replaceFirst("http", "https") else this } ?: ""

fun matchUrlFromClipboard(string: String, isMatchingMultiLink: Boolean = false): String {
    findURLsFromString(string, !isMatchingMultiLink).joinToString(separator = "\n").run {
        if (isEmpty()) ToastUtil.makeToast(R.string.paste_fail_msg)
        else ToastUtil.makeToast(R.string.paste_msg)
        return this
    }
}

fun matchUrlFromSharedText(s: String): String {
    findURLsFromString(s, true).joinToString(separator = "\n").run {
        if (isEmpty()) ToastUtil.makeToast(R.string.share_fail_msg)
        //            else makeToast(R.string.share_success_msg)
        return this
    }
}

fun Number?.toBitrateText(): String {
    val br = this?.toFloat() ?: return ""
    return when {
        br <= 0f -> "" // i don't care
        br < 1024f -> "%.1f Kbps".format(br)

        else -> "%.2f Mbps".format(br / 1024f)
    }
}

fun getErrorReport(th: Throwable, url: String): String =
    App.getVersionReport() + "\nURL: ${url}\n${th.message}"

@Deprecated(
    "Use findURLsFromString instead",
    ReplaceWith("findURLsFromString(s, !isMatchingMultiLink).joinToString(separator = \"\\n\")"),
)
fun matchUrlFromString(s: String, isMatchingMultiLink: Boolean = false): String =
    findURLsFromString(s, !isMatchingMultiLink).joinToString(separator = "\n")

fun connectWithDelimiter(vararg strings: String?, delimiter: String): String =
    strings
        .toList()
        .filter { !it.isNullOrBlank() }
        .joinToString(separator = delimiter) { it.toString() }

fun connectWithBlank(s1: String, s2: String): String {
    val blank = if (s1.isEmpty() || s2.isEmpty()) "" else " "
    return s1 + blank + s2
}
