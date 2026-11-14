package com.virtualcam.app

import android.app.AppOpsManager
import android.provider.Settings
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout

class MainActivity : ComponentActivity() {

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        startService(Intent(this, FloatingWindowService::class.java))
    }



    private var player: ExoPlayer? = null
    private var selectedVideo: Uri? = null

    private lateinit var cameraView: PreviewView
    private lateinit var playerView: PlayerView
    private lateinit var contentFrame: FrameLayout
    private lateinit var status: TextView
    private lateinit var screenMessage: TextView
    private lateinit var screenView: ImageView
    private lateinit var compositeView: ImageView
    private var displayedScreenBitmap: android.graphics.Bitmap? = null
    private var latestCameraBitmap: android.graphics.Bitmap? = null
    private var displayedCompositeBitmap: android.graphics.Bitmap? = null
    private var screenFrameCount = 0
    private var lastScreenLogTime = 0L
    private lateinit var playButton: Button

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedVideo = uri
                player?.setMediaItem(MediaItem.fromUri(uri))
                player?.prepare()
                player?.play()
                playButton.text = "Ⅱ إيقاف الفيديو"
                status.text = "تم اختيار الفيديو"
            }
        }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCameraService()
            else status.text = "لم يتم السماح باستخدام الكاميرا"
        }

    private val screenPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                status.text = "بدأ التقاط الشاشة للاختبار"
                showMode("screen")
                screenMessage.text =
                    "التقاط الشاشة يعمل في الخلفية.\nعرض الصورة الملتقطة ودمجها مع الكاميرا سيضاف في المرحلة التالية."
            } else {
                status.text = "تم إلغاء التقاط الشاشة"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(13, 16, 28)
        window.navigationBarColor = Color.rgb(13, 16, 28)

        buildInterface()
        CameraPreviewBus.mainPreview = cameraView
        CameraPreviewBus.onMainPreviewChanged?.invoke(cameraView)

        ScreenFrameBus.onFrame = { bitmap ->
            if (!isFinishing && !isDestroyed) {
                screenFrameCount++
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastScreenLogTime >= 1000) {
                    android.util.Log.d("VirtualCam", "Screen frames received: $screenFrameCount")
                    screenFrameCount = 0
                    lastScreenLogTime = now
                }
                val oldBitmap = displayedScreenBitmap
                displayedScreenBitmap = bitmap
                screenView.setImageBitmap(bitmap)

                if (oldBitmap != null && oldBitmap !== bitmap && !oldBitmap.isRecycled) {
                    oldBitmap.recycle()
                }
                screenMessage.text = "يتم عرض الشاشة الملتقطة"
                renderComposite()
            } else {
                bitmap.recycle()
            }
        }

        CameraFrameBus.onFrame = { bitmap ->
            if (!isFinishing && !isDestroyed) {
                val oldBitmap = latestCameraBitmap
                latestCameraBitmap = bitmap

                if (oldBitmap != null && oldBitmap !== bitmap && !oldBitmap.isRecycled) {
                    oldBitmap.recycle()
                }

                renderComposite()
            } else {
                bitmap.recycle()
            }
        }

        initializePlayer()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraService()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildInterface() {
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(8))
            setBackgroundColor(Color.rgb(13, 16, 28))
        }

        val title = TextView(this).apply {
            text = "VirtualCam V2"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        main.addView(title)

        status = TextView(this).apply {
            text = "اختر وضع العرض"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(7), 0, dp(7))
        }
        main.addView(status)

        val modeScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addModeButton(modeRow, "كاميرا فقط") { showMode("camera") }
        addModeButton(modeRow, "شاشة فقط") { requestScreenCapture() }
        addModeButton(modeRow, "فيديو فقط") { showMode("video") }
        addModeButton(modeRow, "جنباً إلى جنب") { showMode("side") }
        addModeButton(modeRow, "فوق وتحت") { showMode("vertical") }
        addModeButton(modeRow, "نافذة صغيرة") { showMode("pip") }
        addModeButton(modeRow, "كاميرا + شاشة") { showMode("composite") }
        addModeButton(modeRow, "تفعيل النافذة العائمة") { requestOverlayPermission() }

        modeScroll.addView(modeRow)
        main.addView(modeScroll)

        contentFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        cameraView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        playerView = PlayerView(this).apply {
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }

        screenMessage = TextView(this).apply {
            text = "اختر وضع العرض"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        screenView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }

        compositeView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }

        main.addView(
            contentFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        )

        val importButton = makeButton("＋ اختيار فيديو")
        importButton.setOnClickListener {
            videoPicker.launch("video/*")
        }
        main.addView(importButton)

        playButton = makeButton("▶ تشغيل / إيقاف الفيديو")
        playButton.setOnClickListener {
            val p = player
            if (p == null || selectedVideo == null) {
                Toast.makeText(this, "اختر فيديو أولاً", Toast.LENGTH_SHORT).show()
            } else if (p.isPlaying) {
                p.pause()
                playButton.text = "▶ تشغيل الفيديو"
            } else {
                p.play()
                playButton.text = "Ⅱ إيقاف الفيديو"
            }
        }
        main.addView(playButton)

        val captureButton = makeButton("بدء التقاط الشاشة")
        captureButton.setOnClickListener { requestScreenCapture() }
        main.addView(captureButton)

        val stopCaptureButton = makeButton("إيقاف التقاط الشاشة")
        stopCaptureButton.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            status.text = "تم إيقاف التقاط الشاشة"
        }
        main.addView(stopCaptureButton)

        setContentView(main)
        showMode("side")
    }

    private fun addModeButton(
        row: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(55, 62, 85))
            setOnClickListener { action() }
        }

        row.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(46)
            ).apply {
                marginEnd = dp(5)
            }
        )
    }

    private fun showMode(mode: String) {
        contentFrame.removeAllViews()

        val full = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        when (mode) {
            "camera" -> {
                contentFrame.addView(cameraView, full)
                status.text = "وضع الكاميرا فقط"
            }

            "video" -> {
                contentFrame.addView(playerView, full)
                status.text = "وضع الفيديو فقط"
            }

            "side" -> {
                contentFrame.addView(
                    cameraView,
                    FrameLayout.LayoutParams(
                        0,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.START
                    ).apply {
                        width = contentFrame.width / 2
                    }
                )

                contentFrame.post {
                    contentFrame.removeAllViews()
                    contentFrame.addView(
                        cameraView,
                        FrameLayout.LayoutParams(
                            contentFrame.width / 2,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            Gravity.START
                        )
                    )
                    contentFrame.addView(
                        playerView,
                        FrameLayout.LayoutParams(
                            contentFrame.width / 2,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            Gravity.END
                        )
                    )
                }
                status.text = "الكاميرا والفيديو جنباً إلى جنب"
            }

            "vertical" -> {
                contentFrame.post {
                    contentFrame.removeAllViews()
                    contentFrame.addView(
                        cameraView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            contentFrame.height / 2,
                            Gravity.TOP
                        )
                    )
                    contentFrame.addView(
                        playerView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            contentFrame.height / 2,
                            Gravity.BOTTOM
                        )
                    )
                }
                status.text = "الكاميرا والفيديو فوق وتحت"
            }

            "composite" -> {
                contentFrame.addView(compositeView, full)
                status.text = "الكاميرا والشاشة مقسمتان إلى نصفين"
                renderComposite()
            }

            "screen" -> {
                contentFrame.addView(screenView, full)
                status.text = "عرض الشاشة الملتقطة"
            }

            "pip" -> {
                contentFrame.addView(playerView, full)
                contentFrame.addView(
                    cameraView,
                    FrameLayout.LayoutParams(
                        dp(125),
                        dp(175),
                        Gravity.TOP or Gravity.END
                    ).apply {
                        setMargins(dp(8), dp(8), dp(8), dp(8))
                    }
                )
                status.text = "الفيديو كبير والكاميرا نافذة صغيرة"
            }

            else -> {
                contentFrame.addView(screenMessage, full)
            }
        }
    }

    private fun renderComposite() {
        if (!::compositeView.isInitialized || compositeView.parent == null) return

        val camera = latestCameraBitmap ?: return
        val screen = displayedScreenBitmap ?: return

        if (camera.isRecycled || screen.isRecycled) return

        val output = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        drawFitCenter(canvas, camera, Rect(0, 0, 640, 720), paint)
        drawFitCenter(canvas, screen, Rect(640, 0, 1280, 720), paint)

        val old = displayedCompositeBitmap
        displayedCompositeBitmap = output
        compositeView.setImageBitmap(output)

        if (old != null && old !== output && !old.isRecycled) {
            old.recycle()
        }
    }

    private fun drawFitCenter(
        canvas: Canvas,
        bitmap: Bitmap,
        area: Rect,
        paint: Paint
    ) {
        val scale = minOf(
            area.width().toFloat() / bitmap.width,
            area.height().toFloat() / bitmap.height
        )

        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()

        val left = area.left + (area.width() - width) / 2
        val top = area.top + (area.height() - height) / 2

        val destination = Rect(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE)
                as android.media.projection.MediaProjectionManager
        screenPermission.launch(manager.createScreenCaptureIntent())
    }

    private fun startCameraService() {
        try {
            val intent = Intent(this, CameraForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }

            status.text = "جاري تشغيل خدمة الكاميرا"
        } catch (e: Exception) {
            status.text = "تعذر تشغيل خدمة الكاميرا: ${e.message}"
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    private fun makeButton(label: String) =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(48, 57, 82))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(45)
            ).apply {
                bottomMargin = dp(5)
            }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        ScreenFrameBus.clear()
        CameraFrameBus.onFrame = null

        screenView.setImageDrawable(null)
        compositeView.setImageDrawable(null)

        latestCameraBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        latestCameraBitmap = null

        displayedCompositeBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        displayedCompositeBitmap = null

        displayedScreenBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        displayedScreenBitmap = null

        playerView.player = null
        player?.release()
        player = null
        if (CameraPreviewBus.mainPreview === cameraView) {
            CameraPreviewBus.mainPreview = null
        }
        super.onDestroy()
    }
}
