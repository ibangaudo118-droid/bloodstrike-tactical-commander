package com.bloodstrike.tacticalcommander

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager

class CaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "tactical_capture"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Tactical Commander")
            .setContentText("Live screen capture is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == -1 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startCapture(resultCode, data)

        return START_NOT_STICKY
    }

    private fun startCapture(
        resultCode: Int,
        data: Intent
    ) {
        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        mediaProjection =
            manager.getMediaProjection(resultCode, data)

        val windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener(
            { reader ->
                val image = reader.acquireLatestImage()

                if (image != null) {
                    // We successfully received a live screen frame.
                    // AI analysis will be added after this capture step.

                    image.close()
                }
            },
            null
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TacticalCommanderCapture",
            width,
            height,
            density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager =
            getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
