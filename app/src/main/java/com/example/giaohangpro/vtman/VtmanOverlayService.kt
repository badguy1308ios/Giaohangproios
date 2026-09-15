package com.example.giaohangpro.vtman

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
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
    private val connectionWait = VtmanConnectionWait()
    private var selectionGeneration = 0

    private fun accessibilityEnabled(): Boolean {
        val expected = ComponentName(this, VtmanAccessibilityService::class.java)
        // Read the user's enabled setting, independently of the live service binding.
        // Only Android/user settings can enable or bind this service.
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun requestStart() {
        if (VtmanQueueController.nextWaybill() == null) {
            VtmanQueueController.fail("Hãy nạp danh sách MVĐ trước")
            return
        }
        connectionWait.start(SystemClock.elapsedRealtime())
        checkPendingStart()
    }

    private fun checkPendingStart() {
        when (connectionWait.poll(accessibilityEnabled(), VtmanQueueController.service != null, SystemClock.elapsedRealtime())) {
            VtmanConnectionWait.Result.READY -> VtmanQueueController.service?.begin()
            VtmanConnectionWait.Result.DISABLED -> VtmanQueueController.fail("Trợ năng Giao Hàng Pro chưa bật. Mở Trợ năng để cấp quyền.")
            VtmanConnectionWait.Result.WAITING -> VtmanQueueController.report("Trợ năng đã bật · đang chờ Android kết nối (tối đa 8 giây)…")
            VtmanConnectionWait.Result.TIMED_OUT -> VtmanQueueController.fail("Trợ năng đã bật nhưng Android chưa kết nối dịch vụ. Bấm Chạy để thử lại; nếu vẫn lỗi, kiểm tra dịch vụ trong Trợ năng.")
            VtmanConnectionWait.Result.IDLE -> Unit
        }
    }

    private fun cancelStart() {
        connectionWait.cancel()
        clearSelectionUi()
        VtmanQueueController.service?.stop()
    }

    private val refresh = object : Runnable {
        override fun run() {
            checkPendingStart()
            val s = VtmanQueueController.snapshot()
            val connection = when {
                !accessibilityEnabled() -> "Trợ năng: chưa bật"
                VtmanQueueController.service == null -> "Trợ năng: đã bật, chưa kết nối"
                else -> "Trợ năng: đã kết nối"
            }
            status.text = "$connection\nĐã xử lý ${s.processed}/${s.total} · Lấy được ${s.written} · Bỏ qua ${s.skipped}\n${s.status}"
            handler.postDelayed(this, 450)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::panel.isInitialized) showPanel()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelStart()
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
        row.addView(button("Chạy") { requestStart() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("Dừng") { cancelStart(); VtmanQueueController.report("Đã dừng") }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("Tắt") { cancelStart(); stopSelf() }, LinearLayout.LayoutParams(0, -2, 1f))
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
                    try {
                        showMarker(x,y)
                        onPoint(x,y)
                    } catch (e: RuntimeException) { selectionFailed(e) }
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
        selectionGeneration++
        selector?.let { runCatching { windowManager.removeView(it) } }; selector=null
        marker?.let { runCatching { windowManager.removeView(it) } }; marker=null
    }

    private fun selectionFailed(error: RuntimeException) {
        clearSelectionUi()
        val service = VtmanQueueController.service
        if (service != null) service.reportFailure(error)
        else VtmanQueueController.fail("Không mở được điểm chọn nút gọi. Kiểm tra quyền Hiển thị trên ứng dụng khác rồi thử lại.")
    }

    companion object {
        @Volatile private var instance: VtmanOverlayService? = null
        fun requestCallPointSelection(onPoint: (Float, Float) -> Unit): Boolean {
            val s=instance ?: return false
            val generation = s.selectionGeneration
            s.handler.post {
                if (instance === s && generation == s.selectionGeneration) {
                    try { s.showSelector(onPoint) }
                    catch (e: RuntimeException) { s.selectionFailed(e) }
                }
            }
            return true
        }
        fun clearCallPointUi() { instance?.clearSelectionUi() }
    }
}
