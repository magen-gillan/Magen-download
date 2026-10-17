package com.magen.download.ui.page.settings.general

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.PrintDisabled
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.magen.download.App
import com.magen.download.R
import com.magen.download.ui.common.booleanState
import com.magen.download.ui.component.BackButton
import com.magen.download.ui.component.ConfirmButton
import com.magen.download.ui.component.DismissButton
import com.magen.download.ui.component.PreferenceInfo
import com.magen.download.ui.component.PreferenceItem
import com.magen.download.ui.component.PreferenceSubtitle
import com.magen.download.ui.component.PreferenceSwitch
import com.magen.download.ui.component.PreferenceSwitchWithDivider
import com.magen.download.ui.component.MagenDialog
import com.magen.download.ui.page.PermissionsGate
import com.magen.download.ui.page.download.NotificationPermissionDialog
import com.magen.download.util.CONFIGURE
import com.magen.download.util.CLIPBOARD_SUGGESTION
import com.magen.download.util.CUSTOM_COMMAND
import com.magen.download.util.DEBUG
import com.magen.download.util.DISABLE_PREVIEW
import com.magen.download.util.DOWNLOAD_ARCHIVE
import com.magen.download.util.FileUtil.getArchiveFile
import com.magen.download.util.NOTIFICATION
import com.magen.download.util.NotificationUtil
import com.magen.download.util.PLAYLIST
import com.magen.download.util.PRIVATE_MODE
import com.magen.download.util.SIMPLE_MODE
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.PreferenceUtil.getBoolean
import com.magen.download.util.PreferenceUtil.getString
import com.magen.download.util.PreferenceUtil.updateBoolean
import com.magen.download.util.SPONSORBLOCK
import com.magen.download.util.SUBTITLE
import com.magen.download.util.THUMBNAIL
import com.magen.download.util.ToastUtil
import com.magen.download.util.UpdateUtil
import com.magen.download.util.YT_DLP_VERSION
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneralDownloadPreferences(onNavigateBack: () -> Unit, navigateToTemplate: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var showSponsorBlockDialog by remember { mutableStateOf(false) }
    var showYtdlpDialog by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }

    val downloadSubtitle by SUBTITLE.booleanState

    var displayErrorReport by DEBUG.booleanState
    var downloadPlaylist by remember { mutableStateOf(PLAYLIST.getBoolean()) }
    var isSponsorBlockEnabled by remember { mutableStateOf(SPONSORBLOCK.getBoolean()) }
    var downloadNotification by remember { mutableStateOf(NOTIFICATION.getBoolean()) }

    var isPrivateModeEnabled by remember { mutableStateOf(PRIVATE_MODE.getBoolean()) }

    var isPreviewDisabled by remember { mutableStateOf(DISABLE_PREVIEW.getBoolean()) }
    var isNotificationPermissionGranted by remember {
        mutableStateOf(NotificationUtil.areNotificationsEnabled())
    }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= 33)
            rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS) { status ->
                if (!status) ToastUtil.makeToast(context.getString(R.string.permission_denied))
                else isNotificationPermissionGranted = true
            }
        else null

    var useDownloadArchive by DOWNLOAD_ARCHIVE.booleanState
    var showClearArchiveDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var archiveFileContent by remember { mutableStateOf("") }

    val storagePermission =
        rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val isPermissionGranted =
        Build.VERSION.SDK_INT > 29 || storagePermission.status == PermissionStatus.Granted

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.general_settings)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            val isCustomCommandEnabled by remember { mutableStateOf(CUSTOM_COMMAND.getBoolean()) }
            LazyColumn(modifier = Modifier, contentPadding = it) {
                item {
                    var simpleMode by remember { mutableStateOf(SIMPLE_MODE.getBoolean()) }
                    PreferenceSwitch(
                        title = stringResource(id = R.string.app_mode_simple),
                        description = stringResource(id = R.string.app_mode_setting_desc),
                        icon = Icons.Outlined.Tune,
                        isChecked = simpleMode,
                        onClick = {
                            simpleMode = !simpleMode
                            SIMPLE_MODE.updateBoolean(simpleMode)
                        },
                    )
                }
                item {
                    var clipboardSuggestion by
                        remember { mutableStateOf(CLIPBOARD_SUGGESTION.getBoolean()) }
                    PreferenceSwitch(
                        title = stringResource(id = R.string.clipboard_suggestion_setting),
                        description =
                            stringResource(id = R.string.clipboard_suggestion_setting_desc),
                        icon = Icons.Outlined.ContentPaste,
                        isChecked = clipboardSuggestion,
                        onClick = {
                            clipboardSuggestion = !clipboardSuggestion
                            CLIPBOARD_SUGGESTION.updateBoolean(clipboardSuggestion)
                        },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.permissions_manage),
                        description = stringResource(id = R.string.permissions_manage_desc),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                modifier =
                                    Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { showPermissions = true },
                    )
                }
                //                item {
                //                    SettingTitle(text = stringResource(id =
                // R.string.general_settings))
                //                }
                if (isCustomCommandEnabled)
                    item {
                        PreferenceInfo(
                            text = stringResource(id = R.string.custom_command_enabled_hint)
                        )
                    }
                item {
                    // version() spawns a yt-dlp subprocess and blocks; loading it
                    // inside remember{} froze the settings page on every open.
                    var ytdlpVersion by remember {
                        mutableStateOf(context.getString(R.string.ytdlp_update))
                    }
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            val version = runCatching {
                                YoutubeDL.getInstance().version(context.applicationContext)
                            }.getOrNull()
                            if (version != null) ytdlpVersion = version
                        }
                    }
                    PreferenceItem(
                        title = stringResource(id = R.string.ytdlp_update_action),
                        description = ytdlpVersion,
                        leadingIcon = {
                            if (isUpdating) UpdateProgressIndicator()
                            else {
                                Icon(
                                    imageVector = Icons.Outlined.Update,
                                    contentDescription = null,
                                    modifier =
                                        Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            scope.launch {
                                runCatching {
                                        isUpdating = true
                                        UpdateUtil.updateYtDlp()
                                        ytdlpVersion = YT_DLP_VERSION.getString()
                                    }
                                    .onFailure { th ->
                                        th.printStackTrace()
                                        ToastUtil.makeToastSuspend(
                                            App.context.getString(R.string.yt_dlp_update_fail)
                                        )
                                    }
                                    .onSuccess {
                                        ToastUtil.makeToastSuspend(
                                            context.getString(R.string.yt_dlp_up_to_date) +
                                                " (${YT_DLP_VERSION.getString()})"
                                        )
                                    }
                                isUpdating = false
                            }
                        },
                        onClickLabel = stringResource(id = R.string.update),
                        trailingIcon = {
                            IconButton(onClick = { showYtdlpDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(id = R.string.open_settings),
                                )
                            }
                        },
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.download_notification),
                        description =
                            stringResource(
                                id =
                                    if (isNotificationPermissionGranted)
                                        R.string.download_notification_desc
                                    else R.string.permission_denied
                            ),
                        icon =
                            if (!isNotificationPermissionGranted) Icons.Outlined.NotificationsOff
                            else if (!downloadNotification) Icons.Outlined.Notifications
                            else Icons.Outlined.NotificationsActive,
                        isChecked = downloadNotification && isNotificationPermissionGranted,
                        onClick = {
                            if (notificationPermission?.status is PermissionStatus.Denied) {
                                showNotificationDialog = true
                            } else if (isNotificationPermissionGranted) {
                                if (downloadNotification) NotificationUtil.cancelAllNotifications()
                                downloadNotification = !downloadNotification
                                PreferenceUtil.updateValue(NOTIFICATION, downloadNotification)
                            }
                        },
                    )
                }

                item {
                    var configureBeforeDownload by CONFIGURE.booleanState
                    PreferenceSwitch(
                        title = stringResource(id = R.string.settings_before_download),
                        description = stringResource(id = R.string.settings_before_download_desc),
                        icon =
                            if (configureBeforeDownload) Icons.Outlined.DoneAll
                            else Icons.Outlined.RemoveDone,
                        isChecked = configureBeforeDownload,
                        onClick = {
                            configureBeforeDownload = !configureBeforeDownload
                            PreferenceUtil.updateValue(CONFIGURE, configureBeforeDownload)
                        },
                    )
                }

                item {
                    var thumbnailSwitch by remember { mutableStateOf(THUMBNAIL.getBoolean()) }
                    PreferenceSwitch(
                        title = stringResource(id = R.string.create_thumbnail),
                        description = stringResource(id = R.string.create_thumbnail_summary),
                        enabled = !isCustomCommandEnabled,
                        icon = Icons.Outlined.Image,
                        isChecked = thumbnailSwitch,
                        onClick = {
                            thumbnailSwitch = !thumbnailSwitch
                            PreferenceUtil.updateValue(THUMBNAIL, thumbnailSwitch)
                        },
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.print_details),
                        description = stringResource(R.string.print_details_desc),
                        icon =
                            if (displayErrorReport) Icons.Outlined.Print
                            else Icons.Outlined.PrintDisabled,
                        enabled = !isCustomCommandEnabled,
                        onClick = {
                            displayErrorReport = !displayErrorReport
                            PreferenceUtil.updateValue(DEBUG, displayErrorReport)
                        },
                        isChecked = displayErrorReport,
                    )
                }

                item { PreferenceSubtitle(text = stringResource(id = R.string.privacy)) }

                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.private_mode),
                        description = stringResource(R.string.private_mode_desc),
                        icon =
                            if (isPrivateModeEnabled) Icons.Outlined.HistoryToggleOff
                            else Icons.Outlined.History,
                        isChecked = isPrivateModeEnabled,
                        enabled = !isCustomCommandEnabled,
                        onClick = {
                            isPrivateModeEnabled = !isPrivateModeEnabled
                            PreferenceUtil.updateValue(PRIVATE_MODE, isPrivateModeEnabled)
                        },
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.disable_preview),
                        description = stringResource(R.string.disable_preview_desc),
                        icon =
                            if (isPreviewDisabled) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                        isChecked = isPreviewDisabled,
                        enabled = !isCustomCommandEnabled,
                        onClick = {
                            isPreviewDisabled = !isPreviewDisabled
                            PreferenceUtil.updateValue(DISABLE_PREVIEW, isPreviewDisabled)
                        },
                    )
                }

                item { PreferenceSubtitle(text = stringResource(R.string.advanced_settings)) }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.download_playlist),
                        onClick = {
                            downloadPlaylist = !downloadPlaylist
                            PreferenceUtil.updateValue(PLAYLIST, downloadPlaylist)
                        },
                        icon = Icons.Outlined.PlaylistAddCheck,
                        enabled = !isCustomCommandEnabled,
                        description = stringResource(R.string.download_playlist_desc),
                        isChecked = downloadPlaylist,
                    )
                }

                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(id = R.string.download_archive),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                archiveFileContent = context.getArchiveFile().readText()
                                withContext(Dispatchers.Main) { showClearArchiveDialog = true }
                            }
                        },
                        icon = Icons.Outlined.Archive,
                        description = stringResource(R.string.download_archive_desc),
                        isChecked = useDownloadArchive,
                        onChecked = {
                            useDownloadArchive = !useDownloadArchive
                            DOWNLOAD_ARCHIVE.updateBoolean(useDownloadArchive)
                        },
                        enabled = isPermissionGranted,
                    )
                }

                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(R.string.sponsorblock),
                        description = stringResource(R.string.sponsorblock_desc),
                        icon = Icons.Outlined.MoneyOff,
                        enabled = !isCustomCommandEnabled,
                        isChecked = isSponsorBlockEnabled,
                        onChecked = {
                            isSponsorBlockEnabled = !isSponsorBlockEnabled
                            PreferenceUtil.updateValue(SPONSORBLOCK, isSponsorBlockEnabled)
                        },
                        onClick = { showSponsorBlockDialog = true },
                    )
                }

                if (downloadSubtitle)
                    item {
                        PreferenceInfo(text = stringResource(id = R.string.subtitle_sponsorblock))
                    }
            }
        },
    )
    if (showSponsorBlockDialog) {
        SponsorBlockDialog { showSponsorBlockDialog = false }
    }
    if (showPermissions) {
        // The gate is a full-screen overlay; without a BackHandler the system
        // back press popped the whole settings route underneath it.
        BackHandler(enabled = showPermissions) { showPermissions = false }
        PermissionsGate(inSettings = true, onDone = { showPermissions = false })
    }
    if (showYtdlpDialog) {
        YtdlpUpdateChannelDialog(onDismissRequest = { showYtdlpDialog = false })
    }
    if (showClearArchiveDialog) {
        DownloadArchiveDialog(
            archiveFileContent = archiveFileContent,
            onDismissRequest = { showClearArchiveDialog = false },
        ) { content ->
            scope.launch(Dispatchers.IO) {
                runCatching { context.getArchiveFile().writeText(content) }
            }
        }
    }

    if (showNotificationDialog) {
        NotificationPermissionDialog(
            onDismissRequest = { showNotificationDialog = false },
            onPermissionGranted = {
                notificationPermission?.launchPermissionRequest()
                NOTIFICATION.updateBoolean(true)
                downloadNotification = true
                showNotificationDialog = false
            },
        )
    }
}

