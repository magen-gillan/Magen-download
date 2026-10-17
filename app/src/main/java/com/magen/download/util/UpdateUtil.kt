package com.magen.download.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.magen.download.App
import com.magen.download.App.Companion.context
import com.magen.download.R
import com.magen.download.util.FileUtil.getFileProvider
import com.magen.download.util.PreferenceUtil.getInt
import com.magen.download.util.PreferenceUtil.updateLong
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object UpdateUtil : KoinComponent {

    private const val OWNER = "magen-gillan"
    private const val REPO = "Magen-download"
    private const val ARM64 = "arm64-v8a"
    private const val ARM32 = "armeabi-v7a"
    private const val X86 = "x86"
    private const val X64 = "x86_64"
    private const val TAG = "UpdateUtil"

    private val client = OkHttpClient()

    /**
     * Serializes yt-dlp update runs. Concurrent callers (SimpleModeSetup at
     * first launch + the YtdlpUpdater composable + settings buttons) used to
     * delete/re-extract the yt-dlp directory at the same time, producing
     * intermittent "update failed" errors and corrupt installs.
     */
    private val updateMutex = Mutex()

    /**
     * Lazy lookup: Koin may not be started yet when UpdateUtil is first
     * touched (App.onCreate), so resolve on first use instead of init.
     */
    private val downloaderV2: com.magen.download.download.DownloaderV2? by lazy {
        runCatching { get<com.magen.download.download.DownloaderV2>() }.getOrNull()
    }

    private val requestForLatestRelease =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases/latest")
            .build()

    private val requestForReleases =
        Request.Builder().url("https://api.github.com/repos/${OWNER}/${REPO}/releases").build()

    private const val ytdlpNightlyBuildRelease =
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            updateMutex.withLock {
                // Never replace the yt-dlp binary while downloads are active:
                // the updater deletes the yt-dlp directory and re-extracts it,
                // which breaks every yt-dlp process alive in that window.
                // (All call sites — SimpleModeSetup, YtdlpUpdater, settings —
                // are routed through this single guard now.)
                if ((downloaderV2?.countBusyTasks() ?: 0) > 0) {
                    Log.w(TAG, "yt-dlp update postponed: downloads are active")
                    return@withLock null
                }

                val channel =
                    when (YT_DLP_UPDATE_CHANNEL.getInt()) {
                        YT_DLP_NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                        else -> YoutubeDL.UpdateChannel.STABLE
                    }

                YoutubeDL.getInstance()
                    .updateYoutubeDL(appContext = context, updateChannel = channel)
                    .also {
                        // Only refresh the "last successful update" time when the update
                        // actually succeeded (DONE / ALREADY_UP_TO_DATE; on failure the
                        // library throws, so this block never runs). Recording the time
                        // on failure would postpone the next retry by a whole interval
                        // (a week), leaving the user on a stale yt-dlp build — old
                        // extractors get throttled by YouTube (near-zero speeds).
                        if (it == YoutubeDL.UpdateStatus.DONE ||
                            it == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE
                        ) {
                            // A failed version lookup right after a successful update
                            // must not prevent recording the update time (that would
                            // trigger a redundant re-update on next launch).
                            runCatching {
                                YoutubeDL.getInstance().version(context)?.let { version ->
                                    PreferenceUtil.encodeString(YT_DLP_VERSION, version)
                                }
                            }.onFailure { it.printStackTrace() }
                            YT_DLP_UPDATE_TIME.updateLong(System.currentTimeMillis())
                        }
                    }
            }
        }

    /** Prefer the release title, fall back to the (always-present) tag name. */
    private fun Release.versionOf(): Version =
        (name?.takeIf { it.isNotBlank() } ?: tagName).toVersion()

    private fun getLatestRelease(): Release {
        client.newCall(requestForReleases).execute().use { response ->
            // A 403 (rate limit) / 404 used to fall through to JSON parsing and
            // surface as a confusing deserialization error; check the status
            // first and close the response defensively.
            if (!response.isSuccessful) {
                throw Exception("GitHub releases request failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw Exception("GitHub releases: empty response body")
            val releaseList = jsonFormat.decodeFromString<List<Release>>(body.string())
            val stable = UPDATE_CHANNEL.getInt() == STABLE
            // Version from name with tag_name fallback: GitHub releases without
            // an explicit title have name == null and would sort as 0.0.0,
            // silently disabling the app updater.
            return releaseList
                .filter { if (stable) it.versionOf() is Version.Stable else true }
                .maxByOrNull { it.versionOf() } ?: throw Exception("no releases found")
        }
    }

    fun checkForUpdate(context: Context = App.context): Release? {
        val currentVersion = context.getCurrentVersion()
        val latestRelease = getLatestRelease()
        val latestVersion = latestRelease.versionOf()
        return if (currentVersion < latestVersion) latestRelease else null
    }

    private fun Context.getCurrentVersion(): Version =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
                .toVersion()
        } else {
            packageManager.getPackageInfo(packageName, 0).versionName.toVersion()
        }

    private fun Context.getLatestApk() = File(getExternalFilesDir("apk"), "latest.apk")

    fun installLatestApk(context: Context = App.context) =
        context.run {
            kotlin
                .runCatching {
                    val contentUri =
                        FileProvider.getUriForFile(this, getFileProvider(), getLatestApk())
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setDataAndType(contentUri, "application/vnd.android.package-archive")
                        }
                    startActivity(intent)
                }
                .onFailure { throwable: Throwable ->
                    throwable.printStackTrace()
                    ToastUtil.makeToast(R.string.app_update_failed)
                }
        }

    suspend fun deleteOutdatedApk(context: Context = App.context) =
        context.runCatching {
            val apkFile = getLatestApk()
            if (apkFile.exists()) {
                val apkVersion =
                    context.packageManager
                        .getPackageArchiveInfo(apkFile.absolutePath, 0)
                        ?.versionName
                        .toVersion()
                if (apkVersion <= context.getCurrentVersion()) {
                    apkFile.delete()
                }
            }
        }

    suspend fun downloadApk(
        context: Context = App.context,
        release: Release,
    ): Flow<DownloadStatus> =
        withContext(Dispatchers.IO) {
            val apkVersion =
                context.packageManager
                    .getPackageArchiveInfo(context.getLatestApk().absolutePath, 0)
                    ?.versionName
                    .toVersion()

            Log.d(TAG, apkVersion.toString())

            if (apkVersion >= release.versionOf()) {
                return@withContext flow<DownloadStatus> {
                    emit(DownloadStatus.Finished(context.getLatestApk()))
                }
            }

            val abiList = Build.SUPPORTED_ABIS
            if (abiList.isEmpty()) return@withContext emptyFlow()

            // Match against every supported ABI (device priority order) with a
            // boundary check: a plain contains("x86") also matches "x86_64" and
            // could download the wrong APK on x86 devices.
            val targetUrl =
                release.assets
                    ?.firstOrNull { asset ->
                        val assetName = asset.name ?: return@firstOrNull false
                        abiList.any { abi ->
                            assetName.contains("$abi.") ||
                                assetName.contains("$abi-") ||
                                assetName.endsWith("-$abi") ||
                                assetName.endsWith("_$abi")
                        }
                    }
                    ?.browserDownloadUrl ?: return@withContext emptyFlow()
            val request = Request.Builder().url(targetUrl).build()
            try {
                val response = client.newCall(request).execute()
                // A CDN/HTTP error page (404/403 HTML) would otherwise be saved
                // byte-for-byte and later offered for installation.
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw Exception("APK download failed: HTTP $code")
                }
                val responseBody = response.body
                if (responseBody == null) {
                    response.close()
                    throw Exception("APK download failed: empty response body")
                }
                return@withContext responseBody.downloadFileWithProgress(context.getLatestApk())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            emptyFlow()
        }

    private fun ResponseBody.downloadFileWithProgress(saveFile: File): Flow<DownloadStatus> =
        flow {
                emit(DownloadStatus.Progress(0))

                var deleteFile = true

                try {
                    byteStream().use { inputStream ->
                        saveFile.outputStream().use { outputStream ->
                            val totalBytes = contentLength()
                            val isLengthKnown = totalBytes > 0
                            val data = ByteArray(8_192)
                            var progressBytes = 0L

                            while (true) {
                                val bytes = inputStream.read(data)

                                if (bytes == -1) {
                                    break
                                }

                                outputStream.write(data, 0, bytes)
                                progressBytes += bytes
                                emit(
                                    DownloadStatus.Progress(
                                        percent =
                                            if (isLengthKnown)
                                                ((progressBytes * 100) / totalBytes).toInt()
                                            else 0
                                    )
                                )
                            }

                            when {
                                isLengthKnown && progressBytes < totalBytes ->
                                    throw Exception("missing bytes")
                                isLengthKnown && progressBytes > totalBytes ->
                                    throw Exception("too many bytes")
                                else -> deleteFile = false
                            }
                        }
                    }

                    // Integrity gate: an error page (or truncated file) that
                    // happened to match the content-length would otherwise be
                    // offered for installation. A real APK always parses.
                    if (
                        App.context.packageManager
                            .getPackageArchiveInfo(saveFile.absolutePath, 0) == null
                    ) {
                        throw Exception("downloaded file is not a valid APK")
                    }

                    emit(DownloadStatus.Finished(saveFile))
                } finally {
                    if (deleteFile) {
                        saveFile.delete()
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    @Serializable
    data class Release(
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val draft: Boolean? = null,
        @SerialName("prerelease") val preRelease: Boolean? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<AssetsItem>? = null,
        val body: String? = null,
    )

    @Serializable
    data class AssetsItem(
        val name: String? = null,
        @SerialName("content_type") val contentType: String? = null,
        val size: Int? = null,
        @SerialName("download_count") val downloadCount: Int? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )

    sealed class DownloadStatus {
        object NotYet : DownloadStatus()

        data class Progress(val percent: Int) : DownloadStatus()

        data class Finished(val file: File) : DownloadStatus()
    }

    private val pattern = Pattern.compile("""v?(\d+)\.(\d+)\.(\d+)(-(\w+)\.(\d+))?""")
    private val EMPTY_VERSION = Version.Stable()

    fun String?.toVersion(): Version =
        this?.run {
            val matcher = pattern.matcher(this)
            if (matcher.find()) {
                val major = matcher.group(1)?.toInt() ?: 0
                val minor = matcher.group(2)?.toInt() ?: 0
                val patch = matcher.group(3)?.toInt() ?: 0
                val buildNumber = matcher.group(6)?.toInt() ?: 0
                when (matcher.group(5)) {
                    "alpha" -> Version.Alpha(major, minor, patch, buildNumber)
                    "beta" -> Version.Beta(major, minor, patch, buildNumber)
                    "rc" -> Version.ReleaseCandidate(major, minor, patch, buildNumber)
                    else -> Version.Stable(major, minor, patch)
                }
            } else EMPTY_VERSION
        } ?: EMPTY_VERSION

    sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int = 0) :
        Comparable<Version> {
        companion object {
            // private const val ABI = 1L
            private const val BUILD = 10L
            private const val VARIANT = 100L
            private const val PATCH = 10_000L
            private const val MINOR = 1_000_000L
            private const val MAJOR = 100_000_000L

            private const val STABLE = VARIANT * 4
            private const val ALPHA = VARIANT * 1
            private const val BETA = VARIANT * 2
            private const val RELEASE_CANDIDATE = VARIANT * 3
        }

        abstract fun toVersionName(): String

        abstract fun toNumber(): Long

        class Alpha(
            versionMajor: Int = 0,
            versionMinor: Int = 0,
            versionPatch: Int = 0,
            versionBuild: Int = 0,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-alpha.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + ALPHA
        }

        class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
            Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-beta.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + BETA
        }

        class ReleaseCandidate(
            versionMajor: Int,
            versionMinor: Int,
            versionPatch: Int,
            versionBuild: Int,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-rc.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + RELEASE_CANDIDATE
        }

        class Stable(versionMajor: Int = 0, versionMinor: Int = 0, versionPatch: Int = 0) :
            Version(versionMajor, versionMinor, versionPatch) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + STABLE
            // Prioritize stable versions

        }

        override operator fun compareTo(other: Version): Int =
            this.toNumber().compareTo(other.toNumber())
    }
}
