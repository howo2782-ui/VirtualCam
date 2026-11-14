package com.virtualcam.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService

class CameraForegroundService : LifecycleService() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraPreview: Preview? = null

    override fun onCreate() {
        super.onCreate()

        startCameraForeground()

        CameraPreviewBus.onPreviewRequested = { view ->
            cameraPreview?.surfaceProvider = view.surfaceProvider
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

                preview.surfaceProvider =
                    CameraPreviewBus.overlayPreview?.surfaceProvider
                        ?: CameraPreviewBus.mainPreview?.surfaceProvider

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview
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
        CameraPreviewBus.onPreviewReleased = null

        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraPreview = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
