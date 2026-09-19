package com.example.giaohangpro.vtman

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VtmanAccessibilityService : AccessibilityService() {
    companion object {
        const val VTMAN_PACKAGE_NAME = "com.viettelpost.vtman"
    }

    private val h = Handler(Looper.getMainLooper())
    private var pkg: String? = null
    private var mode = 0
    private var deadline = 0L
    private var returnDeadline = 0L
    private var nextBackAt = 0L
    private var resultDeadline = 0L
    private var rootMissingSince: Long? = null
    private var phoneReadNotBefore = 0L
    private var autoTarget = 0
    private val autoWaybills = linkedSetOf<String>()
    private var autoSeekingTop = false
    private var autoLastSignature = ""
    private var autoStableTicks = 0
    private var autoCallX = 0f
    private var autoCallY = 0f
    private var pausedMode = 0
    private var pausedAt = 0L
    private var tickScheduled = false
    private var lastAutoNavigationTapAt = 0L
    private var autoEntryCheckScheduled = false
    private var autoBeginScheduled = false
    private var autoWorkflowStartedAt = 0L
    private var retryWaybill = ""
    private var retryAttempt = 0
    private val tick = Runnable { tickScheduled = false; safely { process() } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        VtmanQueueController.attachContext(applicationContext)
        VtmanQueueController.service = this
        VtmanOverlayService.notifyAccessibilityConnected()
        diagnostic("connected")
    }
    override fun onInterrupt() = Unit
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (mode > 1) {
            schedule(120)
            return
        }
        if (mode == 0 && VtmanQueueController.hasPendingAutoWorkflow()) {
            val eventPackage = event?.packageName?.toString().orEmpty()
            if (eventPackage.isBlank() || eventPackage == packageName) return
            handlePendingAutoNavigation()
        }
    }

    private fun handlePendingAutoNavigation() {
        if (autoWorkflowStartedAt == 0L) autoWorkflowStartedAt = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow ?: run {
            scheduleAutoEntryCheck(500L)
            return
        }
        try {
            val activePackage = root.packageName?.toString().orEmpty()
            if (activePackage != VTMAN_PACKAGE_NAME) {
                scheduleAutoEntryCheck(500L)
                return
            }

            val strings = root.collectStrings()
            val isHome = strings.any { it.equals("Giao hàng", true) } &&
                strings.any { it.contains("Gạch phát offline", true) }
            val isDelivery = strings.any { it.contains("Tổng giao", true) } &&
                strings.any { it.contains("đơn hàng", true) }
            val isOfflineList = strings.any { it.contains("Gạch phát offline", true) } &&
                strings.any { it.contains("Danh sách phát", true) }
            val now = SystemClock.elapsedRealtime()

            if (VtmanQueueController.isAutoCountDiscoveryPending()) {
                if (isDelivery) {
                    val count = extractDeliveryCount(strings)
                    if (count != null && VtmanQueueController.resolveAutoCount(count, "Tổng giao")) {
                        VtmanQueueController.report("Đã đọc $count MVĐ · đang quay lại trang chính VTMan")
                        lastAutoNavigationTapAt = now
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        scheduleAutoEntryCheck(500L)
                        return
                    }
                } else if (isHome) {
                    val delivery = root.findExactTextBounds("Giao hàng")
                    if (delivery != null && now - lastAutoNavigationTapAt >= 2_000L) {
                        lastAutoNavigationTapAt = now
                        VtmanQueueController.report("Auto Export: đang mở Giao hàng để đọc tổng MVĐ")
                        tap(delivery.exactCenterX(), delivery.exactCenterY())
                    }
                    scheduleAutoEntryCheck(600L)
                    return
                } else if (isOfflineList && now - lastAutoNavigationTapAt >= 2_000L) {
                    lastAutoNavigationTapAt = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    scheduleAutoEntryCheck(600L)
                    return
                }

                if (now - autoWorkflowStartedAt >= 50_000L) {
                    if (!VtmanQueueController.useFallbackAutoCount("không đọc được Tổng giao")) {
                        VtmanQueueController.fail(
                            "Không đọc được số lượng tại Tổng giao. Nhập số dự phòng rồi chạy lại."
                        )
                        return
                    }
                }
                scheduleAutoEntryCheck(600L)
                return
            }

            if (isOfflineList) {
                val search = root.findSearch()
                val oldQuery = search?.text?.toString()?.trim().orEmpty()
                if (oldQuery.isNotEmpty()) {
                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            ""
                        )
                    }
                    val cleared = search?.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        arguments
                    ) == true
                    search?.recycle()
                    if (cleared) {
                        VtmanQueueController.report("Auto Export: đã xóa ô tìm kiếm, đang tải lại danh sách")
                        scheduleAutoEntryCheck(450L)
                    } else {
                        VtmanQueueController.fail(
                            "Không xóa được ô tìm kiếm VTMan. Hãy xóa thủ công rồi bấm Chạy."
                        )
                    }
                    return
                }
                search?.recycle()
                autoWorkflowStartedAt = 0L
                showAutoOverlay()
                scheduleAutoBegin()
                return
            }

            if (isHome) {
                val offline = root.findExactTextBounds("Gạch phát offline")
                if (offline != null && now - lastAutoNavigationTapAt >= 2_000L) {
                    lastAutoNavigationTapAt = now
                    VtmanQueueController.report("Auto Export: đang mở Gạch phát offline")
                    tap(offline.exactCenterX(), offline.exactCenterY())
                }
                scheduleAutoEntryCheck(600L)
                return
            }

            if (isDelivery && now - lastAutoNavigationTapAt >= 2_000L) {
                lastAutoNavigationTapAt = now
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            scheduleAutoEntryCheck(600L)
        } finally {
            root.recycle()
        }
    }

    private fun extractDeliveryCount(strings: List<String>): Int? {
        val joined = strings.joinToString(" ")
        val total = Regex(
            "Tổng\\s*giao\\s*\\(\\s*(\\d{1,3})\\s*\\)",
            RegexOption.IGNORE_CASE
        ).find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val orders = strings.firstNotNullOfOrNull { value ->
            Regex("^(\\d{1,3})\\s*đơn hàng$", RegexOption.IGNORE_CASE)
                .find(value.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        return when {
            total != null && orders != null && total == orders && total > 0 -> total
            total != null && orders == null && total > 0 -> total
            orders != null && total == null && orders > 0 -> orders
            else -> null
        }
    }

    private fun showAutoOverlay() {
        if (VtmanOverlayService.showDeferredAutoPanel()) return
        runCatching {
            startService(
                Intent(this, VtmanOverlayService::class.java)
                    .putExtra(VtmanOverlayService.EXTRA_AUTO_EXPORT_LOCKED, true)
            )
        }.onFailure {
            VtmanQueueController.fail("Không mở được popup Auto Export: ${it.message}")
        }
    }

    private fun scheduleAutoEntryCheck(delayMs: Long) {
        if (autoEntryCheckScheduled) return
        autoEntryCheckScheduled = true
        h.postDelayed({
            autoEntryCheckScheduled = false
            if (mode == 0 && VtmanQueueController.hasPendingAutoWorkflow()) {
                safely { handlePendingAutoNavigation() }
            }
        }, delayMs)
    }

    private fun scheduleAutoBegin() {
        if (autoBeginScheduled) return
        autoBeginScheduled = true
        h.postDelayed({
            autoBeginScheduled = false
            if (mode == 0 && VtmanQueueController.hasPendingAutoExport()) begin()
        }, 350L)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        detach()
        return super.onUnbind(intent)
    }
    override fun onDestroy() { detach(); super.onDestroy() }

    private fun diagnostic(code: String) {
        // Keep only lifecycle/error categories, never customer text or phone numbers.
        Log.i("VtmanAccessibility", code)
        getSharedPreferences("vtman_diagnostics", MODE_PRIVATE).edit()
            .putString("last_event", code).putLong("last_event_at", System.currentTimeMillis()).apply()
    }

    private fun detach() {
        val wasRunning = mode != 0
        mode = 0
        h.removeCallbacksAndMessages(null)
        tickScheduled = false
        autoEntryCheckScheduled = false
        autoBeginScheduled = false
        autoWorkflowStartedAt = 0L
        rootMissingSince = null
        phoneReadNotBefore = 0L
        pausedMode = 0
        pausedAt = 0L
        if (VtmanQueueController.service === this) {
            VtmanQueueController.service = null
            VtmanOverlayService.clearCallPointUi()
            VtmanQueueController.clearCallPoint()
            diagnostic("disconnected")
            if (wasRunning) VtmanQueueController.fail("Trợ năng mất kết nối. Đã dừng Export; bấm Chạy sau khi dịch vụ kết nối lại.")
        }
    }

    private fun safely(action: () -> Unit) {
        try { action() }
        catch (e: RuntimeException) { reportFailure(e) }
    }

    fun reportFailure(error: RuntimeException) {
        stop()
        diagnostic("error:" + error.javaClass.simpleName)
        VtmanQueueController.fail("Lỗi đọc màn hình (" + error.javaClass.simpleName + "). Mở lại VTMan rồi bấm Chạy; không cần cấp lại quyền nếu Trợ năng vẫn kết nối.")
    }

    fun begin() = safely {
        if (mode != 0) return@safely
        rootMissingSince = null
        beginExport()
    }

    private fun beginExport() {
        val root = rootInActiveWindow
        pkg = root?.packageName?.toString()
        if (pkg.isNullOrBlank() || pkg == packageName) {
            root?.recycle()
            VtmanQueueController.fail("Mở VTMan ở Gạch phát offline rồi thử lại")
            return
        }

        val requestedAutoCount = VtmanQueueController.consumePendingAutoExport()
        if (requestedAutoCount > 0 && root != null) {
            val bounds = Rect().also(root::getBoundsInScreen)
            autoTarget = requestedAutoCount
            autoWaybills.clear()
            autoSeekingTop = true
            autoLastSignature = ""
            autoStableTicks = 0
            autoCallX = bounds.left + bounds.width() * 0.90f
            autoCallY = bounds.top + bounds.height() * 0.50f
            root.recycle()
            mode = 7
            VtmanQueueController.report("Auto Export: đang đưa danh sách về đầu")
            schedule(250)
            return
        }
        if (requestedAutoCount <= 0 && root != null &&
            VtmanQueueController.hasResumableSession() &&
            VtmanQueueController.callPoint() == null
        ) {
            val bounds = Rect().also(root::getBoundsInScreen)
            VtmanQueueController.setCallPoint(
                bounds.left + bounds.width() * 0.90f,
                bounds.top + bounds.height() * 0.50f
            )
            root.recycle()
            mode = 2
            VtmanQueueController.report("Đã khôi phục Auto Export · tiếp tục ${VtmanQueueController.nextWaybill()}")
            schedule(350)
            return
        }
        root?.recycle()

        if (VtmanQueueController.nextWaybill() == null) {
            VtmanQueueController.fail("Hãy nạp danh sách MVĐ trước")
            return
        }
        mode = 1
        VtmanQueueController.report("Chạm đúng biểu tượng gọi của đơn đầu tiên")
        if (!VtmanOverlayService.requestCallPointSelection { x,y ->
            if (mode == 1 && VtmanQueueController.service === this) {
                VtmanQueueController.setCallPoint(x,y); mode=2; schedule(100)
            }
        }) {
            VtmanQueueController.fail("Popup VTMan Export chưa mở"); mode=0
        }
    }

    fun isRunning(): Boolean = mode > 1
    fun isPaused(): Boolean = pausedMode > 1
    fun hasPartialAutoResult(): Boolean = mode == 0 && autoTarget > 0 && autoWaybills.isNotEmpty()
    fun partialAutoCount(): Int = if (hasPartialAutoResult()) autoWaybills.size else 0

    fun retryPartialAutoExport() = safely {
        if (!hasPartialAutoResult()) return@safely
        autoWaybills.clear()
        autoSeekingTop = true
        autoLastSignature = ""
        autoStableTicks = 0
        mode = 7
        VtmanQueueController.addAutoLog("Quét lại từ đầu · cần $autoTarget MVĐ")
        VtmanQueueController.report("Auto Export: đang quét lại từ đầu")
        schedule(200)
    }

    fun continuePartialAutoExport() = safely {
        if (!hasPartialAutoResult()) return@safely
        val codes = autoWaybills.toList()
        VtmanQueueController.addAutoLog("Tiếp tục với ${codes.size}/$autoTarget MVĐ đã tìm thấy")
        startExportingAutoCodes(codes)
    }

    fun pause() = safely {
        if (mode <= 1) return@safely
        pausedMode = mode
        pausedAt = System.currentTimeMillis()
        mode = 0
        h.removeCallbacksAndMessages(null)
        tickScheduled = false
        rootMissingSince = null
        VtmanQueueController.report("Đã tạm dừng")
    }

    fun resume() = safely {
        if (pausedMode <= 1) {
            begin()
            return@safely
        }
        val now = System.currentTimeMillis()
        val pausedDuration = (now - pausedAt).coerceAtLeast(0L)
        if (deadline > 0L) deadline += pausedDuration
        if (returnDeadline > 0L) returnDeadline += pausedDuration
        if (resultDeadline > 0L) resultDeadline += pausedDuration
        if (nextBackAt > 0L) nextBackAt += pausedDuration
        if (phoneReadNotBefore > 0L) phoneReadNotBefore += pausedDuration
        mode = pausedMode
        pausedMode = 0
        pausedAt = 0L
        VtmanQueueController.report("Đang tiếp tục Auto Export")
        schedule(100)
    }

    fun stop() { mode=0; pausedMode=0; pausedAt=0L; h.removeCallbacksAndMessages(null); tickScheduled=false; autoEntryCheckScheduled=false; autoBeginScheduled=false; autoWorkflowStartedAt=0L; lastAutoNavigationTapAt=0L; rootMissingSince=null; phoneReadNotBefore=0L; autoTarget=0; autoWaybills.clear(); autoSeekingTop=false; autoLastSignature=""; autoStableTicks=0; retryWaybill=""; retryAttempt=0; VtmanQueueController.clearCallPoint(); VtmanOverlayService.clearCallPointUi(); VtmanQueueController.report("Đã dừng") }

    private fun resetRetry(waybill: String) {
        if (retryWaybill == waybill) {
            retryWaybill = ""
            retryAttempt = 0
        }
    }

    private fun retryCurrentOrFail(waybill: String, reason: String, returnToVtman: Boolean = false) {
        if (retryWaybill != waybill) {
            retryWaybill = waybill
            retryAttempt = 0
        }
        if (retryAttempt >= 2) {
            mode = 0
            VtmanQueueController.fail("$reason · đã tự thử lại 2 lần")
            return
        }
        retryAttempt++
        VtmanQueueController.clearActiveForRetry()
        VtmanQueueController.addAutoLog("↻ $waybill · thử lại $retryAttempt/2")
        VtmanQueueController.report("$reason · đang tự thử lại $retryAttempt/2")
        h.removeCallbacksAndMessages(null)
        tickScheduled = false
        rootMissingSince = null
        if (returnToVtman) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            returnDeadline = System.currentTimeMillis() + 9_000L
            nextBackAt = System.currentTimeMillis() + 600L
            mode = 5
            schedule(500)
        } else {
            mode = 2
            schedule(550)
        }
    }

    fun skipErroredWaybill() = safely {
        val skipped = VtmanQueueController.skipCurrentByUser()
        if (skipped == null) {
            VtmanQueueController.fail("Không có MVĐ để bỏ qua")
            return@safely
        }

        h.removeCallbacksAndMessages(null)
        tickScheduled = false
        rootMissingSince = null
        val (waybill, next) = skipped
        if (next == null) {
            completeAutoRun("Đã bỏ qua $waybill · hoàn tất toàn bộ MVĐ")
        } else if (VtmanQueueController.callPoint() != null) {
            mode = 2
            VtmanQueueController.report("Đã bỏ qua $waybill · tiếp tục $next")
            schedule(300)
        } else {
            mode = 0
            VtmanQueueController.report("Đã bỏ qua $waybill · bấm Chạy để tiếp tục $next")
        }
    }

    private fun process() {
        if (mode <= 1) return
        val root=rootInActiveWindow ?: run {
            val now = SystemClock.elapsedRealtime()
            val since = rootMissingSince ?: now.also { rootMissingSince = it }
            if (now - since >= 5000L) {
                stop()
                diagnostic("window_unavailable")
                VtmanQueueController.fail("Trợ năng chưa đọc được màn hình trong 5 giây. Mở VTMan ở phía trước rồi bấm Chạy lại.")
            } else schedule(180)
            return
        }
        rootMissingSince = null
        try {
            if (mode == 7) {
                collectAutoWaybills(root)
                return
            }
            val mv=VtmanQueueController.nextWaybill() ?: run { mode=0; VtmanQueueController.report("Hoàn tất toàn bộ MVĐ"); return }
            when(mode) {
                2 -> search(root,mv)
                3 -> readBlock(root,mv)
                4 -> readPhone(root,mv)
                5 -> waitReturn(root)
                6 -> waitPhonePickerDismissed(root)
            }
        } finally { root.recycle() }
    }

    private fun search(root: AccessibilityNodeInfo, mv: String) {
        if (root.packageName?.toString()!=pkg) { VtmanQueueController.fail("VTMan không ở trên màn hình"); mode=0; return }
        val f=root.findSearch() ?: run { VtmanQueueController.fail("Không thấy ô Search"); mode=0; return }
        val a=Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,mv) }
        val ok=f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,a); f.recycle()
        if (!ok) { retryCurrentOrFail(mv, "Không nhập được MVĐ vào Search"); return }
        mode=3
        resultDeadline=System.currentTimeMillis()+5000L
        VtmanQueueController.report("Đang chờ kết quả $mv")
        schedule(500)
    }

    private fun readBlock(root: AccessibilityNodeInfo, mv: String) {
        val allStrings=root.collectStrings()
        if (allStrings.any { it.contains("không có dữ liệu",true) || it.contains("không tìm thấy đơn",true) }) {
            skipNoDataAndContinue(mv)
            return
        }

        // Bỏ nội dung ô Search ra khỏi dữ liệu kết quả. Nếu màn hình rỗng thì MVĐ chỉ tồn tại
        // trong ô Search; không được xem đó là một đơn hợp lệ để bấm nút gọi.
        val resultStrings=root.collectResultStrings()
        val hasCurrentResult=resultStrings.any { VtmanFixedBlockParser.containsExpectedWaybill(it,mv) }
        if (!hasCurrentResult) {
            if (System.currentTimeMillis()<resultDeadline) { schedule(250); return }
            skipNoDataAndContinue(mv)
            return
        }

        // Ưu tiên cấu trúc 5 hàng theo vị trí/icon; parser chữ cũ chỉ là
        // dự phòng cho phiên bản VTMan không cung cấp tọa độ TextView.
        val positioned = root.collectResultPositionedTexts()
        val rec = VtmanFixedBlockParser.parsePositioned(positioned, mv)
            ?: VtmanFixedBlockParser.parse(resultStrings, mv)
        if (rec==null || rec.shop.isBlank() || rec.customer.isBlank() || rec.address.isBlank() ||
            rec.goods.isBlank() || rec.status.isBlank() || rec.cod.isBlank()) {
            if (System.currentTimeMillis()<resultDeadline) { schedule(250); return }
            // A matching order is visible. A parsing failure is not an empty result:
            // retain the current queue item so Chạy retries it instead of losing it.
            val missing = buildList {
                if (rec == null) add("khối đơn")
                else {
                    if (rec.shop.isBlank()) add("tên shop")
                    if (rec.customer.isBlank()) add("tên khách")
                    if (rec.address.isBlank()) add("địa chỉ")
                    if (rec.goods.isBlank()) add("hàng hóa")
                    if (rec.status.isBlank()) add("trạng thái")
                    if (rec.cod.isBlank()) add("COD")
                }
            }.joinToString(", ")
            retryCurrentOrFail(mv, "Tìm thấy $mv nhưng chưa đọc được $missing")
            return
        }
        if (!VtmanQueueController.stage(rec)) {
            retryCurrentOrFail(mv, "MVĐ đọc được không trùng mã đang chờ")
            return
        }

        // Không dùng Y cố định của đơn đầu tiên: khi Search còn nhiều block, tọa độ đó
        // có thể bấm nút gọi của MVĐ khác. Giữ X do người dùng chọn nhưng khóa Y theo
        // đúng hàng người nhận của MVĐ hiện tại.
        val p=VtmanQueueController.callPoint() ?: run { VtmanQueueController.fail("Chưa chọn nút gọi"); mode=0; return }
        val callY = positioned.recipientRowCenterY(mv, rec.customer) ?: p.y

        // Popup cũ phải được đóng trước khi bấm. Nếu không, số của đơn trước có thể
        // bị nhận là số của đơn đang xử lý.
        if (allStrings.any { it.contains("Chọn số điện thoại để gọi", true) }) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            resultDeadline = System.currentTimeMillis() + 2500L
            schedule(350)
            return
        }

        val stillShowsCurrent = root.collectResultStrings()
            .any { VtmanFixedBlockParser.containsExpectedWaybill(it, mv) }
        if (!stillShowsCurrent) {
            retryCurrentOrFail(mv, "Màn hình đã đổi trước khi lấy SĐT của $mv")
            return
        }
        if (!tap(p.x,callY)) {
            retryCurrentOrFail(mv, "Không bấm được nút gọi của $mv")
            return
        }
        val now = System.currentTimeMillis()
        phoneReadNotBefore = now + 250L
        deadline=now+4000L
        mode=4
        VtmanQueueController.report("Đã đọc ${rec.shop.ifBlank { "shop chưa rõ" }} · ${rec.customer}; đang lấy SĐT")
        schedule(250)
    }

    private fun skipNoDataAndContinue(mv:String) {
        resetRetry(mv)
        VtmanQueueController.skipNoData()
        val next=VtmanQueueController.nextWaybill()
        if (next==null) {
            completeAutoRun("Hoàn tất toàn bộ MVĐ · $mv không có dữ liệu")
        } else {
            mode=2
            VtmanQueueController.report("$mv không có dữ liệu · bỏ qua, tiếp tục $next")
            schedule(450)
        }
    }

    private fun readPhone(root: AccessibilityNodeInfo, mv: String) {
        val now = System.currentTimeMillis()
        if (!VtmanQueueController.activeMatchesCurrent()) {
            VtmanQueueController.fail("Dữ liệu đang đọc không còn khớp MVĐ $mv")
            mode=0
            return
        }
        if (now < phoneReadNotBefore) {
            schedule(phoneReadNotBefore - now)
            return
        }

        val strings = root.collectStrings()
        val phonePickerVisible = strings.any { it.contains("Chọn số điện thoại để gọi", true) }
        val outsideVtman = root.packageName?.toString() != pkg

        // Khi có bảng chọn nhiều số, chỉ đọc các node nằm dưới tiêu đề popup.
        // Không quét toàn màn hình vì nền phía sau có thể chứa SĐT của MVĐ khác.
        val phoneStrings = when {
            phonePickerVisible -> root.collectPhonePickerStringsTopToBottom()
            outsideVtman -> root.collectStringsTopToBottom()
            else -> emptyList()
        }
        VtmanFixedBlockParser.findPhone(phoneStrings)?.let { phone ->
            VtmanQueueController.updatePhone(phone)
            if (VtmanQueueController.missingActiveFields().isNotEmpty()) {
                retryCurrentOrFail(mv, "Đơn $mv còn thiếu dữ liệu", returnToVtman = true)
                return
            }
            if (VtmanQueueController.finalizeCurrent() == null) {
                retryCurrentOrFail(mv, "Không thể lưu SĐT vì MVĐ hiện tại đã thay đổi", returnToVtman = true)
                return
            }
            resetRetry(mv)
            returnDeadline = now + 9000L
            nextBackAt = now + 180L

            if (phonePickerVisible) {
                // Lấy đúng số đầu tiên từ trên xuống rồi đóng bảng, không thực hiện cuộc gọi.
                performGlobalAction(GLOBAL_ACTION_BACK)
                mode=6
                VtmanQueueController.report("Đã lấy SĐT đầu tiên $phone · đang đóng danh sách số")
            } else {
                mode=5
                VtmanQueueController.report("Đã lấy SĐT $phone · đang quay lại Gạch phát offline")
            }
            schedule(120)
            return
        }

        if (now>deadline) {
            retryCurrentOrFail(mv, "Không đọc được SĐT của $mv", returnToVtman = true)
        } else schedule(180)
    }

    private fun waitPhonePickerDismissed(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        val pickerVisible = root.collectStrings().any { it.contains("Chọn số điện thoại để gọi", true) }
        if (pickerVisible) {
            if (now > returnDeadline) {
                VtmanQueueController.fail("Đã lấy SĐT nhưng không đóng được danh sách số")
                mode = 0
                return
            }
            if (now >= nextBackAt) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                nextBackAt = now + 350L
            }
            schedule(100)
            return
        }

        mode = if (VtmanQueueController.nextWaybill()==null) 0 else 2
        if (mode==0) completeAutoRun("Hoàn tất toàn bộ MVĐ")
        else {
            VtmanQueueController.report("Đã lưu SĐT đầu tiên · tiếp tục đơn kế")
            schedule(180)
        }
    }

    private fun waitReturn(root: AccessibilityNodeInfo) {
        val currentPkg = root.packageName?.toString()
        if (currentPkg == pkg) {
            mode = if (VtmanQueueController.nextWaybill()==null) 0 else 2
            if (mode==0) completeAutoRun("Hoàn tất toàn bộ MVĐ")
            else {
                VtmanQueueController.report("Đã trở lại Gạch phát offline · tiếp tục đơn kế")
                schedule(220)
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now > returnDeadline) {
            VtmanQueueController.fail("Không tự quay lại được Gạch phát offline")
            mode = 0
            return
        }
        if (now >= nextBackAt) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            nextBackAt = now + 650L
        }
        schedule(120)
    }

    private fun clearVtmanSearchField() {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != VTMAN_PACKAGE_NAME) return
            val search = root.findSearch() ?: return
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            search.recycle()
        } finally {
            root.recycle()
        }
    }

    private fun completeAutoRun(message: String) {
        mode = 0
        clearVtmanSearchField()
        VtmanQueueController.report(message)
        if (!VtmanQueueController.isAutoSession()) return
        h.postDelayed({
            val launch = Intent(this, com.example.giaohangpro.MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(launch)
            VtmanOverlayService.closeAfterCompletion()
        }, 180L)
    }

    private fun collectAutoWaybills(root: AccessibilityNodeInfo) {
        if (root.packageName?.toString() != pkg) {
            VtmanQueueController.fail("Auto Export: VTMan không còn ở trên màn hình")
            mode = 0
            return
        }

        val visible = root.collectResultPositionedTexts().visibleWaybillCodes()
        val signature = visible.joinToString("|")

        if (autoSeekingTop) {
            autoStableTicks = if (signature.isNotBlank() && signature == autoLastSignature) autoStableTicks + 1 else 0
            autoLastSignature = signature
            val moved = root.performListScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            if (!moved || autoStableTicks >= 2) {
                autoSeekingTop = false
                autoLastSignature = ""
                autoStableTicks = 0
                VtmanQueueController.report("Auto Export: đã về đầu · đang gom 0/$autoTarget MVĐ")
                schedule(450)
            } else {
                VtmanQueueController.report("Auto Export: đang đưa danh sách về đầu")
                schedule(450)
            }
            return
        }

        visible.forEach { code ->
            if (autoWaybills.size < autoTarget) autoWaybills += code
        }
        VtmanQueueController.report("Auto Export: đã gom ${autoWaybills.size}/$autoTarget MVĐ")

        if (autoWaybills.size >= autoTarget) {
            val codes = autoWaybills.take(autoTarget)
            VtmanQueueController.addAutoLog("Đã gom đủ ${codes.size} MVĐ")
            startExportingAutoCodes(codes)
            return
        }

        autoStableTicks = if (signature.isNotBlank() && signature == autoLastSignature) autoStableTicks + 1 else 0
        autoLastSignature = signature
        val moved = root.performListScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        if (!moved || autoStableTicks >= 3) {
            val found = autoWaybills.size
            VtmanQueueController.fail(
                if (found > 0)
                    "Chỉ tìm thấy $found/$autoTarget MVĐ · chọn Lấy $found đơn hoặc Quét lại"
                else
                    "Không tìm thấy MVĐ · chọn Quét lại"
            )
            mode = 0
            return
        }
        schedule(650)
    }

    private fun startExportingAutoCodes(codes: List<String>) {
        if (codes.isEmpty()) {
            VtmanQueueController.fail("Không có MVĐ để tiếp tục")
            mode = 0
            return
        }
        VtmanQueueController.load(codes, preserveAutoLog = true)
        VtmanQueueController.setCallPoint(autoCallX, autoCallY)
        autoTarget = 0
        autoWaybills.clear()
        autoLastSignature = ""
        autoStableTicks = 0
        mode = 2
        VtmanQueueController.report("Đã chọn ${codes.size} MVĐ · bắt đầu lấy dữ liệu")
        schedule(500)
    }

    private fun List<VtmanScreenText>.visibleWaybillCodes(): List<String> {
        val codeRegex = Regex("^[A-Z0-9]{8,24}$", RegexOption.IGNORE_CASE)
        val combinedRegex = Regex(
            "(?<![A-Z0-9])([A-Z0-9]{8,24})\\s+TT\\s*(?:500|505|506|507|508|515)\\b",
            RegexOption.IGNORE_CASE
        )
        val statusRegex = Regex("^TT\\s*(?:500|505|506|507|508|515)$", RegexOption.IGNORE_CASE)
        val nodes = sortedWith(compareBy<VtmanScreenText> { it.top }.thenBy { it.left })
        val out = linkedSetOf<String>()

        nodes.forEachIndexed { index, node ->
            combinedRegex.find(node.value)?.groupValues?.getOrNull(1)?.uppercase()?.let(out::add)
            if (statusRegex.matches(node.value.trim())) {
                val centerY = (node.top + node.bottom) / 2
                nodes.take(index).asReversed().firstOrNull { candidate ->
                    val value = candidate.value.trim()
                    val candidateCenterY = (candidate.top + candidate.bottom) / 2
                    candidate.left < node.left &&
                        kotlin.math.abs(candidateCenterY - centerY) <= 32 &&
                        codeRegex.matches(value) &&
                        value.any(Char::isDigit)
                }?.value?.trim()?.uppercase()?.let(out::add)
            }
        }
        return out.toList()
    }

    private fun AccessibilityNodeInfo.performListScroll(action: Int): Boolean {
        val candidates = mutableListOf<Pair<Int, AccessibilityNodeInfo>>()
        fun walk(node: AccessibilityNodeInfo) {
            if (node.isScrollable) {
                val bounds = Rect().also(node::getBoundsInScreen)
                candidates += (bounds.width() * bounds.height()) to AccessibilityNodeInfo.obtain(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(this)
        val sorted = candidates.sortedByDescending(Pair<Int, AccessibilityNodeInfo>::first)
        var moved = false
        sorted.forEach { (_, node) ->
            if (!moved) moved = node.performAction(action)
            node.recycle()
        }
        return moved
    }

    private fun AccessibilityNodeInfo.findSearch(): AccessibilityNodeInfo? {
        var out: AccessibilityNodeInfo?=null
        fun walk(n:AccessibilityNodeInfo):Boolean {
            val label=listOfNotNull(n.hintText?.toString(),n.text?.toString(),n.contentDescription?.toString()).joinToString(" ")
            val b=Rect().also(n::getBoundsInScreen)
            if (b.top<600 && (n.isEditable || label.contains("search",true))) { out=AccessibilityNodeInfo.obtain(n); return true }
            for(i in 0 until n.childCount){ val c=n.getChild(i)?:continue; val yes=walk(c); c.recycle(); if(yes)return true }
            return false
        }
        walk(this); return out
    }

    private fun AccessibilityNodeInfo.collectStrings():List<String>{ val out= mutableListOf<String>(); fun walk(n:AccessibilityNodeInfo){ sequenceOf(n.text?.toString(),n.contentDescription?.toString(),n.hintText?.toString()).mapNotNull{it?.trim()?.takeIf(String::isNotBlank)}.forEach(out::add); for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;walk(c);c.recycle()} };walk(this);return out }

    private fun AccessibilityNodeInfo.findExactTextBounds(label: String): Rect? {
        var result: Rect? = null
        fun walk(node: AccessibilityNodeInfo): Boolean {
            val matches = sequenceOf(node.text, node.contentDescription, node.hintText)
                .mapNotNull { it?.toString()?.trim() }
                .any { it.equals(label, ignoreCase = true) }
            if (matches) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (!bounds.isEmpty) {
                    result = bounds
                    return true
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                val found = walk(child)
                child.recycle()
                if (found) return true
            }
            return false
        }
        walk(this)
        return result
    }

    private fun AccessibilityNodeInfo.collectStringsTopToBottom(): List<String> {
        data class PositionedText(val top: Int, val left: Int, val order: Int, val value: String)
        val out = mutableListOf<PositionedText>()
        var order = 0
        fun walk(n: AccessibilityNodeInfo) {
            val bounds = Rect().also(n::getBoundsInScreen)
            sequenceOf(n.text?.toString(), n.contentDescription?.toString(), n.hintText?.toString())
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .forEach { out += PositionedText(bounds.top, bounds.left, order++, it) }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(this)
        return out.sortedWith(compareBy<PositionedText> { it.top }.thenBy { it.left }.thenBy { it.order })
            .map(PositionedText::value)
    }

    private fun AccessibilityNodeInfo.collectPhonePickerStringsTopToBottom(): List<String> {
        data class PositionedText(val top: Int, val bottom: Int, val left: Int, val order: Int, val value: String)
        val out = mutableListOf<PositionedText>()
        var order = 0
        fun walk(n: AccessibilityNodeInfo) {
            val bounds = Rect().also(n::getBoundsInScreen)
            sequenceOf(n.text?.toString(), n.contentDescription?.toString(), n.hintText?.toString())
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .forEach { out += PositionedText(bounds.top, bounds.bottom, bounds.left, order++, it) }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(this)
        val title = out
            .filter { it.value.contains("Chọn số điện thoại để gọi", true) }
            .minByOrNull(PositionedText::top)
            ?: return emptyList()
        return out.asSequence()
            .filter { it.top >= title.bottom - 2 && !it.value.contains("Chọn số điện thoại để gọi", true) }
            .sortedWith(compareBy<PositionedText> { it.top }.thenBy { it.left }.thenBy { it.order })
            .map(PositionedText::value)
            .toList()
    }

    private fun List<VtmanScreenText>.recipientRowCenterY(expectedWaybill: String, customer: String): Float? {
        val headerBottom = asSequence()
            .filter { VtmanFixedBlockParser.containsExpectedWaybill(it.value, expectedWaybill) }
            .minByOrNull(VtmanScreenText::top)
            ?.bottom
            ?: return null
        return asSequence()
            .filter { it.top >= headerBottom - 2 && it.value.trim() == customer.trim() }
            .minByOrNull(VtmanScreenText::top)
            ?.let { (it.top + it.bottom) / 2f }
    }

    private fun AccessibilityNodeInfo.collectResultPositionedTexts(): List<VtmanScreenText> {
        val out = mutableListOf<VtmanScreenText>()
        fun walk(n: AccessibilityNodeInfo) {
            if (!n.isEditable) {
                val value = n.text?.toString()?.trim().orEmpty()
                if (value.isNotBlank()) {
                    val bounds = Rect().also(n::getBoundsInScreen)
                    if (!bounds.isEmpty) {
                        out += VtmanScreenText(
                            value = value,
                            left = bounds.left,
                            top = bounds.top,
                            right = bounds.right,
                            bottom = bounds.bottom
                        )
                    }
                }
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(this)
        return out
    }

    private fun AccessibilityNodeInfo.collectResultStrings():List<String>{
        val out= mutableListOf<String>()
        fun walk(n:AccessibilityNodeInfo){
            if(!n.isEditable){
                sequenceOf(n.text?.toString(),n.contentDescription?.toString())
                    .mapNotNull{it?.trim()?.takeIf(String::isNotBlank)}
                    .forEach(out::add)
            }
            for(i in 0 until n.childCount){ val c=n.getChild(i)?:continue; walk(c); c.recycle() }
        }
        walk(this)
        return out
    }

    private fun tap(x:Float,y:Float):Boolean{ val p=Path().apply{moveTo(x,y)}; return dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p,0,70)).build(),null,null) }
    // Window events must not keep postponing the worker forever.
    private fun schedule(ms:Long){ if(mode>1 && !tickScheduled){ tickScheduled=true; h.postDelayed(tick,ms) } }
}
