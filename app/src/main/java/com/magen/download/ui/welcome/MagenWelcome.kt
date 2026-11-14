package com.magen.download.ui.welcome

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.magen.download.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Magen shared welcome screen. Identical in all four apps (only the package line differs).
 * Foundation-only Compose: no Material dependency, no new library, one 160 KB raw video.
 * Language = device language (Android picks values/ or values-ar/ automatically).
 */

// ───────────────────────────── model ─────────────────────────────

sealed interface PermissionRequest {
    class Runtime(val permissions: List<String>) : PermissionRequest
    /** Several candidate intents; the first one that launches wins. */
    class Settings(val intents: List<Intent>) : PermissionRequest
}

class WelcomePermission(
    val glyph: String,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val isGranted: (Context) -> Boolean,
    val request: (Context) -> PermissionRequest?,
)

class WelcomeChoice(@StringRes val title: Int, @StringRes val desc: Int? = null)

/** An app-specific setting shown above the permissions (e.g. Simple / Advanced). */
class WelcomeOption(
    @StringRes val title: Int,
    val choices: List<WelcomeChoice>,
    val required: Boolean = false,
    val defaultIndex: Int? = null,
)

object MagenPermissions {
    private fun has(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    private fun pkgUri(c: Context) = Uri.parse("package:${c.packageName}")

    fun notifications() = WelcomePermission(
        "♢", R.string.mwelcome_perm_notifications_title, R.string.mwelcome_perm_notifications_desc,
        { c -> Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS) },
        { if (Build.VERSION.SDK_INT >= 33) PermissionRequest.Runtime(listOf(Manifest.permission.POST_NOTIFICATIONS)) else null },
    )

    fun exactAlarms() = WelcomePermission(
        "◷", R.string.mwelcome_perm_alarms_title, R.string.mwelcome_perm_alarms_desc,
        { c -> Build.VERSION.SDK_INT < 31 || (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() },
        { c ->
            if (Build.VERSION.SDK_INT >= 31)
                PermissionRequest.Settings(listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(c))))
            else null
        },
    )

    fun battery() = WelcomePermission(
        "◒", R.string.mwelcome_perm_battery_title, R.string.mwelcome_perm_battery_desc,
        { c -> (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName) },
        { c ->
            PermissionRequest.Settings(
                listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(c)),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
            )
        },
    )

    private fun media(glyph: String, @StringRes t: Int, @StringRes d: Int, api33: String) = WelcomePermission(
        glyph, t, d,
        { c -> has(c, if (Build.VERSION.SDK_INT >= 33) api33 else Manifest.permission.READ_EXTERNAL_STORAGE) },
        { PermissionRequest.Runtime(listOf(if (Build.VERSION.SDK_INT >= 33) api33 else Manifest.permission.READ_EXTERNAL_STORAGE)) },
    )

    fun audio() = media("♪", R.string.mwelcome_perm_audio_title, R.string.mwelcome_perm_audio_desc, Manifest.permission.READ_MEDIA_AUDIO)
    fun images() = media("▣", R.string.mwelcome_perm_images_title, R.string.mwelcome_perm_images_desc, Manifest.permission.READ_MEDIA_IMAGES)
    fun videos() = media("▶", R.string.mwelcome_perm_videos_title, R.string.mwelcome_perm_videos_desc, Manifest.permission.READ_MEDIA_VIDEO)

    /** All-files access on Android 11+, legacy WRITE_EXTERNAL_STORAGE below. */
    fun files() = WelcomePermission(
        "▣", R.string.mwelcome_perm_files_title, R.string.mwelcome_perm_files_desc,
        { c ->
            if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
            else has(c, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        },
        { c ->
            if (Build.VERSION.SDK_INT >= 30) PermissionRequest.Settings(
                listOf(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri(c)),
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(c)),
                )
            ) else PermissionRequest.Runtime(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        },
    )

    fun installUpdates() = WelcomePermission(
        "⇩", R.string.mwelcome_perm_install_title, R.string.mwelcome_perm_install_desc,
        { c -> Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls() },
        { c ->
            if (Build.VERSION.SDK_INT >= 26)
                PermissionRequest.Settings(listOf(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri(c))))
            else null
        },
    )
}

