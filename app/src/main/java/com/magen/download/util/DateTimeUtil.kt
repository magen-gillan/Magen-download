package com.magen.download.util

import android.os.Build
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

// A shared SimpleDateFormat instance is NOT thread-safe; create a fresh
// formatter per call on the legacy (API < 26) fallback path.
private const val LEGACY_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"

fun Long.toLocalizedString(locale: Locale = Locale.getDefault()): String {
    return if (Build.VERSION.SDK_INT >= 26) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date.from(Instant.ofEpochMilli(this)))
    } else {
        SimpleDateFormat(LEGACY_DATE_PATTERN, Locale.getDefault()).format(Date(this))
    }
}
