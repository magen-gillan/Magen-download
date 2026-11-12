package com.magen.download.ui.page

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * One tap on "Grant permissions" walks the user through EVERY missing permission UI
 * back to back, instead of one window per tap:
 *
 *  1. the runtime dialogs that can be batched (notifications on API 33+, legacy
 *     storage on API <= 29) through a single RequestMultiplePermissions call —
 *     Android shows them as consecutive dialogs from the single tap;
 *  2. then the "All files access" settings page (API 30+) when storage is still
 *     missing;
 *  3. then the battery-optimization dialog as soon as the user comes back.
 *
 * Every window opens at most once per press, and only for permissions that are
 * actually still missing. If the user backs out of the all-files page without
 * granting, the chain stops there instead of nagging.
 *
 * Returns a lambda that starts the chain (used by the welcome screen and the
 * permissions gate "Grant permissions" button).
 */
@Composable
internal fun rememberPermissionChainLauncher(): () -> Unit {
    val context = LocalContext.current

    var resumeTick by remember { mutableIntStateOf(0) }
    var stoppedTick by remember { mutableIntStateOf(0) }
    // -1 = not armed; otherwise the stoppedTick captured when the all-files page was
    // opened. The step only advances when the app was really STOPPED after arming
    // (the round trip to the settings page), so a spurious resume between two
    // consecutive windows can never consume the step early.
    var storagePageArmedAtStop by remember { mutableIntStateOf(-1) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeTick++
                Lifecycle.Event.ON_STOP -> stoppedTick++
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun batteryGrantedNow(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun storageGrantedNow(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    fun continueToBatteryIfMissing() {
        if (!batteryGrantedNow()) context.requestIgnoreBatteryOptimizations()
    }

    fun openStoragePageAndArm() {
        storagePageArmedAtStop = stoppedTick
        context.openAllFilesAccessSettings()
    }

    val runtimeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // All runtime dialogs were answered. Continue the chain: the all-files
            // page (API 30+) when storage is still missing, otherwise the battery
            // dialog directly.
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                openStoragePageAndArm()
            } else {
                continueToBatteryIfMissing()
            }
        }

    fun startChain() {
        val runtime =
            buildList {
                if (
                    Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (
                    Build.VERSION.SDK_INT <= 29 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        when {
            runtime.isNotEmpty() -> runtimeLauncher.launch(runtime.toTypedArray())
            Build.VERSION.SDK_INT >= 30 && !storageGrantedNow() -> openStoragePageAndArm()
            else -> continueToBatteryIfMissing()
        }
    }

    // Back from the all-files page: continue with the battery dialog, but only if
    // the storage page was really visited (the app was stopped since arming).
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        val armed = storagePageArmedAtStop
        if (armed >= 0 && stoppedTick > armed) {
            storagePageArmedAtStop = -1
            if (storageGrantedNow()) continueToBatteryIfMissing()
        }
    }

    return ::startChain
}
