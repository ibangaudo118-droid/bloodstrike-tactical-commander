package com.bloodstrike.tacticalcommander

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager

class CaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "tactical_capture"
        private const val NOTIFICATION_ID = 7001
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startProjection(resultCode, resultData)
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        projection = manager.getMediaProjection(resultCode, resultData)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

        val sourceWidth = metrics.widthPixels
        val sourceHeight = metrics.heightPixels

        // Keep the first prototype light enough for low-memory phones.
        val maxWidth = 960
        val scale = minOf(1f, maxWidth.toFloat() / sourceWidth.toFloat())
        val width = (sourceWidth * scale).toInt().coerceAtLeast(320)
        val height = (sourceHeight * scale).toInt().coerceAtLeast(240)

        handlerThread = HandlerThread("tactical-frame-reader").also { it.start() }
        val handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            // Phase 1 only: prove that frames are arriving continuously.
            // Phase 2 will sample frames here and send them to the AI vision layer.
            reader.acquireLatestImage()?.close()
        }, handler)

        virtualDisplay = projection!!.createVirtualDisplay(
            "TacticalCommanderCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )

        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
            }
        }, handler)
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        projection?.stop()
        projection = null

        handlerThread?.quitSafely()
        handlerThread = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Tactical Commander is watching")
            .setContentText("Live screen capture is active.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tactical Commander Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(
        key: String
    ): T? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
    }
}
