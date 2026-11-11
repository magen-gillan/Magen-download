package com.magen.download.ui.page

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magen.download.R

/**
 * Design system for the first-run screens (welcome + permissions).
 *
 * Reproduces the shipped HTML mockups 1:1 — the dark navy palette, the Sora
 * typography, the white badge over the radial glow, the cover-cropped hero
 * card with the title overlay, the option cards with circular icon tiles, and
 * the pill gradient CTA.
 */
internal object OnboardingPalette {
    // Navy + gold, taken from the Magen Gillan logo.
    val bg = Color(0xFF07122B)
    val bgGlow = Color(0xFF14397D)
    val card = Color(0xFF0F1B38)
    val cardSelected = Color(0xFF172B58)
    val line = Color(0xFF22386B)
    val text = Color(0xFFF3F6FF)
    val mute = Color(0xFFA3B1D3)
    val accent = Color(0xFFF2C14E)
    val ok = Color(0xFF5FD39A)
    val warn = Color(0xFFFF9F8F)
    val iconCircle = Color(0xFF1B2D57)
    val radioOff = Color(0xFF5A6A8F)
    val buttonTop = Color(0xFFFFE08A)
    val buttonBottom = Color(0xFFD9A21B)
    val buttonText = Color(0xFF07122B)
}

/** Sora is bundled for the onboarding screens only (OFL license, res/font). */
internal val OnboardingFontFamily: FontFamily =
    FontFamily(
        androidx.compose.ui.text.font.Font(R.font.sora_medium, FontWeight.Medium),
        androidx.compose.ui.text.font.Font(R.font.sora_semibold, FontWeight.SemiBold),
        androidx.compose.ui.text.font.Font(R.font.sora_bold, FontWeight.Bold),
    )

@Composable
internal fun OnboardingText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight ?: (fontSize * 1.4f),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = OnboardingFontFamily),
    )
}

/**
 * Full-screen scaffold shared by the welcome and permissions screens.
 *
 * Layout (top to bottom): badge header, hero card (flex-grows like the CSS
 * `flex: 1`), lead paragraph, option cards, then the pinned actions column.
 * Mirrors the mockup's short-screen rule: below ~720px of height the lead is
 * hidden and the hero minimum height shrinks.
 */
@Composable
internal fun OnboardingScaffold(
    headerTitle: String,
    headerSubtitle: String,
    heroTitle: String,
    leadText: String,
    modifier: Modifier = Modifier,
    options: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .systemBarsPadding()
    ) {
        // radial-gradient(120% 60% at 50% 0%, #17315f 0%, #0a1020 60%)
        val density = LocalDensity.current
        val glowCenter = with(density) { Offset(maxWidth.toPx() / 2f, 0f) }
        val glowRadius = with(density) { maxWidth.toPx() * 1.2f }
        val screenMaxHeight = maxHeight
        val backgroundBrush =
            Brush.radialGradient(
                colorStops =
                    arrayOf(
                        0f to OnboardingPalette.bgGlow,
                        0.6f to OnboardingPalette.bg,
                    ),
                center = glowCenter,
                radius = glowRadius,
            )
        Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
            val shortScreen = screenMaxHeight < 560.dp
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OnboardingHeader(title = headerTitle, subtitle = headerSubtitle)

                OnboardingHero(
                    heroTitle = heroTitle,
                    minHeight = if (shortScreen) 100.dp else 130.dp,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (!shortScreen) {
                    OnboardingText(
                        text = leadText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnboardingPalette.mute,
                        lineHeight = 20.sp,
                    )
                }

                options()

                Spacer(modifier = Modifier.weight(0.02f))

                actions()
            }
        }
    }
}

@Composable
private fun OnboardingHeader(title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OnboardingLogoBadge(size = 60.dp, contentDescription = title)
        Column {
            OnboardingText(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = OnboardingPalette.text,
            )
            OnboardingText(
                text = subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = OnboardingPalette.mute,
            )
        }
    }
}

@Composable
private fun OnboardingHero(heroTitle: String, minHeight: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(OnboardingPalette.cardSelected, OnboardingPalette.card)
                    )
                )
                .border(BorderStroke(1.dp, OnboardingPalette.line), RoundedCornerShape(26.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.alisha_chibi),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.align(Alignment.CenterEnd).size(minHeight + 24.dp),
        )
        OnboardingText(
            text = heroTitle,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = OnboardingPalette.text,
            lineHeight = 26.sp,
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .padding(start = 18.dp, end = minHeight + 28.dp, top = 12.dp, bottom = 12.dp),
        )
    }
}

