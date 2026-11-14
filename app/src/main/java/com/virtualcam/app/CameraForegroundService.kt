package com.virtualcam.app

import android.app.Notification
import android.graphics.Bitmap
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors

class CameraForegroundService : LifecycleService() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraPreview: Preview? = null
    private var cameraAnalysis: ImageAnalysis? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var lastCameraFrameTimeMs = 0L

    override fun onCreate() {
        super.onCreate()

        startCameraForeground()

        CameraPreviewBus.onPreviewRequested = { view ->
            cameraPreview?.surfaceProvider = view.surfaceProvider
        }

        CameraPreviewBus.onMainPreviewChanged = { view ->
            if (CameraPreviewBus.overlayPreview == null) {
                cameraPreview?.surfaceProvider = view.surfaceProvider
            }
        }

        CameraPreviewBus.onPreviewReleased = {
            cameraPreview?.surfaceProvider =
                CameraPreviewBus.mainPreview?.surfaceProvider
        }

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build()
                cameraPreview = preview

                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                cameraAnalysis = analysis

                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()

                        if (now - lastCameraFrameTimeMs >= 100) {
                            lastCameraFrameTimeMs = now

                            val plane = image.planes[0]
                            val pixelStride = plane.pixelStride
                            val paddedWidth = plane.rowStride / pixelStride

                            val paddedBitmap = Bitmap.createBitmap(
                                paddedWidth,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )

                            val buffer = plane.buffer
                            buffer.rewind()
                            paddedBitmap.copyPixelsFromBuffer(buffer)

                            val frame = if (paddedWidth > image.width) {
                                Bitmap.createBitmap(
                                    paddedBitmap,
                                    0,
                                    0,
                                    image.width,
                                    image.height
                                )
                            } else {
                                paddedBitmap
                            }

                            if (frame !== paddedBitmap) {
                                paddedBitmap.recycle()
                            }

                            if (CameraFrameBus.onFrame != null) {
                                CameraFrameBus.send(frame)
                            } else {
                                frame.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "VirtualCam",
                            "Camera frame conversion failed",
                            e
                        )
                    } finally {
                        image.close()
                    }
                }

                preview.surfaceProvider =
                    CameraPreviewBus.overlayPreview?.surfaceProvider
                        ?: CameraPreviewBus.mainPreview?.surfaceProvider

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "VirtualCam",
                    "Camera service failed",
                    e
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCameraForeground() {
        val channelId = "virtualcam_camera"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VirtualCam Camera",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("VirtualCam")
                .setContentText("الكاميرا تعمل في الخلفية")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onDestroy() {
        CameraPreviewBus.onPreviewRequested = null
        CameraPreviewBus.onMainPreviewChanged = null
        CameraPreviewBus.onPreviewReleased = null

        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraPreview = null
        cameraAnalysis?.clearAnalyzer()
        cameraAnalysis = null
        analysisExecutor.shutdown()
        CameraFrameBus.clear()

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
