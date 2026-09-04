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
        private const val ANALYSIS_INTERVAL_MS = 1000L
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

        return try {

            startCommanderSafely(intent)

            START_NOT_STICKY

        } catch (e: Exception) {

            reportError(
                "START ERROR: ${errorText(e)}"
            )

            stopCapture()
            stopSelf()

            START_NOT_STICKY
        }
    }

    private fun startCommanderSafely(
        intent: Intent?
    ) {

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

            reportError(
                "START ERROR: Missing screen capture permission data"
            )

            stopSelf()

            return
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

                reportError(
                    "CAPTURE ERROR: MediaProjection unavailable"
                )

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

        if (virtualDisplay == null) {

            reportError(
                "CAPTURE ERROR: Virtual display could not be created"
            )

            stopCapture()
            stopSelf()

            return
        }

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

            } catch (e: Exception) {

                reportError(
                    "FRAME ERROR: ${errorText(e)}"
                )

                null
            }

        if (image == null) {
            return
        }

        try {

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

                    updateNotification(
                        "LIVE • Signing in to AI..."
                    )

                    val authResult =
                        VisionCommander.signIn()

                    if (
                        authResult.isFailure
                    ) {

                        reportError(
                            "AI AUTH ERROR: ${
                                errorText(
                                    authResult.exceptionOrNull()
                                )
                            }"
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

                    if (
                        result.isSuccess
                    ) {

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

                        } else {

                            updateNotification(
                                "LIVE • AI returned no command"
                            )
                        }

                    } else {

                        reportError(
                            "AI REQUEST ERROR: ${
                                errorText(
                                    result.exceptionOrNull()
                                )
                            }"
                        )
                    }

                } catch (e: Exception) {

                    reportError(
                        "AI ERROR: ${errorText(e)}"
                    )

                } finally {

                    analysisRunning.set(false)
                }
            }

        } catch (e: Exception) {

            analysisRunning.set(false)

            reportError(
                "FRAME PROCESS ERROR: ${errorText(e)}"
            )

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

        } catch (e: Exception) {

            reportError(
                "BITMAP ERROR: ${errorText(e)}"
            )

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

        } catch (e: Exception) {

            reportError(
                "JPEG ERROR: ${errorText(e)}"
            )

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

        if (
            markerIndex == -1
        ) {

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

        if (
            colonIndex == -1
        ) {

            return ""
        }

        val startQuote =
            response.indexOf(
                '"',
                colonIndex + 1
            )

        if (
            startQuote == -1
        ) {

            return ""
        }

        val endQuote =
            response.indexOf(
                '"',
                startQuote + 1
            )

        if (
            endQuote == -1
        ) {

            return ""
        }

        return response.substring(
            startQuote + 1,
            endQuote
        ).trim()
    }

    private fun errorText(
        error: Throwable?
    ): String {

        if (error == null) {
            return "Unknown error"
        }

        val message =
            error.message?.trim()

        return if (
            !message.isNullOrEmpty()
        ) {

            "${error.javaClass.simpleName}: $message"

        } else {

            error.javaClass.simpleName
        }
    }

    private fun reportError(
        text: String
    ) {

        updateNotification(
            text.take(180)
        )
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

        try {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.notify(
                NOTIFICATION_ID,
                buildNotification(text)
            )

        } catch (_: Exception) {
        }
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
