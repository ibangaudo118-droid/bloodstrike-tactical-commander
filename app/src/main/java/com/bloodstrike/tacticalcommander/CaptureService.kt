package com.bloodstrike.tacticalcommander

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import android.content.pm.ServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "tactical_capture"
        private const val NOTIFICATION_ID = 1001

        // Analyze approximately once per second.
        private const val ANALYSIS_INTERVAL_MS = 1000L

        // Keep capture workload reasonable for low-end phones.
        private const val MAX_CAPTURE_WIDTH = 720
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay:
            android.hardware.display.VirtualDisplay? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val analysisRunning =
        AtomicBoolean(false)

    private var lastAnalysisTime = 0L

    private var frameCount = 0L

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {
                stopCapture()
            }
        }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        captureThread =
            HandlerThread(
                "TacticalCaptureThread"
            ).also {
                it.start()

                captureHandler =
                    Handler(it.looper)
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode =
            intent?.getIntExtra(
                "resultCode",
                -1
            ) ?: -1

        val data =
            if (Build.VERSION.SDK_INT >= 33) {

                intent?.getParcelableExtra(
                    "data",
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")
                intent?.getParcelableExtra("data")
            }

        if (
            resultCode == -1 ||
            data == null
        ) {

            stopSelf()

            return START_NOT_STICKY
        }

        val notification =
            buildNotification(
                "Starting tactical vision..."
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        startCapture(
            resultCode,
            data
        )

        return START_NOT_STICKY
    }

    private fun startCapture(
        resultCode: Int,
        data: Intent
    ) {

        if (mediaProjection != null) {
            return
        }

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        mediaProjection =
            manager.getMediaProjection(
                resultCode,
                data
            )

        val projection =
            mediaProjection ?: run {

                stopSelf()

                return
            }

        projection.registerCallback(
            projectionCallback,
            captureHandler
        )

        val windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        val metrics =
            DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager
            .defaultDisplay
            .getMetrics(metrics)

        val originalWidth =
            metrics.widthPixels

        val originalHeight =
            metrics.heightPixels

        val density =
            metrics.densityDpi

        val scale =
            if (
                originalWidth >
                MAX_CAPTURE_WIDTH
            ) {

                MAX_CAPTURE_WIDTH.toFloat() /
                    originalWidth.toFloat()

            } else {

                1f
            }

        val width =
            (
                originalWidth * scale
            )
                .toInt()
                .coerceAtLeast(1)

        val height =
            (
                originalHeight * scale
            )
                .toInt()
                .coerceAtLeast(1)

        imageReader =
            ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            )

        imageReader?.setOnImageAvailableListener(
            { reader ->
                processLatestImage(reader)
            },
            captureHandler
        )

        virtualDisplay =
            projection.createVirtualDisplay(
                "TacticalCommanderCapture",
                width,
                height,
                density,
                DisplayManager
                    .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                captureHandler
            )

        updateNotification(
            "LIVE • Tactical vision active"
        )
    }

    private fun processLatestImage(
        reader: ImageReader
    ) {

        val image =
            try {

                reader.acquireLatestImage()

            } catch (_: Exception) {

                null
            }

        if (image == null) {
            return
        }

        try {

            frameCount++

            val now =
                System.currentTimeMillis()

            if (
                now - lastAnalysisTime <
                ANALYSIS_INTERVAL_MS
            ) {
                return
            }

            if (
                !analysisRunning.compareAndSet(
                    false,
                    true
                )
            ) {
                return
            }

            lastAnalysisTime = now

            val bitmap =
                imageToBitmap(image)

            if (bitmap == null) {

                analysisRunning.set(false)

                return
            }

            val jpeg =
                bitmapToJpeg(bitmap)

            bitmap.recycle()

            if (jpeg == null) {

                analysisRunning.set(false)

                return
            }

            val encoded =
                Base64.encodeToString(
                    jpeg,
                    Base64.NO_WRAP
                )

            serviceScope.launch {

                try {

                    val authResult =
                        VisionCommander.signIn()

                    if (
                        authResult.isFailure
                    ) {

                        updateNotification(
                            "AI authentication failed"
                        )

                        return@launch
                    }

                    updateNotification(
                        "LIVE • Analyzing gameplay..."
                    )

                    val result =
                        VisionCommander.analyzeFrame(
                            encoded
                        )

                    if (result.isSuccess) {

                        val response =
                            result.getOrNull()
                                ?: ""

                        val command =
                            extractCommand(
                                response
                            )

                        if (
                            command.isNotBlank()
                        ) {

                            CommanderOverlay.show(
                                applicationContext,
                                command
                            )

                            updateNotification(
                                "LIVE • $command"
                            )
                        }

                    } else {

                        updateNotification(
                            "LIVE • AI request failed"
                        )
                    }

                } catch (_: Exception) {

                    updateNotification(
                        "LIVE • Waiting for AI"
                    )

                } finally {

                    analysisRunning.set(false)
                }
            }

        } finally {

            image.close()
        }
    }

    private fun imageToBitmap(
        image: Image
    ): Bitmap? {

        return try {

            val plane =
                image.planes[0]

            val buffer =
                plane.buffer

            val pixelStride =
                plane.pixelStride

            val rowStride =
                plane.rowStride

            val rowPadding =
                rowStride -
                    pixelStride *
                    image.width

            val paddedWidth =
                image.width +
                    rowPadding /
                    pixelStride

            val bitmap =
                Bitmap.createBitmap(
                    paddedWidth,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )

            buffer.rewind()

            bitmap.copyPixelsFromBuffer(
                buffer
            )

            if (
                paddedWidth ==
                image.width
            ) {

                bitmap

            } else {

                val cropped =
                    Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        image.width,
                        image.height
                    )

                bitmap.recycle()

                cropped
            }

        } catch (_: Exception) {

            null
        }
    }

    private fun bitmapToJpeg(
        bitmap: Bitmap
    ): ByteArray? {

        return try {

            val output =
                ByteArrayOutputStream()

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                55,
                output
            )

            output.toByteArray()

        } catch (_: Exception) {

            null
        }
    }

    private fun extractCommand(
        response: String
    ): String {

        val marker =
            "\"command\""

        val markerIndex =
            response.indexOf(marker)

        if (markerIndex == -1) {

            return response
                .replace("{", "")
                .replace("}", "")
                .replace("\"", "")
                .trim()
        }

        val colonIndex =
            response.indexOf(
                ':',
                markerIndex
            )

        if (colonIndex == -1) {
            return ""
        }

        val startQuote =
            response.indexOf(
                '"',
                colonIndex + 1
            )

        if (startQuote == -1) {
            return ""
        }

        val endQuote =
            response.indexOf(
                '"',
                startQuote + 1
            )

        if (endQuote == -1) {
            return ""
        }

        return response.substring(
            startQuote + 1,
            endQuote
        ).trim()
    }

    private fun buildNotification(
        text: String
    ): Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Tactical Commander"
            )
            .setContentText(text)
            .setSmallIcon(
                android.R.drawable.ic_menu_view
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    private fun createNotificationChannel() {

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Tactical Commander",
                NotificationManager.IMPORTANCE_LOW
            )

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    private fun stopCapture() {

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }

        imageReader = null

        try {
            mediaProjection?.unregisterCallback(
                projectionCallback
            )
        } catch (_: Exception) {
        }

        mediaProjection = null

        CommanderOverlay.hide(
            applicationContext
        )
    }

    override fun onDestroy() {

        stopCapture()

        serviceScope.cancel()

        captureThread?.quitSafely()

        captureThread = null
        captureHandler = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
