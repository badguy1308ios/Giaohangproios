package com.example.giaohangpro.vtman

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class VtmanOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private var selector: View? = null
    private var marker: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val s = VtmanQueueController.snapshot()
            status.text = "Đã xử lý ${s.processed}/${s.total} · Lấy được ${s.written} · Bỏ qua ${s.skipped}\n${s.status}"
            handler.postDelayed(this, 450)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::panel.isInitialized) showPanel()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        clearSelectionUi()
        if (::panel.isInitialized) runCatching { windowManager.removeView(panel) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun showPanel() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            VtmanQueueController.fail("Chưa cấp quyền Hiển thị trên ứng dụng khác cho Giao Hàng Pro")
            stopSelf(); return
        }
        instance = this
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
            background = GradientDrawable().apply { setColor(Color.rgb(194, 78, 20)); cornerRadius = 24f }
        }
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; maxLines = 6 }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
        row.addView(button("Chạy") { VtmanQueueController.service?.begin() ?: VtmanQueueController.serviceUnavailable() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("Dừng") { VtmanQueueController.service?.stop() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("Tắt") { VtmanQueueController.service?.stop(); stopSelf() }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(status); panel.addView(row)
        makeDraggable(panel)
        val params = WindowManager.LayoutParams(
            760, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = 140 }
        runCatching { windowManager.addView(panel, params) }
            .onSuccess { handler.post(refresh) }
            .onFailure { VtmanQueueController.fail("Không mở được popup VTMan: ${it.message}") }
    }

    private fun makeDraggable(view: View) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        view.setOnTouchListener { _, e ->
            val p = view.layoutParams as WindowManager.LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = p.x; sy = p.y; true }
                MotionEvent.ACTION_MOVE -> { p.x = sx + (e.rawX-dx).toInt(); p.y = sy + (e.rawY-dy).toInt(); windowManager.updateViewLayout(view,p); true }
                else -> false
            }
        }
    }

    private fun showSelector(onPoint: (Float, Float) -> Unit) {
        clearSelectionUi()
        val v = View(this).apply {
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    val x=e.rawX; val y=e.rawY
                    runCatching { windowManager.removeView(this) }
                    selector=null
                    showMarker(x,y)
                    onPoint(x,y)
                }
                true
            }
        }
        selector=v
        windowManager.addView(v, WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT))
    }

    private fun showMarker(x: Float, y: Float) {
        val d=(14*resources.displayMetrics.density).toInt()
        val v=View(this).apply { background=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(Color.RED); setStroke(2,Color.WHITE) } }
        marker=v
        val p=WindowManager.LayoutParams(d,d,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START; this.x=(x-d/2f).toInt(); this.y=(y-d/2f).toInt()
        }
        windowManager.addView(v,p)
    }

    private fun clearSelectionUi() {
        selector?.let { runCatching { windowManager.removeView(it) } }; selector=null
        marker?.let { runCatching { windowManager.removeView(it) } }; marker=null
    }

    companion object {
        @Volatile private var instance: VtmanOverlayService? = null
        fun requestCallPointSelection(onPoint: (Float, Float) -> Unit): Boolean {
            val s=instance ?: return false
            s.handler.post { s.showSelector(onPoint) }
            return true
        }
        fun clearCallPointUi() { instance?.handler?.post { instance?.clearSelectionUi() } }
    }
}
