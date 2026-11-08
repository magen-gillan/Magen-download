package com.magen.download.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.magen.download.R
import com.magen.download.util.APP_MODE_SELECTED
import com.magen.download.util.PreferenceUtil.updateBoolean
import com.magen.download.util.SIMPLE_MODE
import com.magen.download.util.SimpleModeSetup
import com.magen.download.util.ToastUtil

/**
 * Full-screen overlay shown on the very first app launch: the "Welcome" screen
 * from the shipped HTML mockup, letting the user pick between Simple mode and
 * Advanced mode. Simple mode is pre-selected by default. Confirming with
 * Simple mode also triggers the automatic one-time setup (library update +
 * cookies) via [SimpleModeSetup].
 *
 * Visual design: [OnboardingScaffold] — dark navy, Sora typography, badge
 * header, cover hero with the welcome title overlay, two option cards with
 * radio indicators, and the gradient pill "Done" button.
 */
@Composable
fun ModeSelectionOverlay(
    onConfirmed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var simpleModeSelected by rememberSaveable { mutableStateOf(true) }

    OnboardingScaffold(
        headerTitle = stringResource(R.string.app_name),
        headerSubtitle = stringResource(R.string.onboarding_by_brand),
        heroTitle = stringResource(R.string.welcome_brand_title),
        leadText = stringResource(R.string.onboarding_welcome_lead),
        modifier = modifier,
        options = {
            OnboardingOptionCard(
                icon = OnboardingIcons.Rocket,
                title = stringResource(R.string.app_mode_simple),
                description = stringResource(R.string.mode_simple_desc),
                selected = simpleModeSelected,
                onClick = { simpleModeSelected = true },
                trailing = { OnboardingRadioIcon(selected = simpleModeSelected) },
            )
            OnboardingOptionCard(
                icon = OnboardingIcons.Sliders,
                title = stringResource(R.string.app_mode_advanced),
                description = stringResource(R.string.mode_advanced_desc),
                selected = !simpleModeSelected,
                onClick = { simpleModeSelected = false },
                trailing = { OnboardingRadioIcon(selected = !simpleModeSelected) },
            )
        },
        actions = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.mode_confirm),
                onClick = {
                    val simple = simpleModeSelected
                    APP_MODE_SELECTED.updateBoolean(true)
                    SIMPLE_MODE.updateBoolean(simple)
                    if (simple) {
                        SimpleModeSetup.setup()
                        ToastUtil.makeToast(R.string.simple_mode_setup)
                    }
                    onConfirmed()
                },
            )
        },
    )
}
