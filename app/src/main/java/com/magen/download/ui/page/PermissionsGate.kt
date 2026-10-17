package com.magen.download.ui.page

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.magen.download.App
import com.magen.download.R
import com.magen.download.util.PERMISSIONS_REQUESTED
import com.magen.download.util.PreferenceUtil.updateBoolean
import kotlinx.coroutines.delay

/**
 * Full-screen overlay that asks for every permission the app needs, shown right
 * after the first-launch mode selection (and reachable later from Settings):
 *
 * 1. Notifications (API 33+) — download progress and completion.
 * 2. Storage — runtime WRITE_EXTERNAL_STORAGE on API <= 29, "All files access"
 *    (isExternalStorageManager) on API 30+, so downloads can always be saved
 *    and managed in the public Downloads folder.
 * 3. Battery optimization exemption — OEM battery managers (e.g. Honor/Xiaomi)
 *    freeze background apps and silently stall ongoing downloads.
 *
 * Finishing the gate (all granted, or skipped) records [PERMISSIONS_REQUESTED]
 * so the overlay is not shown again on every launch.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsGate(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    inSettings: Boolean = false,
) {
    val context = LocalContext.current

    val notificationsPermission =
        if (Build.VERSION.SDK_INT >= 33)
            rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        else null

    val legacyStoragePermission =
        if (Build.VERSION.SDK_INT <= 29)
            rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else null

    // System permission states can change while we are away in the system
    // settings UI; refresh them every time the app comes back to the foreground.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsGranted =
        remember(resumeTick) {
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        }
    val storageGranted =
        remember(resumeTick) {
            if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
            else
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
        }
    val batteryGranted =
        remember(resumeTick) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }

    val allGranted = notificationsGranted && storageGranted && batteryGranted

    if (allGranted) {
        PERMISSIONS_REQUESTED.updateBoolean(true)
        LaunchedEffect(Unit) {
            delay(400)
            onDone()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (inSettings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDone) {
                        Text(text = stringResource(R.string.close))
                    }
                }
            }

            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            PermissionRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.permissions_notifications),
                description = stringResource(R.string.permissions_notifications_desc),
                granted = notificationsGranted,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.permissions_storage),
                description = stringResource(R.string.permissions_storage_desc),
                granted = storageGranted,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Outlined.BatteryFull,
                title = stringResource(R.string.permissions_battery),
                description = stringResource(R.string.permissions_battery_desc),
                granted = batteryGranted,
            )

            Button(
                onClick = {
                    when {
                        !notificationsGranted -> {
                            if (Build.VERSION.SDK_INT >= 33)
                                notificationsPermission?.launchPermissionRequest()
                        }

                        !storageGranted -> {
                            if (Build.VERSION.SDK_INT <= 29)
                                legacyStoragePermission?.launchPermissionRequest()
                            else context.openAllFilesAccessSettings()
                        }

                        !batteryGranted -> {
                            runCatching {
                                    context.startActivity(
                                        Intent(
                                                Settings
                                                    .ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            )
                                            .setData(Uri.parse("package:${context.packageName}"))
                                    )
                                }
                                .onFailure { it.printStackTrace() }
                        }
                    }
                },
                enabled = !allGranted,
                modifier = Modifier.padding(top = 28.dp).fillMaxWidth(0.6f),
            ) {
                Text(
                    text =
                        stringResource(
                            if (allGranted) R.string.permissions_all_granted
                            else R.string.permissions_grant
                        )
                )
            }

            if (!inSettings) {
                TextButton(
                    onClick = {
                        PERMISSIONS_REQUESTED.updateBoolean(true)
                        onDone()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permissions_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Context.openAllFilesAccessSettings() {
    runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
        .onFailure {
            runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                .onFailure { it.printStackTrace() }
        }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).selectable(selected = false, onClick = {}),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                imageVector =
                    if (granted) Icons.Filled.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint =
                    if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            )
        }
    }
}
