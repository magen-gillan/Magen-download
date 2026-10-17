package com.magen.download.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magen.download.Downloader
import com.magen.download.download.DownloaderV2
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.PreferenceUtil.getBoolean
import com.magen.download.util.PreferenceUtil.getLong
import com.magen.download.util.PreferenceUtil.getString
import com.magen.download.util.UpdateUtil
import com.magen.download.util.YT_DLP_AUTO_UPDATE
import com.magen.download.util.YT_DLP_UPDATE_INTERVAL
import com.magen.download.util.YT_DLP_UPDATE_TIME
import com.magen.download.util.YT_DLP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun YtdlpUpdater() {

    val downloaderState by Downloader.downloaderState.collectAsStateWithLifecycle()
    val downloaderV2 = koinInject<DownloaderV2>()
    // Reading the snapshot-backed map in composition subscribes us to task
    // state changes, so this recomposes (and re-runs the effect) once active
    // V2 downloads finish.
    val v2Busy = downloaderV2.countBusyTasks() > 0

    // Keyed on the download states: previously a session that started with a
    // running download never re-checked the update interval, and updating
    // yt-dlp WHILE a download is running must never happen — the updater
    // deletes the yt-dlp directory and re-extracts it, breaking any yt-dlp
    // process spawned in that window.
    LaunchedEffect(downloaderState, v2Busy) {
        if (downloaderState !is Downloader.State.Idle) return@LaunchedEffect
        if (v2Busy) return@LaunchedEffect

        if (!YT_DLP_AUTO_UPDATE.getBoolean() && YT_DLP_VERSION.getString().isNotEmpty())
            return@LaunchedEffect

        if (!PreferenceUtil.isNetworkAvailableForDownload()) {
            return@LaunchedEffect
        }

        val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
        val currentTime = System.currentTimeMillis()

        if (currentTime < lastUpdateTime + YT_DLP_UPDATE_INTERVAL.getLong()) {
            return@LaunchedEffect
        }

        runCatching {
                Downloader.updateState(state = Downloader.State.Updating)
                withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() }
            }
            .onFailure { it.printStackTrace() }
        Downloader.updateState(state = Downloader.State.Idle)
    }
}
