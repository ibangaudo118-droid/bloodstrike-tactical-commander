package com.bloodstrike.tacticalcommander

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
    }

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun buildUi() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 70, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Blood Strike Tactical Commander"
            textSize = 24f
        }

        val description = TextView(this).apply {
            text = "Live AI tactical coach"
            textSize = 16f
            setPadding(0, 20, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Status: Ready"
            textSize = 18f
            setPadding(0, 20, 0, 30)
        }

        val overlayButton = Button(this).apply {
            text = "Enable Floating Commander"
        }

        overlayButton.setOnClickListener {
            openOverlaySettings()
        }

        val startButton = Button(this).apply {
            text = "START COMMANDER"
        }

        startButton.setOnClickListener {
            startCommander()
        }

        val stopButton = Button(this).apply {
            text = "STOP COMMANDER"
        }

        stopButton.setOnClickListener {

            stopService(
                Intent(
                    this,
                    CaptureService::class.java
                )
            )

            statusText.text =
                "Status: Commander stopped"
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(statusText)
        layout.addView(overlayButton)
        layout.addView(startButton)
        layout.addView(stopButton)

        setContentView(layout)
    }

    private fun startCommander() {

        if (!Settings.canDrawOverlays(this)) {

            Toast.makeText(
                this,
                "Enable Floating Commander first",
                Toast.LENGTH_LONG
            ).show()

            openOverlaySettings()
            return
        }

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        statusText.text =
            "Status: Requesting screen permission..."

        val captureIntent =
            manager.createScreenCaptureIntent()

        startActivityForResult(
            captureIntent,
            REQUEST_SCREEN_CAPTURE
        )
    }

    private fun openOverlaySettings() {

        if (Settings.canDrawOverlays(this)) {

            Toast.makeText(
                this,
                "Floating Commander is already enabled",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse(
                "package:$packageName"
            )
        )

        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode !=
            REQUEST_SCREEN_CAPTURE
        ) {
            return
        }

        if (
            resultCode != RESULT_OK ||
            data == null
        ) {

            statusText.text =
                "Status: Screen permission denied"

            Toast.makeText(
                this,
                "Screen capture permission was not granted",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        statusText.text =
            "Status: Screen permission granted"

        try {

            val serviceIntent =
                Intent(
                    this,
                    CaptureService::class.java
                )

            serviceIntent.putExtra(
                "resultCode",
                resultCode
            )

            serviceIntent.putExtra(
                "data",
                data
            )

            statusText.text =
                "Status: Starting live commander..."

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                startForegroundService(
                    serviceIntent
                )

            } else {

                startService(
                    serviceIntent
                )
            }

            statusText.text =
                "Status: LIVE COMMANDER ACTIVE"

        } catch (e: Exception) {

            statusText.text =
                "START ERROR: ${e.javaClass.simpleName}"

            Toast.makeText(
                this,
                "Could not start commander: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
