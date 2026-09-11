package com.example.giaohangpro

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : ComponentActivity() {
    private var openedMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val video = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        root.addView(video)
        setContentView(root)

        val uri = Uri.parse("android.resource://$packageName/${R.raw.loading_screen}")
        video.setVideoURI(uri)
        video.setOnPreparedListener { player ->
            player.isLooping = false
            video.post {
                // Center-crop: luôn phủ kín màn hình, giữ đúng tỉ lệ video, không kéo méo.
                val vw = player.videoWidth.toFloat().coerceAtLeast(1f)
                val vh = player.videoHeight.toFloat().coerceAtLeast(1f)
                val sw = root.width.toFloat().coerceAtLeast(1f)
                val sh = root.height.toFloat().coerceAtLeast(1f)
                val videoRatio = vw / vh
                val screenRatio = sw / sh
                video.scaleX = 1f
                video.scaleY = 1f
                if (videoRatio > screenRatio) {
                    video.scaleX = videoRatio / screenRatio
                } else {
                    video.scaleY = screenRatio / videoRatio
                }
                video.start()
            }
        }
        video.setOnCompletionListener { openMain() }
        video.setOnErrorListener { _, _, _ ->
            openMain()
            true
        }
    }

    private fun openMain() {
        if (openedMain) return
        openedMain = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() {
        // Không thoát app trong lúc loading; bỏ qua nút Back cho tới khi video kết thúc.
    }
}
