package com.fastdm.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.fastdm.app.FastDMApp
import com.fastdm.app.R
import com.fastdm.app.core.DownloadItem
import com.fastdm.app.core.formatSpeed
import com.fastdm.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object Notifier {
    const val CH_PROGRESS = "progress"
    const val CH_DONE = "done"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, ctx.getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, ctx.getString(R.string.notif_channel_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @SuppressLint("MissingPermission")
    fun complete(ctx: Context, item: DownloadItem) {
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(ctx.getString(R.string.notif_done))
            .setContentText(item.fileName)
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(item.id.hashCode(), n)
    }

    @SuppressLint("MissingPermission")
    fun failed(ctx: Context, item: DownloadItem) {
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(ctx.getString(R.string.notif_failed))
            .setContentText(item.fileName)
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(item.id.hashCode(), n)
    }
}

/** Keeps the process alive while downloads are running and shows aggregate progress. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as FastDMApp).engine
        if (intent?.action == ACTION_PAUSE_ALL) engine.pauseAll()

        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID,
                build(engine.items.value, engine.speeds.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (_: Exception) {
        }

        if (watcher == null) {
            watcher = scope.launch {
                combine(engine.items, engine.speeds) { i, s -> i to s }.collect { (items, speeds) ->
                    if (items.none { it.isActive }) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        NotificationManagerCompat.from(this@DownloadService)
                            .notify(NOTIFICATION_ID, build(items, speeds))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(items: List<DownloadItem>, speeds: Map<String, Long>): Notification {
        val active = items.filter { it.isActive }
        val done = active.sumOf { it.downloadedBytes }
        val total = active.sumOf { maxOf(it.totalBytes, 0L) }
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        val pause = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Notifier.CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.notif_title, active.size))
            .setContentText(formatSpeed(speeds.values.sum()))
            .setProgress(100, pct, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notifier.openAppIntent(this))
            .addAction(0, getString(R.string.pause_all), pause)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_PAUSE_ALL = "com.fastdm.app.PAUSE_ALL"
    }
}
