package com.magen.download.ui.page

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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.magen.download.R
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.PreferenceUtil.updateBoolean
import com.magen.download.util.SIMPLE_MODE
import com.magen.download.util.APP_MODE_SELECTED
import com.magen.download.util.SimpleModeSetup
import com.magen.download.util.ToastUtil

/**
 * Full-screen overlay shown on the very first app launch, letting the user pick
 * between Simple mode and Advanced mode. Simple mode is pre-selected by default.
 * Confirming with Simple mode also triggers the automatic one-time setup
 * (library update + cookies) via [SimpleModeSetup].
 */
@Composable
fun ModeSelectionOverlay(
    onConfirmed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var simpleModeSelected by rememberSaveable { mutableStateOf(true) }

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
            Text(
                text = stringResource(R.string.mode_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.mode_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            ModeCard(
                title = stringResource(R.string.app_mode_simple),
                description = stringResource(R.string.mode_simple_desc),
                icon = Icons.Outlined.RocketLaunch,
                selected = simpleModeSelected,
                onClick = { simpleModeSelected = true },
            )
            Spacer(Modifier.height(12.dp))
            ModeCard(
                title = stringResource(R.string.app_mode_advanced),
                description = stringResource(R.string.mode_advanced_desc),
                icon = Icons.Outlined.Tune,
                selected = !simpleModeSelected,
                onClick = { simpleModeSelected = false },
            )

            Button(
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
                modifier = Modifier.padding(top = 28.dp).fillMaxWidth(0.6f),
            ) {
                Text(text = stringResource(R.string.mode_confirm))
            }
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .selectable(selected = selected, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
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
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}
