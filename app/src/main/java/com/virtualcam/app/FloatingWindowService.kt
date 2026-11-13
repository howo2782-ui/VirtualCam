package com.virtualcam.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import kotlin.math.abs

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: FrameLayout? = null
    private var overlayPreview: PreviewView? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val windowType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            300,
            400,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 150
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY

                    if (abs(dx) > 5 || abs(dy) > 5) {
                        moved = true
                    }

                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) stopSelf()
                    true
                }

                else -> true
            }
        }

        floatingView = root
        overlayPreview = preview
        windowManager.addView(root, params)

        CameraPreviewBus.requestPreview(preview)
    }

    override fun onDestroy() {
        overlayPreview?.let {
            CameraPreviewBus.clearPreview(it)
        }

        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("VirtualCam", "Window removal failed", e)
            }
        }

        overlayPreview = null
        floatingView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
