package com.magen.download.util

import com.magen.download.App
import com.magen.download.util.FileUtil.getCookiesFile
import com.magen.download.util.PreferenceUtil.updateBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Automatic one-time setup performed when the user chooses Simple mode for the first time:
 * 1. Enables cookies usage so requests to YouTube are less likely to be blocked.
 *    The cookies file is (re)generated from the app's WebView cookie database on every
 *    app start by [App]; if none exist yet, a valid empty Netscape cookie file is
 *    created so yt-dlp never fails on a missing file.
 * 2. Enables automatic yt-dlp library updates and triggers an immediate update.
 */
object SimpleModeSetup {

    fun setup() {
        // 1) Cookies: enabled + make sure a valid cookies file exists
        COOKIES.updateBoolean(true)
        App.applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val content = DownloadUtil.getCookiesContentFromDatabase().getOrNull()
                val file = App.context.getCookiesFile()
                file.parentFile?.mkdirs()
                if (content.isNullOrEmpty()) {
                    if (!file.exists()) file.writeText(PreferenceUtil.COOKIE_HEADER)
                } else {
                    FileUtil.writeContentToFile(content, file)
                }
            }.onFailure { it.printStackTrace() }
        }

        // 2) Library: auto-update enabled + immediate refresh
        YT_DLP_AUTO_UPDATE.updateBoolean(true)
        App.applicationScope.launch(Dispatchers.IO) {
            runCatching { withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() } }
                .onFailure { it.printStackTrace() }
        }
    }
}
