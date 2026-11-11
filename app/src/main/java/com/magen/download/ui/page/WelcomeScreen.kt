package com.magen.download.ui.page

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.magen.download.R
import com.magen.download.util.APP_MODE_SELECTED
import com.magen.download.util.PERMISSIONS_REQUESTED
import com.magen.download.util.PreferenceUtil.getBoolean
import com.magen.download.util.PreferenceUtil.updateBoolean
import com.magen.download.util.SIMPLE_MODE
import com.magen.download.util.SimpleModeSetup
import com.magen.download.util.ToastUtil
import kotlinx.coroutines.delay

/**
 * First-run screen: ONE page that replaces the old welcome + mode + permissions overlays.
 *
 * Top to bottom: the Magen Gillan logo, chibi Alisha with the welcome message, a short
 * explanation of the app, the Simple / Advanced mode choice and the permission rows.
 *
 * The user enters the app directly (no extra "Done" tap) as soon as BOTH conditions hold:
 *  1. a usage mode has been picked (nothing is pre-selected, so this is an explicit choice);
 *  2. every permission is granted (notifications, file access, battery exemption).
 *
 * "Skip permissions" is offered once a mode is picked, so a user who refuses a permission
 * in the system UI can never get stuck on this screen.
 *
 * Also shown to users upgrading from an older version that never saw the permissions
 * step; in that case their existing mode is pre-selected.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WelcomeFlow(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ---- mode ------------------------------------------------------------------------
    val initialMode: Boolean? = remember {
        if (APP_MODE_SELECTED.getBoolean()) SIMPLE_MODE.getBoolean() else null
    }
    var simpleSelected: Boolean? by rememberSaveable { mutableStateOf(initialMode) }

    // ---- permissions -------------------------------------------------------------------
    val notificationsPermission =
        if (Build.VERSION.SDK_INT >= 33)
            rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        else null
    val legacyStoragePermission =
        if (Build.VERSION.SDK_INT <= 29)
            rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else null

    // Permissions can change while the user is in the system settings: re-check on resume.
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
    val modeChosen = simpleSelected != null

    fun requestNextMissingPermission() {
        when {
            !notificationsGranted -> {
                if (Build.VERSION.SDK_INT >= 33) notificationsPermission?.launchPermissionRequest()
            }

            !storageGranted -> {
                if (Build.VERSION.SDK_INT <= 29) legacyStoragePermission?.launchPermissionRequest()
                else context.openAllFilesAccessSettings()
            }

            !batteryGranted -> context.requestIgnoreBatteryOptimizations()
        }
    }

    fun finishOnboarding() {
        val simple = simpleSelected ?: return
        val wasSimpleBefore = initialMode == true
        APP_MODE_SELECTED.updateBoolean(true)
        SIMPLE_MODE.updateBoolean(simple)
        if (simple && !wasSimpleBefore) {
            // One-time automatic setup of Simple mode (yt-dlp update + cookies).
            SimpleModeSetup.setup()
            ToastUtil.makeToast(R.string.simple_mode_setup)
        }
        PERMISSIONS_REQUESTED.updateBoolean(true)
        onFinished()
    }

    // Straight into the app once mode + permissions are both done. Restarts (cancelling the
    // short delay) if the user changes the mode in the meantime.
    val ready = allGranted && modeChosen
    LaunchedEffect(ready, simpleSelected) {
        if (!ready) return@LaunchedEffect
        delay(700)
        finishOnboarding()
    }

    // ---- layout ------------------------------------------------------------------------
    val density = LocalDensity.current
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Swallow touches so nothing underneath (the home page) can be tapped.
                .pointerInput(Unit) { detectTapGestures {} }
                .background(OnboardingPalette.bg)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OnboardingPalette.bgGlow, OnboardingPalette.bg),
                        center = Offset(with(density) { 180.dp.toPx() }, 0f),
                        radius = with(density) { 520.dp.toPx() },
                    )
                )
                .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WelcomeLogoHeader()
                AlishaWelcomeCard(allGranted = allGranted)
                AboutAppCard()

                OnboardingSectionTitle(stringResource(R.string.welcome_mode_section))
                OnboardingOptionCard(
                    icon = OnboardingIcons.Rocket,
                    title = stringResource(R.string.app_mode_simple),
                    description = stringResource(R.string.mode_simple_desc),
                    selected = simpleSelected == true,
                    onClick = { simpleSelected = true },
                    trailing = { OnboardingRadioIcon(selected = simpleSelected == true) },
                )
                OnboardingOptionCard(
                    icon = OnboardingIcons.Sliders,
                    title = stringResource(R.string.app_mode_advanced),
                    description = stringResource(R.string.mode_advanced_desc),
                    selected = simpleSelected == false,
                    onClick = { simpleSelected = false },
                    trailing = { OnboardingRadioIcon(selected = simpleSelected == false) },
                )

                OnboardingSectionTitle(stringResource(R.string.welcome_permissions_section))
                OnboardingText(
                    text = stringResource(R.string.welcome_permissions_why),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnboardingPalette.mute,
                    lineHeight = 18.sp,
                )
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
                        if (!batteryGranted) context.requestIgnoreBatteryOptimizations()
                    },
                    trailing = { OnboardingStatusIcon(granted = batteryGranted) },
                )
            }

            // Pinned action area.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    !allGranted ->
                        OnboardingPrimaryButton(
                            text = stringResource(R.string.permissions_grant),
                            onClick = { requestNextMissingPermission() },
                        )

                    !modeChosen ->
                        OnboardingPrimaryButton(
                            text = stringResource(R.string.welcome_cta_choose_mode),
                            onClick = {},
                            enabled = false,
                        )

                    else ->
                        OnboardingPrimaryButton(
                            text = stringResource(R.string.welcome_cta_enter),
                            onClick = { finishOnboarding() },
                        )
                }
                if (modeChosen && !allGranted) {
                    OnboardingTextButton(
                        text = stringResource(R.string.welcome_skip_permissions),
                        onClick = { finishOnboarding() },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeLogoHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnboardingLogoBadge(
            size = 96.dp,
            contentDescription = stringResource(R.string.app_name),
        )
        OnboardingText(
            text = stringResource(R.string.app_name),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = OnboardingPalette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OnboardingText(
            text = stringResource(R.string.onboarding_by_brand),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = OnboardingPalette.mute,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AlishaWelcomeCard(allGranted: Boolean) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(OnboardingPalette.cardSelected, OnboardingPalette.card)
                    )
                )
                .border(BorderStroke(1.dp, OnboardingPalette.line), shape)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.alisha_chibi),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(132.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardingText(
                text = stringResource(R.string.welcome_brand_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = OnboardingPalette.text,
                lineHeight = 23.sp,
            )
            OnboardingText(
                text =
                    stringResource(
                        if (allGranted) R.string.permission_mood_granted
                        else R.string.welcome_alisha_greeting
                    ),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = OnboardingPalette.mute,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun AboutAppCard() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(OnboardingPalette.card)
                .border(BorderStroke(1.dp, OnboardingPalette.line), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnboardingText(
            text = stringResource(R.string.welcome_about_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnboardingPalette.text,
        )
        OnboardingText(
            text = stringResource(R.string.welcome_about_body),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = OnboardingPalette.mute,
            lineHeight = 19.sp,
        )
        FeatureLine(stringResource(R.string.welcome_feature_share))
        FeatureLine(stringResource(R.string.welcome_feature_background))
        FeatureLine(stringResource(R.string.welcome_feature_history))
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = OnboardingIcons.StatusOk,
            contentDescription = null,
            tint = OnboardingPalette.accent,
            modifier = Modifier.size(18.dp),
        )
        OnboardingText(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = OnboardingPalette.text,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
