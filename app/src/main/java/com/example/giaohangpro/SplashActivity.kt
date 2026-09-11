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

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(198, 90, 34)) }
        textureView = TextureView(this).apply {
            surfaceTextureListener = this@SplashActivity
            isOpaque = true
        }
        root.addView(textureView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) = startVideo(surfaceTexture)

    private fun startVideo(surfaceTexture: SurfaceTexture) {
        try {
            val mp = MediaPlayer()
            player = mp
            resources.openRawResourceFd(R.raw.loading_screen).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setSurface(Surface(surfaceTexture))
            mp.isLooping = false
            mp.setVolume(1f, 1f)
            mp.setOnPreparedListener { prepared ->
                textureView.post {
                    applyFitCenter(prepared.videoWidth, prepared.videoHeight)
                    prepared.start()
                }
            }
            mp.setOnVideoSizeChangedListener { _, w, h -> textureView.post { applyFitCenter(w, h) } }
            mp.setOnCompletionListener { openMain() }
            mp.setOnErrorListener { _, _, _ -> openMain(); true }
            mp.prepareAsync()
        } catch (_: Throwable) { openMain() }
    }

    // Fit toàn bộ video vào màn hình, giữ đúng tỉ lệ; không phóng/crop mất chữ hay hình.
    private fun applyFitCenter(videoWidth: Int, videoHeight: Int) {
        val viewW = textureView.width.toFloat()
        val viewH = textureView.height.toFloat()
        if (videoWidth <= 0 || videoHeight <= 0 || viewW <= 0f || viewH <= 0f) return

        // TextureView mặc định kéo buffer phủ kín view. Matrix dưới đây bù lại sự kéo giãn đó.
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val viewAspect = viewW / viewH
        val matrix = Matrix()
        val cx = viewW / 2f
        val cy = viewH / 2f
        if (videoAspect > viewAspect) {
            // Video rộng hơn màn hình: vừa theo chiều ngang, chừa nền trên/dưới nếu cần.
            val scaleY = viewAspect / videoAspect
            matrix.setScale(1f, scaleY, cx, cy)
        } else {
            // Video cao hơn màn hình: vừa theo chiều dọc, chừa nền hai bên nếu cần.
            val scaleX = videoAspect / viewAspect
            matrix.setScale(scaleX, 1f, cx, cy)
        }
        textureView.setTransform(matrix)
    }

    private fun openMain() {
        if (openedMain) return
        openedMain = true
        player?.release(); player = null
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        player?.let { textureView.post { applyFitCenter(it.videoWidth, it.videoHeight) } }
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { player?.release(); player = null; return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
    @Deprecated("Deprecated in Java") override fun onBackPressed() = Unit
}
