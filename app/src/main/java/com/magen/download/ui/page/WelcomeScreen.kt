package com.magen.download.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.magen.download.R
import com.magen.download.ui.welcome.MagenPermissions
import com.magen.download.ui.welcome.MagenWelcomeScreen
import com.magen.download.ui.welcome.WelcomeChoice
import com.magen.download.ui.welcome.WelcomeOption
import com.magen.download.util.APP_MODE_SELECTED
import com.magen.download.util.PERMISSIONS_REQUESTED
import com.magen.download.util.PreferenceUtil.getBoolean
import com.magen.download.util.PreferenceUtil.updateBoolean
import com.magen.download.util.SIMPLE_MODE
import com.magen.download.util.SimpleModeSetup
import com.magen.download.util.ToastUtil

/**
 * First-run screen (replaces the old multi-card WelcomeScreen). Same entry point as before:
 * WelcomeFlow(onFinished). Mode choice (Simple / Advanced) is required: the button stays
 * disabled until one is picked, exactly like the previous screen. Permissions: notifications,
 * file access, battery exemption, all requested back to back by one button.
 */
@Composable
fun WelcomeFlow(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    // Upgrading users keep their existing mode pre-selected; new users start with nothing selected.
    val initialSimple: Boolean? = remember {
        if (APP_MODE_SELECTED.getBoolean()) SIMPLE_MODE.getBoolean() else null
    }
    MagenWelcomeScreen(
        brandName = R.string.mwelcome_brand,
        tagline = R.string.mwelcome_tag,
        helloTitle = R.string.mwelcome_hi_t,
        helloBody = R.string.mwelcome_hi_b,
        logo = R.drawable.magen_gillan_logo,
        accent = Color(0xFF36E39A),
        permissions = remember { listOf(MagenPermissions.notifications(), MagenPermissions.files(), MagenPermissions.battery()) },
        option = WelcomeOption(
            title = R.string.welcome_mode_section,
            choices = listOf(
                WelcomeChoice(R.string.app_mode_simple, R.string.mode_simple_desc),
                WelcomeChoice(R.string.app_mode_advanced, R.string.mode_advanced_desc),
            ),
            required = true,
            defaultIndex = initialSimple?.let {{ if (it) 0 else 1 }},
        ),
        onFinished = { choice ->
            val simple = choice == 0
            APP_MODE_SELECTED.updateBoolean(true)
            SIMPLE_MODE.updateBoolean(simple)
            if (simple && initialSimple != true) {
                // One-time automatic setup of Simple mode (yt-dlp update + cookies).
                SimpleModeSetup.setup()
                ToastUtil.makeToast(R.string.simple_mode_setup)
            }
            PERMISSIONS_REQUESTED.updateBoolean(true)
            onFinished()
        },
        modifier = modifier,
    )
}
