package com.example.giaohangpro.vtman

import android.app.Service
import android.accessibilityservice.AccessibilityServiceInfo
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
import android.view.accessibility.AccessibilityManager
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
    private lateinit var runPauseButton: Button
    private lateinit var skipButton: Button
    private var selector: View? = null
    private var marker: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val connectionWait = VtmanConnectionWait()
    private var selectionGeneration = 0
    private var autoExportLocked = false

    private fun accessibilityEnabled(): Boolean {
        // Một service đang kết nối thật luôn được xem là đã bật. Tránh trường hợp
        // HiOS trả Settings.Secure cũ khiến popup báo sai "chưa bật".
        if (VtmanQueueController.service != null) return true

        val expected = ComponentName(this, VtmanAccessibilityService::class.java)
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledByManager = runCatching {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo.serviceInfo
                    ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
                }
        }.getOrDefault(false)
        if (enabledByManager) return true

        // Fallback cho ROM không trả danh sách đầy đủ qua AccessibilityManager.
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }

    private fun requestStart() {
        val service = VtmanQueueController.service
        if (service?.isPaused() == true) {
            service.resume()
            return
        }
        if (VtmanQueueController.nextWaybill() == null && !VtmanQueueController.hasPendingAutoExport()) {
            VtmanQueueController.fail("Hãy nạp danh sách MVĐ trước")
            return
        }
        connectionWait.start(SystemClock.elapsedRealtime())
        checkPendingStart()
    }

    private fun toggleRunPause() {
        val service = VtmanQueueController.service
        when {
            service?.hasPartialAutoResult() == true -> service.retryPartialAutoExport()
            service?.isRunning() == true -> service.pause()
            else -> requestStart()
        }
    }

    private fun skipOrUsePartial() {
        val service = VtmanQueueController.service
        when {
            service?.hasPartialAutoResult() == true -> service.continuePartialAutoExport()
            service != null -> service.skipErroredWaybill()
            else -> VtmanQueueController.fail("Trợ năng chưa kết nối; chưa thể bỏ qua MVĐ")
        }
    }

    private fun checkPendingStart() {
        when (connectionWait.poll(accessibilityEnabled(), VtmanQueueController.service != null, SystemClock.elapsedRealtime())) {
            VtmanConnectionWait.Result.READY -> VtmanQueueController.service?.begin()
            VtmanConnectionWait.Result.DISABLED -> VtmanQueueController.fail("Trợ năng Giao Hàng Pro chưa bật. Mở Trợ năng để cấp quyền.")
            VtmanConnectionWait.Result.WAITING -> VtmanQueueController.report("Trợ năng đã bật · đang chờ Android kết nối…")
            VtmanConnectionWait.Result.TIMED_OUT -> VtmanQueueController.report("Trợ năng vẫn đang bật · Android kết nối lại hơi lâu. Export sẽ tự chạy khi kết nối, không cần bật lại quyền.")
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
            status.text = if (autoExportLocked) {
                "Auto Export ${s.processed}/${s.total} • Lưu ${s.written} • Bỏ ${s.skipped}"
            } else {
                "Đã xử lý ${s.processed}/${s.total} · Lấy được ${s.written} · Bỏ qua ${s.skipped}\n${s.status}"
            }
            val service = VtmanQueueController.service
            val partialCount = service?.partialAutoCount() ?: 0
            if (::runPauseButton.isInitialized) {
                runPauseButton.text = when {
                    partialCount > 0 -> "Quét lại"
                    service?.isRunning() == true -> "Tạm dừng"
                    else -> "Chạy"
                }
            }
            if (::skipButton.isInitialized) {
                skipButton.text = if (partialCount > 0) "Lấy $partialCount đơn" else "Bỏ qua"
                skipButton.isEnabled = partialCount > 0 || (s.error.isNotBlank() && s.currentWaybill.isNotBlank())
            }
            handler.postDelayed(this, 450)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_AUTO_EXPORT_LOCKED, false) == true) {
            autoExportLocked = true
        }
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
            if (autoExportLocked) setPadding(12, 5, 12, 6) else setPadding(18, 12, 18, 12)
            background = GradientDrawable().apply { setColor(Color.rgb(194, 78, 20)); cornerRadius = 24f }
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = if (autoExportLocked) 12f else 13f
            maxLines = if (autoExportLocked) 1 else 6
            gravity = Gravity.CENTER
            if (autoExportLocked) ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
            if (autoExportLocked) {
                textSize = 11f
                isAllCaps = false
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(2, 0, 2, 0)
            }
        }
        runPauseButton = button("Chạy") { toggleRunPause() }
        row.addView(runPauseButton, LinearLayout.LayoutParams(0, if (autoExportLocked) 68 else -2, 1f))
        skipButton = button("Bỏ qua") { skipOrUsePartial() }.apply { isEnabled = false }
        row.addView(skipButton, LinearLayout.LayoutParams(0, if (autoExportLocked) 68 else -2, 1f))
        row.addView(
            button("Tắt") { cancelStart(); stopSelf() },
            LinearLayout.LayoutParams(0, if (autoExportLocked) 68 else -2, 1f)
        )
        panel.addView(status)
        panel.addView(row)
        if (!autoExportLocked) makeDraggable(panel)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            if (autoExportLocked) (screenWidth * 0.94f).toInt() else 760,
            -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = if (autoExportLocked) (screenHeight * 0.184f).toInt() else 140
            x = 0
        }
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
        const val EXTRA_AUTO_EXPORT_LOCKED = "AUTO_EXPORT_LOCKED"
        @Volatile private var instance: VtmanOverlayService? = null

        fun notifyAccessibilityConnected() {
            val service = instance ?: return
            service.handler.post {
                if (instance === service) service.checkPendingStart()
            }
        }

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