/** White rounded badge holding the Magen Gillan mark (the logo is navy, so it needs a light tile). */
@Composable
internal fun OnboardingLogoBadge(
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(Color.White)
                .border(
                    BorderStroke(2.dp, OnboardingPalette.accent),
                    RoundedCornerShape(size * 0.3f),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.magen_logo_mark),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.82f),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Small section heading used on the welcome screen. */
@Composable
internal fun OnboardingSectionTitle(text: String, modifier: Modifier = Modifier) {
    OnboardingText(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = OnboardingPalette.accent,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/** Option card: circular icon tile, title + description, trailing indicator. */
@Composable
internal fun OnboardingOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) OnboardingPalette.cardSelected else OnboardingPalette.card)
                .border(
                    BorderStroke(1.5.dp, if (selected) OnboardingPalette.accent else OnboardingPalette.line),
                    RoundedCornerShape(18.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(OnboardingPalette.iconCircle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnboardingPalette.text,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            OnboardingText(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnboardingPalette.text,
            )
            OnboardingText(
                text = description,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = OnboardingPalette.mute,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        trailing()
    }
}

/** Radio-style trailing indicator for the mode options (gray circle → blue). */
@Composable
internal fun OnboardingRadioIcon(selected: Boolean) {
    Icon(
        imageVector = OnboardingIcons.Radio,
        contentDescription = null,
        tint = if (selected) OnboardingPalette.accent else OnboardingPalette.radioOff,
        modifier = Modifier.size(20.dp),
    )
}

/** Check/warn trailing indicator for the permission rows. */
@Composable
internal fun OnboardingStatusIcon(granted: Boolean) {
    Icon(
        imageVector = if (granted) OnboardingIcons.StatusOk else OnboardingIcons.StatusWarn,
        contentDescription = null,
        tint = if (granted) OnboardingPalette.ok else OnboardingPalette.warn,
        modifier = Modifier.size(20.dp),
    )
}

/** Full-width pill button with the mockup's light-blue gradient. */
@Composable
internal fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.linearGradient(
                        listOf(OnboardingPalette.buttonTop, OnboardingPalette.buttonBottom)
                    )
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        OnboardingText(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = OnboardingPalette.buttonText.copy(alpha = if (enabled) 1f else 0.45f),
        )
    }
}

/** Muted full-width text button ("Skip for now"). */
@Composable
internal fun OnboardingTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        OnboardingText(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = OnboardingPalette.mute,
        )
    }
}

/**
 * Stroke icons traced from the mockup's inline SVGs (24dp viewport, 1.7 stroke,
 * round caps/joins — identical geometry to the HTML `svg` elements).
 */
internal object OnboardingIcons {

    private fun circleD(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

    private fun roundedRectD(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r} $y h${w - 2 * r} a$r $r 0 0 1 $r $r v${h - 2 * r} a$r $r 0 0 1 ${-r} $r " +
            "h${-(w - 2 * r)} a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)} a$r $r 0 0 1 $r ${-r} z"

    private fun strokeVector(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = addPathNodes(d),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.7f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }
            .build()

    val Rocket: ImageVector by lazy {
        strokeVector(
            "OnboardingRocket",
            "M5 19c0-3 1-4 3-4l-1-3 5-5c3-3 6-3 8-3 0 2 0 5-3 8l-5 5-3-1c0 2-1 3-4 3z",
            circleD(15f, 9f, 1.2f),
        )
    }

    val Sliders: ImageVector by lazy {
        strokeVector(
            "OnboardingSliders",
            "M4 7h9",
            "M17 7h3",
            "M4 17h3",
            "M11 17h9",
            circleD(15f, 7f, 2f),
            circleD(9f, 17f, 2f),
        )
    }

    val Bell: ImageVector by lazy {
        strokeVector("OnboardingBell", "M6 17V11a6 6 0 0 1 12 0v6l1.5 2h-15z", "M10 21h4")
    }

    val Folder: ImageVector by lazy {
        strokeVector(
            "OnboardingFolder",
            "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    val Battery: ImageVector by lazy {
        strokeVector("OnboardingBattery", roundedRectD(8f, 6f, 8f, 15f, 2f), "M10 3h4v3h-4z")
    }

    val StatusOk: ImageVector by lazy {
        strokeVector("OnboardingStatusOk", circleD(12f, 12f, 9f), "m8 12.5 3 3 5-6")
    }

    val StatusWarn: ImageVector by lazy {
        strokeVector("OnboardingStatusWarn", circleD(12f, 12f, 9f), "M12 7v6", "M12 16.5v.5")
    }

    val Radio: ImageVector by lazy { strokeVector("OnboardingRadio", circleD(12f, 12f, 9f)) }
}
