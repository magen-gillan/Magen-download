package com.magen.download.ui.page

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.magen.download.R
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.ToastUtil
import com.magen.download.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppUpdater() {

    val context = LocalContext.current

    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var currentDownloadStatus by remember {
        mutableStateOf(UpdateUtil.DownloadStatus.NotYet as UpdateUtil.DownloadStatus)
    }
    val scope = rememberCoroutineScope()
    // Must be remembered: a plain local var is re-initialized to null on every
    // recomposition (which happens on every progress tick), so the
    // onDismissRequest lambda would cancel a stale (null) job and the APK
    // download would keep running — and even prompt for install — after the
    // dialog was dismissed.
    var updateJob by remember { mutableStateOf<Job?>(null) }
    var release by remember { mutableStateOf(UpdateUtil.Release()) }
    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            UpdateUtil.installLatestApk()
        }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if (result) {
                UpdateUtil.installLatestApk()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls())
                        settings.launch(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    else UpdateUtil.installLatestApk()
                } else {
                    // Below API 26 unknown sources is a global setting and the
                    // install intent works directly — never dead-end silently
                    // after a finished download.
                    UpdateUtil.installLatestApk()
                }
            }
        }

    LaunchedEffect(Unit) {
        if (
            !PreferenceUtil.isNetworkAvailableForDownload() || !PreferenceUtil.isAutoUpdateEnabled()
        )
            return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                    UpdateUtil.checkForUpdate()?.let {
                        release = it
                        showUpdateDialog = true
                    }
                }
                .onFailure { it.printStackTrace() }
        }
    }

    if (showUpdateDialog) {
        UpdateDialogImpl(
            onDismissRequest = {
                showUpdateDialog = false
                updateJob?.cancel()
            },
            title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName ?: "",
            onConfirmUpdate = {
                updateJob =
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                                UpdateUtil.downloadApk(release = release).collect { downloadStatus ->
                                    currentDownloadStatus = downloadStatus
                                    if (downloadStatus is UpdateUtil.DownloadStatus.Finished) {
                                        // ActivityResult launchers must be
                                        // invoked from the main thread.
                                        withContext(Dispatchers.Main) {
                                            launcher.launch(
                                                Manifest.permission.REQUEST_INSTALL_PACKAGES
                                            )
                                        }
                                    }
                                }
                            }
                            .onFailure {
                                it.printStackTrace()
                                currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
                                ToastUtil.makeToastSuspend(
                                    context.getString(R.string.app_update_failed)
                                )
                                return@launch
                            }
                    }
            },
            releaseNote = release.body.toString(),
            downloadStatus = currentDownloadStatus,
        )
    }
}
