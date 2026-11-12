package com.virtualcam.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var selectedVideo: Uri? = null
    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView
    private lateinit var playButton: Button
    private var looping = true

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedVideo = uri
                playVideo(uri)
                statusText.text = "تم اختيار الفيديو بنجاح"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 16, 28)
        window.navigationBarColor = Color.rgb(13, 16, 28)
        window.decorView.systemUiVisibility = 0
        buildScreen()
        initializePlayer()
    }

    private fun buildScreen() {
        val background = Color.rgb(13, 16, 28)
        val accent = Color.rgb(108, 92, 231)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            setBackgroundColor(background)
        }

        val title = TextView(this).apply {
            text = "VirtualCam"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        main.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "كاميرتك الافتراضية — بدون Root"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(18))
        }
        main.addView(subtitle, matchWrap())

        playerView = PlayerView(this).apply {
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }
        main.addView(
            playerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                bottomMargin = dp(14)
            }
        )

        statusText = TextView(this).apply {
            text = "اختر فيديو من هاتفك للبدء"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        main.addView(statusText, matchWrap())

        val importButton = makeButton("＋  اختيار فيديو", accent)
        importButton.setOnClickListener { videoPicker.launch("video/*") }
        main.addView(importButton, buttonParams())

        playButton = makeButton("▶  تشغيل", Color.rgb(42, 48, 68))
        playButton.setOnClickListener {
            val p = player
            if (p == null || selectedVideo == null) {
                Toast.makeText(this, "اختر فيديو أولاً", Toast.LENGTH_SHORT).show()
            } else if (p.isPlaying) {
                p.pause()
                playButton.text = "▶  تشغيل"
            } else {
                p.play()
                playButton.text = "Ⅱ  إيقاف مؤقت"
            }
        }
        main.addView(playButton, buttonParams())

        val loopButton = makeButton("تكرار الفيديو: مفعّل", Color.rgb(42, 48, 68))
        loopButton.setOnClickListener {
            looping = !looping
            player?.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            loopButton.text = if (looping) "تكرار الفيديو: مفعّل" else "تكرار الفيديو: متوقف"
        }
        main.addView(loopButton, buttonParams())

        val shareButton = makeButton("مشاركة الفيديو إلى TikTok", Color.rgb(0, 160, 150))
        shareButton.setOnClickListener { shareVideo() }
        main.addView(shareButton, buttonParams())

        val note = TextView(this).apply {
            text = "ملاحظة: هذه النسخة تشغّل الفيديو داخل VirtualCam وتشاركه إلى TikTok. لا تستبدل كاميرا TikTok مباشرة."
            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        main.addView(note, matchWrap())

        setContentView(main)
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        playButton.text = "▶  تشغيل"
                    }
                }
            })
        }
    }

    private fun playVideo(uri: Uri) {
        val exo = player ?: return
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.play()
        playButton.text = "Ⅱ  إيقاف مؤقت"
    }

    private fun shareVideo() {
        val uri = selectedVideo
        if (uri == null) {
            Toast.makeText(this, "اختر فيديو أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "مشاركة الفيديو"))
    }

    private fun makeButton(label: String, color: Int) =
        Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
        }

    private fun buttonParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
    ).apply { bottomMargin = dp(9) }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
