package com.virtualcam.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastFrameTimeMs = 0L

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "virtualcam_capture"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0

        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()

            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("VirtualCam")
                .setContentText("التقاط الشاشة قيد التشغيل")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            startCapture(resultCode, resultData)

        } catch (e: Exception) {
            Log.e("VirtualCam", "Screen capture failed", e)
            releaseCapture(true)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

        val screenProjection = manager.getMediaProjection(resultCode, resultData)
            ?: run {
                Log.e("VirtualCam", "MediaProjection returned null")
                stopSelf()
                return
            }
        projection = screenProjection

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )
        imageReader = reader

        reader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage()
                ?: return@setOnImageAvailableListener

            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastFrameTimeMs >= 100) {
                    lastFrameTimeMs = now

                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val paddedWidth = rowStride / pixelStride

                    val paddedBitmap = Bitmap.createBitmap(
                        paddedWidth,
                        height,
                        Bitmap.Config.ARGB_8888
                    )

                    paddedBitmap.copyPixelsFromBuffer(plane.buffer)

                    val frame = if (paddedWidth > width) {
                        Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                    } else {
                        paddedBitmap
                    }

                    if (frame !== paddedBitmap) {
                        paddedBitmap.recycle()
                    }

                    if (ScreenFrameBus.onFrame != null) {
                        ScreenFrameBus.send(frame)
                    } else {
                        frame.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e("VirtualCam", "Frame conversion failed", e)
            } finally {
                image.close()
            }
        }, null)

        screenProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseCapture(false)
                    stopSelf()
                }
            },
            null
        )

        virtualDisplay = screenProjection.createVirtualDisplay(
            "VirtualCamScreen",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VirtualCam Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun releaseCapture(stopProjection: Boolean) {
        val oldProjection = projection
        projection = null

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        if (stopProjection) {
            oldProjection?.stop()
        }
    }

    override fun onDestroy() {
        releaseCapture(true)
        super.onDestroy()
    }
}
