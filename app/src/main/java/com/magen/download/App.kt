package com.magen.download

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import com.magen.download.download.DownloaderV2
import com.magen.download.download.DownloaderV2Impl
import com.magen.download.ui.page.download.HomePageViewModel
import com.magen.download.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.magen.download.ui.page.settings.directory.Directory
import com.magen.download.ui.page.settings.network.CookiesViewModel
import com.magen.download.ui.page.videolist.VideoListViewModel
import com.magen.download.util.AUDIO_DIRECTORY
import com.magen.download.util.COMMAND_DIRECTORY
import com.magen.download.util.DownloadUtil
import com.magen.download.util.FileUtil
import com.magen.download.util.FileUtil.createEmptyFile
import com.magen.download.util.FileUtil.getCookiesFile
import com.magen.download.util.FileUtil.getExternalDownloadDirectory
import com.magen.download.util.FileUtil.getExternalPrivateDownloadDirectory
import com.magen.download.util.NotificationUtil
import com.magen.download.util.PreferenceUtil
import com.magen.download.util.PreferenceUtil.getString
import com.magen.download.util.PreferenceUtil.updateString
import com.magen.download.util.SDCARD_URI
import com.magen.download.util.UpdateUtil
import com.magen.download.util.VIDEO_DIRECTORY
import com.magen.download.util.YT_DLP_VERSION
import com.tencent.mmkv.MMKV
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                module {
                    single<DownloaderV2> { DownloaderV2Impl(androidContext()) }
                    viewModel { DownloadDialogViewModel(downloader = get()) }
                    viewModel { HomePageViewModel() }
                    viewModel { CookiesViewModel() }
                    viewModel { VideoListViewModel() }
                }
            )
        }

        context = applicationContext
        packageInfo =
            packageManager.run {
                if (Build.VERSION.SDK_INT >= 33)
                    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                else getPackageInfo(packageName, 0)
            }
        applicationScope = CoroutineScope(SupervisorJob())
        DynamicColors.applyToActivitiesIfAvailable(this)

        clipboard = requireNotNull(getSystemService()) { "ClipboardManager unavailable" }
        connectivityManager =
            requireNotNull(getSystemService()) { "ConnectivityManager unavailable" }

        applicationScope.launch((Dispatchers.IO)) {
            try {
                YoutubeDL.init(this@App)
                FFmpeg.init(this@App)
                Aria2c.init(this@App)
                DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let {
                    FileUtil.writeContentToFile(it, getCookiesFile())
                }
                UpdateUtil.deleteOutdatedApk()
            } catch (th: Throwable) {
                withContext(Dispatchers.Main) { startCrashReportActivity(th) }
            }
        }

        videoDownloadDir = VIDEO_DIRECTORY.getString(getExternalDownloadDirectory().absolutePath)

        audioDownloadDir = AUDIO_DIRECTORY.getString(File(videoDownloadDir, "Audio").absolutePath)
        if (!PreferenceUtil.containsKey(COMMAND_DIRECTORY)) {
            COMMAND_DIRECTORY.updateString(videoDownloadDir)
        }
        if (Build.VERSION.SDK_INT >= 26) NotificationUtil.createNotificationChannel()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            startCrashReportActivity(e)
            previousHandler?.uncaughtException(t, e)
        }
    }

    private fun startCrashReportActivity(th: Throwable) {
        th.printStackTrace()
        startActivity(
            Intent(this, CrashReportActivity::class.java)
                .setAction("$packageName.error_report")
                .apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("error_report", getVersionReport() + "\n" + th.stackTraceToString())
                }
        )
    }

    companion object {
        lateinit var clipboard: ClipboardManager
        lateinit var videoDownloadDir: String
        lateinit var audioDownloadDir: String
        lateinit var applicationScope: CoroutineScope
        lateinit var connectivityManager: ConnectivityManager
        lateinit var packageInfo: PackageInfo

        var isServiceRunning = false

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    val binder = service as DownloadService.DownloadServiceBinder
                    isServiceRunning = true
                }

                override fun onServiceDisconnected(arg0: ComponentName) {}
            }

        @Synchronized
        fun startService() {
            if (isServiceRunning) return
            isServiceRunning = true
            Intent(context.applicationContext, DownloadService::class.java).also { intent ->
                // startService keeps the foreground service alive even when the
                // binder connection drops transiently (activity recreation,
                // config change). With the old bind-only lifecycle the FGS and
                // its notification were torn down mid-download on unbind.
                runCatching {
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.applicationContext.startForegroundService(intent)
                    } else {
                        context.applicationContext.startService(intent)
                    }
                }
                    .onFailure {
                        // Background FGS start restrictions (Android 12+):
                        // fall back to the binding only — downloads still run
                        // in-process, we just lose the foreground priority.
                        it.printStackTrace()
                    }
                runCatching {
                    context.applicationContext.bindService(
                        intent,
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                }.onFailure { it.printStackTrace() }
            }
        }

        @Synchronized
        fun stopService() {
            if (!isServiceRunning) return
            isServiceRunning = false
            try {
                context.applicationContext.run {
                    unbindService(connection)
                    // Stop the (possibly startService-started) foreground
                    // service as well — the queue drained, nothing is running.
                    stopService(Intent(this, DownloadService::class.java))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val privateDownloadDir: String
            get() =
                getExternalPrivateDownloadDirectory().run {
                    createEmptyFile(".nomedia")
                    absolutePath
                }

        /**
         * Resolves and persists the directory for [directoryType]. Returns the
         * resolved filesystem path so callers (e.g. the directory settings UI)
         * display exactly what was stored instead of re-resolving the URI a
         * second time with possibly diverging results.
         */
        fun updateDownloadDir(uri: Uri, directoryType: Directory): String {
            val path = FileUtil.getRealPath(uri)
            when (directoryType) {
                Directory.AUDIO -> {
                    audioDownloadDir = path
                    PreferenceUtil.encodeString(AUDIO_DIRECTORY, path)
                }

                Directory.VIDEO -> {
                    videoDownloadDir = path
                    PreferenceUtil.encodeString(VIDEO_DIRECTORY, path)
                }

                Directory.CUSTOM_COMMAND -> {
                    // Previously the resolved path was computed and discarded —
                    // the custom-command directory preference was never saved.
                    COMMAND_DIRECTORY.updateString(path)
                }

                Directory.SDCARD -> {
                    context.contentResolver?.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    PreferenceUtil.encodeString(SDCARD_URI, uri.toString())
                }
            }
            return path
        }

        fun getVersionReport(): String {
            val versionName = packageInfo.versionName
            val page = packageInfo
            val versionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
            val release =
                if (Build.VERSION.SDK_INT >= 30) {
                    Build.VERSION.RELEASE_OR_CODENAME
                } else {
                    Build.VERSION.RELEASE
                }
            return StringBuilder()
                .append("App version: $versionName ($versionCode)\n")
                .append("Device information: Android $release (API ${Build.VERSION.SDK_INT})\n")
                .append("Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}\n")
                .append("Yt-dlp version: ${YT_DLP_VERSION.getString()}\n")
                .toString()
        }

        fun isFDroidBuild(): Boolean = BuildConfig.FLAVOR == "fdroid"

        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

        @SuppressLint("StaticFieldLeak") lateinit var context: Context
    }
}
