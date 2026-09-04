package com.bloodstrike.tacticalcommander

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 1001
    }

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Blood Strike Tactical Commander"
            textSize = 24f
        }

        statusText = TextView(this).apply {
            text = "Status: Ready"
            textSize = 18f
            setPadding(0, 40, 0, 40)
        }

        val button = Button(this).apply {
            text = "Start Screen Capture"
        }

        button.setOnClickListener {
            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager

            statusText.text = "Status: Waiting for permission..."

            startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_SCREEN_CAPTURE
            )
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(button)

        setContentView(layout)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_SCREEN_CAPTURE) {
            return
        }

        if (resultCode == RESULT_OK && data != null) {

            statusText.text = "Status: Starting live capture..."

            val serviceIntent =
                Intent(this, CaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }

            startForegroundService(serviceIntent)

            statusText.text = "Status: LIVE CAPTURE ACTIVE"
        } else {
            statusText.text = "Status: Capture permission denied"
        }
    }
}
