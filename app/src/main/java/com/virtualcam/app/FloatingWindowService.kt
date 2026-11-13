package com.virtualcam.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: TextView? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = TextView(this).apply {
            text = "VirtualCam\nاضغط هنا للإغلاق"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            setPadding(24, 20, 24, 20)
            setOnClickListener { stopSelf() }
        }

        val windowType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 150
        }

        floatingView = view
        windowManager.addView(view, params)
    }

    override fun onDestroy() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("VirtualCam", "Window removal failed", e)
            }
        }
        floatingView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
