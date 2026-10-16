package com.magen.download.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magen.download.R
import com.magen.download.ui.component.MagenDialog
import com.magen.download.ui.component.ConfirmButton
import com.magen.download.ui.component.DismissButton

/**
 * Suggests downloading the URL(s) currently copied to the clipboard.
 * Shown when the app comes to the foreground and detects a fresh
 * clipboard entry containing links.
 */
@Composable
fun ClipboardSuggestionDialog(
    urls: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    MagenDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ConfirmButton(text = stringResource(id = R.string.clipboard_suggestion_confirm)) {
                onConfirm(urls)
            }
        },
        dismissButton = { DismissButton { onDismiss() } },
        icon = { Icon(imageVector = Icons.Outlined.ContentPaste, contentDescription = null) },
        title = { Text(text = stringResource(id = R.string.clipboard_suggestion_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text =
                        if (urls.size == 1)
                            stringResource(id = R.string.clipboard_suggestion_message)
                        else
                            stringResource(
                                id = R.string.clipboard_suggestion_multiple,
                                urls.size,
                            ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = urls.joinToString(separator = "\n") { it },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}
