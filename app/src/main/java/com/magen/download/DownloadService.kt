package com.magen.download

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.magen.download.download.DownloaderV2
import com.magen.download.util.NotificationUtil
import com.magen.download.util.NotificationUtil.SERVICE_NOTIFICATION_ID
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private const val TAG = "DownloadService"

/**
 * Foreground keep-alive service for the download queue.
 *
 * Lifecycle rules:
 * - Started via [App.startService] (startForegroundService + bind), so the
 *   service survives transient unbinds (activity recreation, config change).
 * - The service calls startForeground() as the very first thing in both
 *   [onCreate] and [onStartCommand]. Android requires this within a few
 *   seconds of startForegroundService(); if the service is stopped before it
 *   happens the app is killed with ForegroundServiceDidNotStartInTimeException.
 *   For that reason [App.stopService] never stops a service that has not
 *   reached the foreground yet: it sets [pendingStop] and the service stops
 *   itself right after it has called startForeground().
 * - [onUnbind] only tears the service down when the download queue is idle;
 *   while downloads are busy the service stays started and [onRebind] is
 *   called when the client rebinds.
 */
class DownloadService : Service(), KoinComponent {

    private val downloaderV2: DownloaderV2 by lazy { get<DownloaderV2>() }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must come first: this is what satisfies startForegroundService().
        startForegroundWithNotification()

        // A stop was requested while this service was still starting up.
        if (pendingStop) {
            pendingStop = false
            Log.d(TAG, "onStartCommand: deferred stop requested")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        // Re-created by the system after process death (intent == null): the
        // download queue is restored as "canceled", nothing is running, so do
        // not leave a stale "downloading..." notification behind.
        if (intent == null && !isDownloadQueueBusy()) {
            Log.d(TAG, "onStartCommand: sticky restart with idle queue")
            stopSelfSafely()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return DownloadServiceBinder()
    }

    private fun startForegroundWithNotification() {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE,
                )
            }
        val notification = NotificationUtil.makeServiceNotification(pendingIntent)
        try {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
            isForegroundStarted = true
        } catch (e: Exception) {
            // Android 12+ may refuse a foreground promotion when the service was
            // only bound from the background. Downloads still run in-process, we
            // just lose the foreground priority - never crash the app for it.
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun isDownloadQueueBusy(): Boolean =
        runCatching { downloaderV2.countBusyTasks() }.getOrDefault(0) > 0

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        // Only stop when the queue is idle - a transient unbind used to kill
        // the foreground service (and its notification) while downloads were
        // still running, causing the system to throttle/freeze them.
        if (isDownloadQueueBusy()) {
            Log.d(TAG, "onUnbind: downloads active, staying alive")
            return true // onRebind() will be called when clients rebind
        }
        stopSelfSafely()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isForegroundStarted = false
        // The system can kill the service independently of App.stopService()
        // (memory pressure, app swipe). Without this reset the stale
        // isServiceRunning flag made the next App.startService() return early
        // and downloads then ran without any foreground service at all.
        App.isServiceRunning = false
        super.onDestroy()
    }

    inner class DownloadServiceBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    companion object {
        /** True once [startForeground] succeeded for the current service instance. */
        @Volatile var isForegroundStarted = false

        /**
         * Set by [App.stopService] when the service was asked to stop before it
         * reached the foreground. The service then stops itself safely.
         */
        @Volatile var pendingStop = false
    }
}