@Composable
private fun DialogSingleChoiceItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    label: String,
    labelContainerColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = true, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(
            modifier = Modifier.clearAndSetSemantics {},
            selected = selected,
            onClick = onClick,
        )

        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Surface(modifier.padding(end = 12.dp), shape = CircleShape, color = labelContainerColor) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = label,
                color = MaterialTheme.colorScheme.contentColorFor(labelContainerColor),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun DialogCheckBoxItem(
    modifier: Modifier = Modifier,
    text: String,
    checked: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = true, onValueChange = onValueChange)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            modifier = Modifier.clearAndSetSemantics {},
            checked = checked,
            onCheckedChange = onValueChange,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UpdateProgressIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp).padding(2.dp)
    )
}

@Composable
fun DownloadArchiveDialog(
    archiveFileContent: String,
    onDismissRequest: () -> Unit,
    onSaveChangesCallback: (String) -> Unit,
) {
    var editContent by remember { mutableStateOf(archiveFileContent) }

    MagenDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton(text = stringResource(id = R.string.save)) {
                onSaveChangesCallback(editContent)
                onDismissRequest()
            }
        },
        dismissButton = { DismissButton { onDismissRequest() } },
        icon = { Icon(imageVector = Icons.Outlined.Edit, contentDescription = null) },
        title = { Text(text = stringResource(id = R.string.edit_file)) },
        text = {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                val textStyle =
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

                OutlinedTextField(
                    label = { Text(text = "archive.txt") },
                    value = editContent,
                    onValueChange = { str -> editContent = str },
                    textStyle = textStyle,
                    minLines = 10,
                    maxLines = 10,
                )
            }
        },
    )
}

@Composable
@Preview
fun DownloadArchiveDialogPreview() {
    val strs = buildList { repeat(20) { add("youtube IPf4AxotvNU") } }
    val str = strs.fold(initial = "") { acc, text -> acc + text + "\n" }
    DownloadArchiveDialog(archiveFileContent = str, onDismissRequest = {}) {}
}
