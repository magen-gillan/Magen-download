package com.magen.download.ui.svg

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.magen.download.ui.svg.drawablevectors.coder
import com.magen.download.ui.svg.drawablevectors.download
import com.magen.download.ui.svg.drawablevectors.videoFiles
import com.magen.download.ui.svg.drawablevectors.videoSteaming
import com.magen.download.ui.theme.MagenTheme

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Night", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Download() {
    MagenTheme {
        Surface {
            Column {
                Image(
                    imageVector = DynamicColorImageVectors.download(),
                    contentDescription = null,
                    modifier = Modifier.aspectRatio(16 / 9f),
                )
                Image(
                    imageVector = DynamicColorImageVectors.coder(),
                    contentDescription = null,
                    modifier = Modifier.aspectRatio(16 / 9f),
                )
                Image(
                    imageVector = DynamicColorImageVectors.videoFiles(),
                    contentDescription = null,
                    modifier = Modifier.aspectRatio(16 / 9f),
                )
                Image(
                    imageVector = DynamicColorImageVectors.videoSteaming(),
                    contentDescription = null,
                    modifier = Modifier.aspectRatio(16 / 9f),
                )
            }
        }
    }
}
