package com.magen.download

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.magen.download.App
import com.magen.download.ui.common.LocalDarkTheme
import com.magen.download.ui.common.SettingsProvider
import com.magen.download.ui.page.AppEntry
import com.magen.download.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.magen.download.ui.theme.MagenTheme
import com.magen.download.util.APP_MODE_SELECTED
import com.magen.download.util.CLIPBOARD_SUGGESTION
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.PreferenceUtil.getBoolean
import com.magen.download.util.findURLsFromString
import com.magen.download.util.matchUrlFromSharedText
import com.magen.download.util.setLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : AppCompatActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        consumeShortcutIntent(intent)

        setContent {
            KoinContext {
                val windowSizeClass = calculateWindowSizeClass(this)
                SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                    MagenTheme(
                        darkTheme = LocalDarkTheme.current.isDarkTheme(),
                        isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    ) {
                        AppEntry(dialogViewModel = dialogViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeShortcutIntent(intent)
        val url = intent.getSharedURL()
        if (url != null) {
            dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(listOf(url)))
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForLinks()
    }

    /**
     * Reads the clipboard while the app is in the foreground. If the clipboard
     * content is new (i.e. the user copied something since the last check) and
     * contains one or more URLs, a download suggestion is proposed.
     *
     * - Deduplicated per clipboard content: the exact same clipboard entry is
     *   never suggested twice, but copying the same link again later WILL
     *   trigger a fresh suggestion.
     * - Fully disabled when the user turns the feature off in Settings, or
     *   during the first-run mode selection.
     */
    private fun checkClipboardForLinks() {
        val rawUrls = runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.primaryClip?.let { clip ->
                (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.text?.toString() }
            }?.joinToString(separator = "\n")
        }
            .onFailure { it.printStackTrace() }
            .getOrNull()
            ?: return

        val urls = findURLsFromString(rawUrls)
        val clipboardKey = urls.joinToString(separator = "\n")

        // Only react to clipboard content we have not seen before.
        if (clipboardKey == lastCheckedClipboard) return
        lastCheckedClipboard = clipboardKey

        if (urls.isEmpty()) return
        if (!CLIPBOARD_SUGGESTION.getBoolean()) return
        // Do not interrupt the first-run mode selection screen.
        if (!APP_MODE_SELECTED.getBoolean()) return

        dialogViewModel.proposeClipboardUrls(urls)
    }

    private fun consumeShortcutIntent(intent: Intent?) {
        if (intent == null) return
        val route =
            when (intent.action) {
                ACTION_OPEN_DOWNLOAD_SHEET -> PENDING_ROUTE_DOWNLOAD_SHEET
                ACTION_OPEN_HISTORY -> PENDING_ROUTE_HISTORY
                ACTION_OPEN_SETTINGS -> PENDING_ROUTE_SETTINGS
                else -> null
            }
        if (route != null) {
            intent.action = Intent.ACTION_MAIN
            mPendingRouteFlow.value = route
        }
    }

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent).also { matchedUrl ->
                        if (sharedUrlCached != matchedUrl) {
                            sharedUrlCached = matchedUrl
                        }
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private var sharedUrlCached = ""

        const val ACTION_OPEN_DOWNLOAD_SHEET = "com.magen.download.action.OPEN_DOWNLOAD_SHEET"
        const val ACTION_OPEN_HISTORY = "com.magen.download.action.OPEN_HISTORY"
        const val ACTION_OPEN_SETTINGS = "com.magen.download.action.OPEN_SETTINGS"

        const val PENDING_ROUTE_DOWNLOAD_SHEET = "download_sheet"
        const val PENDING_ROUTE_HISTORY = "download_history"
        const val PENDING_ROUTE_SETTINGS = "settings_page"

        private val mPendingRouteFlow = MutableStateFlow<String?>(null)
        val pendingRouteFlow = mPendingRouteFlow.asStateFlow()

        fun clearPendingRoute() {
            mPendingRouteFlow.value = null
        }

        /** Last clipboard content checked, to avoid suggesting the same entry twice. */
        private var lastCheckedClipboard: String = ""
    }
}