// ───────────────────────────── screen ─────────────────────────────

private val Ink = Color(0xFF050914)
private val Card = Color(0xE80B162B)
private val Line = Color(0xFF20365B)
private val Muted = Color(0xFF9BAAC7)
private val Green = Color(0xFF36E39A)
private val Warn = Color(0xFFFF9E8E)

private enum class Say { Hi, Next, Done, Later }

/**
 * One full-screen welcome: Alisha greets the user, one button runs EVERY permission request
 * back to back, then [onFinished] is called with the selected option index (or null).
 * Layout scales itself to fit any screen size / orientation: nothing is cropped, nothing scrolls.
 */
@Composable
fun MagenWelcomeScreen(
    @StringRes brandName: Int,
    @StringRes tagline: Int,
    @StringRes helloTitle: Int,
    @StringRes helloBody: Int,
    @DrawableRes logo: Int,
    accent: Color,
    permissions: List<WelcomePermission>,
    onFinished: (selectedChoice: Int?) -> Unit,
    modifier: Modifier = Modifier,
    option: WelcomeOption? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var say by remember { mutableStateOf(Say.Hi) }
    var selected by remember { mutableStateOf(option?.defaultIndex) }
    val granted = remember(tick) { permissions.map { it.isGranted(context) } }
    val waitingForChoice = option?.required == true && selected == null

    val holder = remember { object { var pending: CompletableDeferred<Unit>? = null } }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        holder.pending?.complete(Unit)
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        holder.pending?.complete(Unit)
    }

    fun start() {
        if (busy || waitingForChoice) return
        busy = true
        scope.launch {
            permissions.forEachIndexed { i, p ->
                if (!p.isGranted(context)) {
                    val req = p.request(context)
                    if (req != null) {
                        val gate = CompletableDeferred<Unit>().also { holder.pending = it }
                        val launched = runCatching {
                            when (req) {
                                is PermissionRequest.Runtime -> runtimeLauncher.launch(req.permissions.toTypedArray())
                                is PermissionRequest.Settings -> {
                                    val ok = req.intents.any { runCatching { settingsLauncher.launch(it) }.isSuccess }
                                    if (!ok) error("no settings screen")
                                }
                            }
                        }.isSuccess
                        if (launched) gate.await()
                    }
                }
                tick++
                say = if (i == permissions.lastIndex) Say.Done else Say.Next
            }
            delay(900)
            busy = false
            onFinished(selected)
        }
    }

    Box(modifier.fillMaxSize().background(Ink)) {
        WelcomeVideo(Modifier.fillMaxSize())
        Layout(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            content = {
                Box(Modifier.layoutId("b")) { Brand(brandName, tagline, logo, accent) }
                Box(Modifier.layoutId("h"), contentAlignment = Alignment.TopEnd) {
                    Speech(say, helloTitle, helloBody, accent)
                }
                Box(Modifier.layoutId("p")) {
                    Panel(
                        accent, permissions, granted, option, selected, waitingForChoice, busy,
                        onPick = { if (!busy) selected = it },
                        onGrant = ::start,
                        onSkip = {
                            if (!busy && !waitingForChoice) {
                                say = Say.Later
                                scope.launch { delay(800); onFinished(selected) }
                            }
                        },
                    )
                }
            },
        ) { ms, c ->
            val w = c.maxWidth
            val h = c.maxHeight
            val rtl = layoutDirection == LayoutDirection.Rtl
            val land = w > h * 1.2f
            val gap = 18.dp.roundToPx()
            val heroW = if (land) (w * 0.44f).toInt() else w
            val colW = if (land) w - heroW - gap else w
            val mb = ms.first { it.layoutId == "b" }
            val mh = ms.first { it.layoutId == "h" }
            val mp = ms.first { it.layoutId == "p" }
            fun fixedW(x: Int) = Constraints(minWidth = x, maxWidth = x)
            // pass 1: natural heights → scale; pass 2: re-measure wider so the scaled block fills the column
            val b0 = mb.measure(fixedW(colW))
            val p0 = mp.measure(fixedW(colW))
            val avail = if (land) h else (h * 0.78f).toInt()
            val s = (avail.toFloat() / (b0.height + p0.height)).coerceIn(0.5f, 1f)
            val wide = (colW / s).toInt()
            val b = mb.measure(fixedW(wide))
            val p = mp.measure(fixedW(wide))
            val bH = (b.height * s).toInt()
            val pH = (p.height * s).toInt()
            fun px(x: Int, width: Int) = if (rtl) w - x - width else x
            layout(w, h) {
                val colX = if (land) heroW + gap else 0
                b.placeWithLayer(px(colX, colW), 0) {
                    scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0f)
                }
                val pY = if (land) bH + (h - bH - pH) / 2 else h - pH
                p.placeWithLayer(px(colX, colW), pY) {
                    scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0f)
                }
                if (land) {
                    mh.measure(Constraints.fixed(heroW, h)).place(px(0, heroW), 0)
                } else {
                    val hh = (h - bH - pH).coerceAtLeast(0)
                    mh.measure(Constraints.fixed(w, hh)).place(0, bH)
                }
            }
        }
    }
}

