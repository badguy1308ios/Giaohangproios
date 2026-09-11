package com.example.giaohangpro

import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : ComponentActivity(), TextureView.SurfaceTextureListener {
    private var openedMain = false
    private var player: MediaPlayer? = null
    private lateinit var textureView: TextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(198, 90, 34))
        }

        textureView = TextureView(this).apply {
            surfaceTextureListener = this@SplashActivity
            isOpaque = true
        }
        root.addView(
            textureView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        startVideo(surfaceTexture)
    }

    private fun startVideo(surfaceTexture: SurfaceTexture) {
        try {
            val mp = MediaPlayer()
            player = mp
            val afd = resources.openRawResourceFd(R.raw.loading_screen)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setSurface(Surface(surfaceTexture))
            mp.isLooping = false
            mp.setVolume(1f, 1f)

            mp.setOnPreparedListener { prepared ->
                applyCenterCrop(prepared.videoWidth, prepared.videoHeight)
                prepared.start()
            }
            mp.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                applyCenterCrop(videoWidth, videoHeight)
            }
            mp.setOnCompletionListener { openMain() }
            mp.setOnErrorListener { _, _, _ ->
                openMain()
                true
            }
            mp.prepareAsync()
        } catch (_: Throwable) {
            openMain()
        }
    }

    private fun applyCenterCrop(videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0 || textureView.width <= 0 || textureView.height <= 0) return

        val viewW = textureView.width.toFloat()
        val viewH = textureView.height.toFloat()
        val videoW = videoWidth.toFloat()
        val videoH = videoHeight.toFloat()
        val scale = maxOf(viewW / videoW, viewH / videoH)
        val scaledW = videoW * scale
        val scaledH = videoH * scale
        val dx = (viewW - scaledW) / 2f
        val dy = (viewH - scaledH) / 2f

        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        textureView.setTransform(matrix)
    }

    private fun openMain() {
        if (openedMain) return
        openedMain = true
        player?.release()
        player = null
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        player?.let { applyCenterCrop(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        player?.release()
        player = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Không thoát app trong lúc video khởi động đang chạy.
    }
}
