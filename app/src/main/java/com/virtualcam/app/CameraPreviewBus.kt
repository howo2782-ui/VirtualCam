package com.virtualcam.app

import androidx.camera.view.PreviewView

object CameraPreviewBus {

    @Volatile
    var mainPreview: PreviewView? = null

    @Volatile
    var overlayPreview: PreviewView? = null

    @Volatile
    var onPreviewRequested: ((PreviewView) -> Unit)? = null

    @Volatile
    var onPreviewReleased: (() -> Unit)? = null

    fun requestPreview(view: PreviewView) {
        overlayPreview = view
        onPreviewRequested?.invoke(view)
    }

    fun clearPreview(view: PreviewView) {
        if (overlayPreview === view) {
            overlayPreview = null
            onPreviewReleased?.invoke()
        }
    }
}