@Composable
private fun T(
    text: String,
    size: Float,
    color: Color = Color(0xFFF5F8FF),
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
) = BasicText(
    text,
    modifier,
    style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = align, lineHeight = (size * 1.55f).sp),
)

@Composable
private fun Brand(@StringRes name: Int, @StringRes tag: Int, @DrawableRes logo: Int, accent: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
        Image(
            painterResource(logo), null,
            Modifier.size(44.dp).clip(RoundedCornerShape(30)).border(2.dp, accent, RoundedCornerShape(30)),
        )
        Column(Modifier.padding(horizontal = 10.dp)) {
            T(stringResource(name), 18f, weight = FontWeight.Bold)
            T(stringResource(tag), 11f, Color(0xFFCFE0FF))
        }
    }
}

@Composable
private fun Speech(say: Say, @StringRes hiT: Int, @StringRes hiB: Int, accent: Color) {
    val (t, b) = when (say) {
        Say.Hi -> hiT to hiB
        Say.Next -> R.string.mwelcome_next_t to R.string.mwelcome_next_b
        Say.Done -> R.string.mwelcome_done_t to R.string.mwelcome_done_b
        Say.Later -> R.string.mwelcome_later_t to R.string.mwelcome_later_b
    }
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(stringResource(t)) }
        append("\n"); append(stringResource(b))
    }
    Box(
        Modifier.padding(top = 10.dp).widthIn(max = 220.dp).fillMaxWidth(0.62f)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xE8071226))
            .border(1.dp, Color(0x2CFFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
    ) { BasicText(text, style = TextStyle(color = Color(0xFFF5F8FF), fontSize = 12.5.sp, lineHeight = 20.sp)) }
}

