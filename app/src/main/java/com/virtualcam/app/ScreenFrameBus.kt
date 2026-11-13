package com.virtualcam.app

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

object ScreenFrameBus {

    @Volatile
    var onFrame: ((Bitmap) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var pending: Bitmap? = null
    private var posted = false

    fun send(bitmap: Bitmap) {
        synchronized(lock) {
            pending?.recycle()
            pending = bitmap

            if (posted) return
            posted = true
        }

        mainHandler.post {
            val frame = synchronized(lock) {
                val latest = pending
                pending = null
                posted = false
                latest
            }

            if (frame != null) {
                val callback = onFrame
                if (callback != null) {
                    callback.invoke(frame)
                } else {
                    frame.recycle()
                }
            }
        }
    }

    fun clear() {
        onFrame = null
        synchronized(lock) {
            pending?.recycle()
            pending = null
        }
    }
}
