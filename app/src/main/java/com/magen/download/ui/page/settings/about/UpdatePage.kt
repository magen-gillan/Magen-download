package com.magen.download.ui.page.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magen.download.App
import com.magen.download.R
import com.magen.download.ui.common.intState
import com.magen.download.ui.component.BackButton
import com.magen.download.ui.component.PreferenceInfo
import com.magen.download.ui.component.PreferenceSingleChoiceItem
import com.magen.download.ui.component.PreferenceSubtitle
import com.magen.download.ui.component.PreferenceSwitchWithContainer
import com.magen.download.ui.page.UpdateDialog
import com.magen.download.util.AUTO_UPDATE
import com.magen.download.util.PRE_RELEASE
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.PreferenceUtil.updateBoolean
import com.magen.download.util.PreferenceUtil.updateInt
import com.magen.download.util.STABLE
import com.magen.download.util.ToastUtil
import com.magen.download.util.UPDATE_CHANNEL
import com.magen.download.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePage(onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    var autoUpdate by remember { mutableStateOf(PreferenceUtil.isAutoUpdateEnabled()) }
    var updateChannel by UPDATE_CHANNEL.intState
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var release by remember { mutableStateOf(UpdateUtil.Release()) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showUnavailableDialog by remember { mutableStateOf(App.isFDroidBuild()) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(modifier = Modifier, text = stringResource(id = R.string.auto_update))
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { paddings ->
            LazyColumn(modifier = Modifier.padding(paddings)) {
                item {
                    PreferenceSwitchWithContainer(
                        title = stringResource(id = R.string.enable_auto_update),
                        icon = null,
                        isChecked = autoUpdate,
                    ) {
                        autoUpdate = !autoUpdate
                        AUTO_UPDATE.updateBoolean(autoUpdate)
                    }
                }
                item {
                    PreferenceSubtitle(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.update_channel),
                    )
                }
                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(id = R.string.stable_channel),
                        selected = updateChannel == STABLE,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        updateChannel = STABLE
                        UPDATE_CHANNEL.updateInt(updateChannel)
                    }
                }

                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(id = R.string.pre_release_channel),
                        selected = updateChannel == PRE_RELEASE,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        updateChannel = PRE_RELEASE
                        UPDATE_CHANNEL.updateInt(updateChannel)
                    }
                }
                item {
                    var isLoading by remember { mutableStateOf(false) }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ProgressIndicatorButton(
                            modifier =
                                Modifier.padding(horizontal = 24.dp)
                                    .padding(top = 6.dp)
                                    .padding(bottom = 12.dp),
                            text = stringResource(id = R.string.check_for_updates),
                            icon = Icons.Outlined.Update,
                            isLoading = isLoading,
                        ) {
                            if (!isLoading)
                                scope.launch {
                                    runCatching {
                                            isLoading = true
                                            withContext(Dispatchers.IO) {
                                                UpdateUtil.checkForUpdate()?.let {
                                                    release = it
                                                    showUpdateDialog = true
                                                }
                                                    ?: ToastUtil.makeToastSuspend(
                                                        context.getString(R.string.app_up_to_date)
                                                    )
                                            }
                                            isLoading = false
                                        }
                                        .onFailure {
                                            it.printStackTrace()
                                            ToastUtil.makeToastSuspend(
                                                context.getString(R.string.app_update_failed)
                                            )
                                            isLoading = false
                                        }
                                }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
                item {
                    PreferenceInfo(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.update_channel_desc),
                    )
                }
            }
        },
    )
    if (showUpdateDialog)
        UpdateDialog(onDismissRequest = { showUpdateDialog = false }, release = release)

    if (showUnavailableDialog) {
        AutoUpdateUnavailableDialog {
            showUnavailableDialog = false
            onNavigateBack()
        }
    }
}

@Composable
fun ProgressIndicatorButton(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        if (isLoading)
            Box(modifier = Modifier.size(18.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).align(Alignment.Center),
                    strokeWidth = 3.dp,
                )
            }
        else Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = text, modifier = Modifier.padding(start = 8.dp))
    }
}