@Composable
private fun Panel(
    accent: Color,
    permissions: List<WelcomePermission>,
    granted: List<Boolean>,
    option: WelcomeOption?,
    selected: Int?,
    waiting: Boolean,
    busy: Boolean,
    onPick: (Int) -> Unit,
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        T(stringResource(R.string.mwelcome_title), 19f, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        T(stringResource(R.string.mwelcome_sub), 12f, Muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 9.dp))
        if (option != null) {
            T(stringResource(option.title), 11.5f, Color(0xFFCFE0FF), align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), Arrangement.spacedBy(6.dp)) {
                option.choices.forEachIndexed { i, ch ->
                    val on = selected == i
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFF14233F) else Card)
                            .border(if (on) 1.5.dp else 1.dp, if (on) accent else Line, RoundedCornerShape(14.dp))
                            .clickable { onPick(i) }.padding(horizontal = 6.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        T(stringResource(ch.title), 12.5f, weight = FontWeight.Bold, align = TextAlign.Center)
                        ch.desc?.let { T(stringResource(it), 10.5f, Muted, align = TextAlign.Center) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(7.dp)) {
            permissions.forEachIndexed { i, p ->
                val ok = granted.getOrElse(i) { false }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (ok) Color(0xFF0C211F) else Card)
                        .border(1.dp, if (ok) Color(0xFF276F59) else Line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (ok) Color(0xFF164936) else Color(0xFF182C50)),
                        contentAlignment = Alignment.Center,
                    ) { T(if (ok) "✓" else p.glyph, 16f, if (ok) Green else Color(0xFFA9D8FF)) }
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        T(stringResource(p.title), 12.5f, weight = FontWeight.SemiBold)
                        T(stringResource(p.desc), 10.5f, Muted)
                    }
                    T(stringResource(if (ok) R.string.mwelcome_granted else R.string.mwelcome_required), 10.5f, if (ok) Green else Warn)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 12.dp).alpha(if (waiting || busy) 0.45f else 1f)
                .clip(RoundedCornerShape(18.dp)).background(accent)
                .clickable(enabled = !waiting && !busy, onClick = onGrant).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            T(
                stringResource(if (waiting) R.string.mwelcome_pick_first else R.string.mwelcome_go),
                15f, Color(0xFF091327), FontWeight.ExtraBold, TextAlign.Center,
            )
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 18.dp).clickable(enabled = !waiting && !busy, onClick = onSkip), contentAlignment = Alignment.Center) {
            if (!waiting) T(stringResource(R.string.mwelcome_skip), 12f, Color(0xFF8FA2C3), FontWeight.SemiBold, TextAlign.Center)
        }
    }
}

// ───────────────────────────── video ─────────────────────────────

/** Looping muted Alisha video, centre-cropped to cover the screen, faded into the UI. */
@Composable
private fun WelcomeVideo(modifier: Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val land = maxWidth > maxHeight * 1.2f
        val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
        val videoMod = if (land) {
            Modifier.fillMaxHeight().fillMaxWidth(0.48f)
                .align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val stops = arrayOf(0f to Color.Black, 0.65f to Color.Black, 1f to Color.Transparent)
                    drawRect(
                        if (rtl) Brush.horizontalGradient(*stops, startX = size.width, endX = 0f)
                        else Brush.horizontalGradient(*stops),
                        blendMode = BlendMode.DstIn,
                    )
                }
        } else Modifier.fillMaxSize()
        CoverVideo(videoMod)
        if (!land) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0xD9050914), 0.20f to Color.Transparent, 0.38f to Color.Transparent,
                        0.70f to Color(0xE6050914), 1f to Ink,
                    )
                )
            )
        }
    }
}

@Composable
private fun CoverVideo(modifier: Modifier) {
    val context = LocalContext.current
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) { onDispose { runCatching { player[0]?.release() }; player[0] = null } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CoverTextureView(ctx).also { tv ->
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        runCatching {
                            val afd = context.resources.openRawResourceFd(R.raw.alisha_welcome)
                            val mp = MediaPlayer()
                            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                            mp.setSurface(Surface(st))
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            mp.setOnVideoSizeChangedListener { _, vw, vh -> tv.vw = vw; tv.vh = vh; tv.cover() }
                            mp.setOnPreparedListener { it.start() }
                            mp.prepareAsync()
                            player[0] = mp
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { tv.cover() }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { player[0]?.release() }; player[0] = null; return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
    )
}

private class CoverTextureView(context: Context) : TextureView(context) {
    var vw = 0
    var vh = 0

    /** Centre-crop the video to the view (object-fit: cover), keeping the face area visible. */
    fun cover() {
        if (vw == 0 || vh == 0 || width == 0 || height == 0) return
        val sc = maxOf(width / vw.toFloat(), height / vh.toFloat())
        val m = Matrix()
        m.setScale(vw * sc / width, vh * sc / height, width / 2f, height * 0.18f)
        setTransform(m)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        cover()
    }
}
