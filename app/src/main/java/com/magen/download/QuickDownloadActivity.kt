package com.magen.download

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magen.download.ui.common.LocalDarkTheme
import com.magen.download.ui.common.SettingsProvider
import com.magen.download.ui.page.downloadv2.configure.Config
import com.magen.download.ui.page.downloadv2.configure.DownloadDialog
import com.magen.download.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.magen.download.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.magen.download.ui.page.downloadv2.configure.DownloadDialogViewModel.SelectionState
import com.magen.download.ui.page.downloadv2.configure.FormatPage
import com.magen.download.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.magen.download.ui.theme.MagenTheme
import com.magen.download.util.DownloadUtil
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.matchUrlFromSharedText
import com.magen.download.util.setLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.magen.download.download.DownloaderV2
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.getViewModel

private const val TAG = "QuickDownloadActivity"

class QuickDownloadActivity : ComponentActivity() {
    private var sharedUrlCached: String = ""
    private val downloaderV2: DownloaderV2 by inject()

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                // Normalize like MainActivity does: a browser "open with" URL
                // can carry tracking/query garbage around the media link.
                intent.dataString?.let { matchUrlFromSharedText(it) }
            }

            Intent.ACTION_SEND -> {
                // Do NOT removeExtra: this intent is re-delivered verbatim when
                // the activity is recreated (rotation, dark-mode change,
                // split-screen resize). Removing it used to make the recreated
                // instance see no URL and finish() mid-configuration, killing
                // the share flow. sharedUrlCached survives recreation anyway.
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    matchUrlFromSharedText(sharedContent)
                }
            }

            else -> {
                null
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getSharedURL()?.let { sharedUrlCached = it }

        if (sharedUrlCached.isEmpty()) {
            finish()
            return
        }

        App.startService()

        enableEdgeToEdge()

        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }

        val viewModel: DownloadDialogViewModel = getViewModel()
        viewModel.postAction(Action.ShowSheet(listOf(sharedUrlCached)))

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                MagenTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    var preferences by remember {
                        mutableStateOf(DownloadUtil.DownloadPreferences.createFromPreferences())
                    }

                    val sheetValue = viewModel.sheetValueFlow.collectAsStateWithLifecycle().value

                    val state = viewModel.sheetStateFlow.collectAsStateWithLifecycle().value

                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                    val selectionState =
                        viewModel.selectionStateFlow.collectAsStateWithLifecycle().value

                    var showDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(sheetValue, selectionState) {
                        if (sheetValue == DownloadDialogViewModel.SheetValue.Expanded) {
                            showDialog = true
                        } else if (sheetValue == DownloadDialogViewModel.SheetValue.Hidden) {
                            launch { sheetState.hide() }
                                .invokeOnCompletion {
                                    showDialog = false
                                    if (selectionState == SelectionState.Idle) {
                                        this@QuickDownloadActivity.finish()
                                    }
                                }
                        }
                    }

                    if (showDialog) {
                        DownloadDialog(
                            state = state,
                            sheetState = sheetState,
                            config = Config(),
                            preferences = preferences,
                            onPreferencesUpdate = { preferences = it },
                            onActionPost = { viewModel.postAction(it) },
                        )
                    }

                    when (selectionState) {
                        is SelectionState.FormatSelection ->
                            FormatPage(
                                state = selectionState,
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this.finish()
                                },
                            )

                        SelectionState.Idle -> {}
                        is SelectionState.PlaylistSelection -> {
                            PlaylistSelectionPage(
                                state = selectionState,
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this.finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleInstance re-delivers a second share here; ignoring it used to
        // silently drop the new link while the stale sheet kept showing the
        // old URL.
        intent.getSharedURL()?.let { sharedUrlCached = it }
        if (sharedUrlCached.isNotEmpty()) {
            App.startService()
            getViewModel<DownloadDialogViewModel>().postAction(
                Action.ShowSheet(listOf(sharedUrlCached))
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isFinishing) return
        // The service is started early (while this activity is visible, which is
        // what Android requires for a foreground-service start). If the user closed
        // the sheet without downloading anything, stop it again instead of leaving a
        // "downloading..." notification behind. The short delay lets a task that was
        // just enqueued move to its running state first.
        val downloader = downloaderV2
        App.applicationScope.launch {
            delay(IDLE_CHECK_DELAY_MS)
            val busy =
                downloader.countBusyTasks() > 0 ||
                    Downloader.processCount.value > 0 ||
                    Downloader.downloaderState.value !is Downloader.State.Idle
            if (!busy) App.stopService()
        }
    }
}

private const val IDLE_CHECK_DELAY_MS = 2_500L
