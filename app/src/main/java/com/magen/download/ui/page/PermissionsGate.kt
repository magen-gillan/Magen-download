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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.magen.download.R
import com.magen.download.util.PERMISSIONS_REQUESTED
import com.magen.download.util.PreferenceUtil.updateBoolean
import kotlinx.coroutines.delay

/**
 * Full-screen overlay that asks for every permission the app needs, shown right
 * after the first-launch welcome screen (and reachable later from Settings):
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
 *
 * Visual design: the "App permissions" screen from the shipped HTML mockup —
 * same [OnboardingScaffold] as the welcome screen, with real-time check/warn
 * status icons on each row (tapping a row requests that specific permission).
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
        LaunchedEffect(Unit) {
            // Side effect in an effect, not in composition: writing the flag
            // during composition re-wrote it on every recomposition.
            PERMISSIONS_REQUESTED.updateBoolean(true)
            delay(900)
            onDone()
        }
    }

    OnboardingScaffold(
        headerTitle = stringResource(R.string.permissions_title),
        headerSubtitle = stringResource(R.string.permissions_subtitle),
        heroTitle = stringResource(R.string.onboarding_one_last_step),
        leadText =
            stringResource(
                if (allGranted) R.string.permission_mood_granted
                else R.string.permission_mood_denied
            ),
        modifier = modifier,
        options = {
            OnboardingOptionCard(
                icon = OnboardingIcons.Bell,
                title = stringResource(R.string.permissions_notifications),
                description = stringResource(R.string.permissions_notifications_desc),
                selected = notificationsGranted,
                onClick = {
                    if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
                        notificationsPermission?.launchPermissionRequest()
                    }
                },
                trailing = { OnboardingStatusIcon(granted = notificationsGranted) },
            )
            OnboardingOptionCard(
                icon = OnboardingIcons.Folder,
                title = stringResource(R.string.permissions_storage),
                description = stringResource(R.string.permissions_storage_desc),
                selected = storageGranted,
                onClick = {
                    if (!storageGranted) {
                        if (Build.VERSION.SDK_INT <= 29) {
                            legacyStoragePermission?.launchPermissionRequest()
                        } else {
                            context.openAllFilesAccessSettings()
                        }
                    }
                },
                trailing = { OnboardingStatusIcon(granted = storageGranted) },
            )
            OnboardingOptionCard(
                icon = OnboardingIcons.Battery,
                title = stringResource(R.string.permissions_battery),
                description = stringResource(R.string.permissions_battery_desc),
                selected = batteryGranted,
                onClick = {
                    if (!batteryGranted) {
                        context.requestIgnoreBatteryOptimizations()
                    }
                },
                trailing = { OnboardingStatusIcon(granted = batteryGranted) },
            )
        },
        actions = {
            OnboardingPrimaryButton(
                text =
                    stringResource(
                        if (allGranted) R.string.permissions_all_granted
                        else R.string.permissions_grant
                    ),
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

                        !batteryGranted -> context.requestIgnoreBatteryOptimizations()
                    }
                },
                enabled = !allGranted,
            )

            if (!inSettings) {
                OnboardingTextButton(
                    text = stringResource(R.string.permissions_skip),
                    onClick = {
                        PERMISSIONS_REQUESTED.updateBoolean(true)
                        onDone()
                    },
                )
            } else {
                OnboardingTextButton(text = stringResource(R.string.close), onClick = onDone)
            }
        },
    )
}

internal fun Context.requestIgnoreBatteryOptimizations() {
    runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
        .onFailure { it.printStackTrace() }
}

internal fun Context.openAllFilesAccessSettings() {
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
