package com.bloodstrike.tacticalcommander

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val requestScreenCapture = 1001

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

        val button = Button(this).apply {
            text = "Start Screen Capture"
        }

        button.setOnClickListener {
            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            startActivityForResult(
                manager.createScreenCaptureIntent(),
                requestScreenCapture
            )
        }

        layout.addView(title)
        layout.addView(button)

        setContentView(layout)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == requestScreenCapture &&
            resultCode == RESULT_OK &&
            data != null
        ) {
            val serviceIntent = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }

            startForegroundService(serviceIntent)
        }
    }
}
