package com.bloodstrike.tacticalcommander

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.view.ViewGroup

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
    }

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(8, 11, 15))
        }

        val title = TextView(this).apply {
            text = "TACTICAL COMMANDER"
            textSize = 26f
            setTextColor(Color.rgb(232, 237, 242))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Native Android capture prototype"
            textSize = 14f
            setTextColor(Color.rgb(137, 147, 158))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 36)
        }

        statusText = TextView(this).apply {
            text = "STATUS: OFFLINE"
            textSize = 16f
            setTextColor(Color.rgb(137, 147, 158))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        startButton = Button(this).apply {
            text = "START COMMANDER"
            setOnClickListener { requestScreenCapture() }
        }

        stopButton = Button(this).apply {
            text = "STOP COMMANDER"
            isEnabled = false
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                setOffline()
            }
        }

        root.addView(title, match())
        root.addView(subtitle, match())
        root.addView(statusText, match())
        root.addView(startButton, match())
        root.addView(stopButton, match())

        setContentView(root)
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Use Activity Result API in a later UI pass")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            statusText.text = "STATUS: CAPTURING"
            statusText.setTextColor(Color.rgb(108, 219, 139))
            startButton.isEnabled = false
            stopButton.isEnabled = true
        } else {
            setOffline()
        }
    }

    private fun setOffline() {
        statusText.text = "STATUS: OFFLINE"
        statusText.setTextColor(Color.rgb(137, 147, 158))
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }

    private fun match(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
}
