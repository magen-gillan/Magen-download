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
 * - [onUnbind] only tears the service down when the download queue is idle;
 *   while downloads are busy the service stays started and [onRebind] is
 *   called when the client rebinds.
 * - [App.stopService] stops it explicitly once the queue drains.
 */
class DownloadService : Service(), KoinComponent {

    private val downloaderV2: DownloaderV2 by lazy { get<DownloaderV2>() }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        // Recreated after process death (START_STICKY): the foreground
        // notification is restored; the download queue itself is rehydrated by
        // DownloaderV2Impl.enqueueFromBackup().
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
        startForeground(SERVICE_NOTIFICATION_ID, notification)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        // Only stop when the queue is idle — a transient unbind used to kill
        // the foreground service (and its notification) while downloads were
        // still running, causing the system to throttle/freeze them.
        val busy =
            runCatching { downloaderV2.countBusyTasks() }.getOrDefault(0) > 0
        if (busy) {
            Log.d(TAG, "onUnbind: downloads active, staying alive")
            return true // onRebind() will be called when clients rebind
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
        return super.onUnbind(intent)
    }

    inner class DownloadServiceBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
}
