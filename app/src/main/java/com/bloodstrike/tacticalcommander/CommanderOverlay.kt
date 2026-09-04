package com.bloodstrike.tacticalcommander

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

object CommanderOverlay {

    private var overlayView: TextView? = null

    fun show(
        context: Context,
        command: String
    ) {

        val manager =
            context.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        val existing =
            overlayView

        if (existing != null) {

            existing.post {
                existing.text =
                    "TACTICAL COMMAND\n$command"
            }

            return
        }

        val text =
            TextView(context).apply {

                this.text =
                    "TACTICAL COMMAND\n$command"

                textSize = 16f

                setTextColor(
                    Color.WHITE
                )

                setBackgroundColor(
                    Color.argb(
                        210,
                        0,
                        0,
                        0
                    )
                )

                setPadding(
                    24,
                    16,
                    24,
                    16
                )

                gravity =
                    Gravity.CENTER
            }

        val type =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or Gravity.CENTER_HORIZONTAL

        params.y = 100

        try {

            manager.addView(
                text,
                params
            )

            overlayView = text

        } catch (_: Exception) {
        }
    }

    fun hide(
        context: Context
    ) {

        val view =
            overlayView
                ?: return

        val manager =
            context.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        try {
            manager.removeView(view)
        } catch (_: Exception) {
        }

        overlayView = null
    }
}
