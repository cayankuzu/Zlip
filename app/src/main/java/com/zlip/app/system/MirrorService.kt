package com.zlip.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zlip.app.MainActivity
import com.zlip.app.R
import com.zlip.app.ZlipApp

/**
 * Holds screen mirroring in the foreground, which Android requires for a MediaProjection. Started right after the
 * user allows sharing (never from the background, and never restarted by the system: a new process has no
 * permission to use). Its notification says what is happening and stops it.
 */
class MirrorService : Service() {

    private val app: ZlipApp get() = application as ZlipApp

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        try {
            // Before asking for the projection: Android hands it out only to a running mediaProjection service.
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot run screen mirroring in the foreground", e)
            app.mirror.onStartFailed()
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val projection = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Screen sharing permission could not be used", e)
            null
        }
        if (projection == null) {
            app.mirror.onStartFailed()
            stopSelf()
        } else {
            app.mirror.attach(projection)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Without this service a projection must not run on.
        app.mirror.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_zlip)
        .setContentTitle(getString(R.string.mirror_notif_title))
        .setContentText(getString(R.string.mirror_notif_text))
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(R.drawable.ic_pip_mirror_off, getString(R.string.action_stop), CoverActionReceiver.pendingIntent(this, CoverNotifier.ACTION_MIRROR_STOP))
        .build()

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.mirror_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.mirror_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ZlipMirror"
        private const val CHANNEL_ID = "mirror"
        private const val NOTIFICATION_ID = 3
        private const val REQUEST_OPEN = 20
        private const val EXTRA_RESULT_CODE = "com.zlip.app.extra.MIRROR_RESULT_CODE"
        private const val EXTRA_DATA = "com.zlip.app.extra.MIRROR_DATA"

        /** Call from the screen-sharing dialog's result, while Zlip is on screen. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MirrorService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MirrorService::class.java))
        }
    }
}
