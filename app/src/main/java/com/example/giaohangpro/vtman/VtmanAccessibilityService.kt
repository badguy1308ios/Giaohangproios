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
    private var resultStableWaybill = ""
    private var resultStableSignature = ""
    private var resultStableTicks = 0
    private var rootMissingSince: Long? = null
    private var phoneReadNotBefore = 0L
    private var phoneScreenBaseline = ""
    private var phoneCandidate = ""
    private var phoneCandidateSince = 0L
    private var phoneSurfaceSeenAt = 0L
    private var firstPhoneOfDataSession = false
    private var previousSessionLastPhone = ""
    private var lastAcceptedPhone = ""
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
        val activePackage = root?.packageName?.toString().orEmpty()

        // Người dùng tự mở VTMan > Gạch phát offline trước khi bấm Chạy.
        // Không kiểm tra text tiêu đề vì một số bản VTMan không expose dòng
        // "Gạch phát offline" qua Accessibility, làm Bước 1 bị chặn dù đang đúng màn hình.
        if (root == null || activePackage.isBlank() || activePackage == packageName) {
            root?.recycle()
            VtmanQueueController.fail("Mở VTMan ở Gạch phát offline rồi bấm Chạy lại")
            return
        }

        val requestedAutoCount = VtmanQueueController.consumePendingAutoExport()
        val requestedDataExport = VtmanQueueController.consumePendingDataExport()
        pkg = activePackage
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
        if (requestedDataExport && root != null &&
            VtmanQueueController.nextWaybill() != null
        ) {
            firstPhoneOfDataSession = true
            previousSessionLastPhone = lastAcceptedPhone
            phoneCandidate = ""
            phoneCandidateSince = 0L
            phoneSurfaceSeenAt = 0L
            val bounds = Rect().also(root::getBoundsInScreen)
            if (VtmanQueueController.callPoint() == null) {
                VtmanQueueController.setCallPoint(
                    bounds.left + bounds.width() * 0.90f,
                    bounds.top + bounds.height() * 0.50f
                )
            }
            root.recycle()
            mode = 2
            VtmanQueueController.report(
                "Bắt đầu Export dữ liệu · ${VtmanQueueController.nextWaybill()}"
            )
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
        storeCollectedAutoCodes(codes)
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

    fun stop() { mode=0; pausedMode=0; pausedAt=0L; h.removeCallbacksAndMessages(null); tickScheduled=false; rootMissingSince=null; resultStableWaybill=""; resultStableSignature=""; resultStableTicks=0; phoneReadNotBefore=0L; phoneScreenBaseline=""; phoneCandidate=""; phoneCandidateSince=0L; phoneSurfaceSeenAt=0L; autoTarget=0; autoWaybills.clear(); autoSeekingTop=false; autoLastSignature=""; autoStableTicks=0; retryWaybill=""; retryAttempt=0; VtmanQueueController.clearCallPoint(); VtmanOverlayService.clearCallPointUi(); VtmanQueueController.report("Đã dừng") }

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
            returnDeadline = System.currentTimeMillis() + 12_000L
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
            // Hai trạng thái quay về phải chạy kể cả khi đơn cuối đã finalize và
            // nextWaybill() == null. Trước đây check queue trước làm mode 5/6 bị
            // cắt ngang, nên Auto Export báo 92/92 nhưng vẫn nằm ở màn hình gọi.
            if (mode == 5) {
                waitReturn(root)
                return
            }
            if (mode == 6) {
                waitPhonePickerDismissed(root)
                return
            }
            val mv=VtmanQueueController.nextWaybill() ?: run {
                mode=0
                VtmanQueueController.report("Hoàn tất toàn bộ MVĐ")
                return
            }
            when(mode) {
                2 -> search(root,mv)
                3 -> readBlock(root,mv)
                4 -> readPhone(root,mv)
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
        resultStableWaybill = mv
        resultStableSignature = ""
        resultStableTicks = 0
        resultDeadline=System.currentTimeMillis()+5000L
        VtmanQueueController.report("Đang chờ kết quả $mv")
        schedule(500)
    }

    private fun readBlock(root: AccessibilityNodeInfo, mv: String) {
        val allStrings = root.collectStrings()
        // Bỏ nội dung ô Search ra khỏi dữ liệu kết quả. Nếu màn hình rỗng thì MVĐ chỉ tồn tại
        // trong ô Search; không được xem đó là một đơn hợp lệ để bấm nút gọi.
        val resultStrings=root.collectResultStrings()
        val hasCurrentResult=resultStrings.any { VtmanFixedBlockParser.containsExpectedWaybill(it,mv) }
        if (!hasCurrentResult) {
            if (System.currentTimeMillis()<resultDeadline) { schedule(250); return }
            val explicitlyEmpty = resultStrings.any {
                it.trim().equals("không có dữ liệu", true) || it.trim().equals("không tìm thấy đơn", true)
            }
            if (explicitlyEmpty) skipNoDataAndContinue(mv)
            else retryCurrentOrFail(mv, "Chưa đọc được kết quả $mv; giữ lại mã để thử lại")
            return
        }

        // Không đọc ngay lúc card vừa đổi MVĐ: UI VTMan có thể cập nhật từng hàng,
        // tạo một khoảnh khắc MVĐ mới nhưng shop/khách/hàng hóa vẫn là card cũ.
        val positioned = root.collectResultPositionedTexts()
        val currentSignature = positioned
            .sortedWith(compareBy<VtmanScreenText> { it.top }.thenBy { it.left })
            .joinToString("\u001E") { "${it.top}:${it.left}:${it.value}" }
        if (resultStableWaybill != mv || currentSignature != resultStableSignature) {
            resultStableWaybill = mv
            resultStableSignature = currentSignature
            resultStableTicks = 0
            if (System.currentTimeMillis() < resultDeadline) {
                schedule(180)
                return
            }
        } else {
            resultStableTicks++
            if (resultStableTicks < 1 && System.currentTimeMillis() < resultDeadline) {
                schedule(180)
                return
            }
        }

        // Nếu Accessibility đã thấy đúng MVĐ bằng tọa độ thì CHỈ dùng parser theo card.
        // Không fallback sang parser toàn màn hình khi card đang thiếu hàng, vì đó là
        // đường dễ kéo shop/khách/hàng hóa của MVĐ bên cạnh vào đơn hiện tại.
        val hasPositionedCurrent = positioned.any {
            VtmanFixedBlockParser.containsExpectedWaybill(it.value, mv)
        }
        val rec = if (hasPositionedCurrent) {
            VtmanFixedBlockParser.parsePositioned(positioned, mv)
        } else {
            VtmanFixedBlockParser.parse(resultStrings, mv)
        }
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
        // Chụp trạng thái TRƯỚC cú bấm, rồi xóa toàn bộ ứng viên SĐT của đơn trước.
        // dispatchGesture là bất đồng bộ nên không ghi baseline sau tap.
        phoneScreenBaseline = root.phoneScreenSignature()
        phoneCandidate = ""
        phoneCandidateSince = 0L
        phoneSurfaceSeenAt = 0L
        if (!tap(p.x,callY)) {
            retryCurrentOrFail(mv, "Không bấm được nút gọi của $mv")
            return
        }
        val now = System.currentTimeMillis()
        phoneReadNotBefore = now + 250L
        deadline=now+6000L
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
        val phonePickerVisible = strings.any(VtmanPhonePicker::isTitle)
        val currentPackage = root.packageName?.toString()
        val outsideVtman = currentPackage != pkg

        // Khi có bảng chọn nhiều số, chỉ đọc các node nằm dưới tiêu đề popup.
        // Không quét toàn màn hình vì nền phía sau có thể chứa SĐT của MVĐ khác.
        val phoneStrings = when {
            phonePickerVisible -> emptyList()
            outsideVtman -> root.collectStringsTopToBottom()
            else -> emptyList()
        }

        // Chỉ đọc khi đã thật sự rời màn hình kết quả của VTMan (hoặc popup số đã mở).
        // Dialer có thể hiện SĐT cũ vài trăm ms trước khi cập nhật số mới, nhất là
        // đơn đầu của phiên kế tiếp, nên không được chốt "số đầu tiên nhìn thấy".
        val currentPhoneSignature = root.phoneScreenSignature()
        val phoneSurfaceVisible = phonePickerVisible || outsideVtman
        if (!phoneSurfaceVisible || currentPhoneSignature == phoneScreenBaseline) {
            if (now > deadline) {
                retryCurrentOrFail(mv, "Màn hình SĐT chưa mở sau khi bấm gọi", returnToVtman = outsideVtman)
            } else schedule(120)
            return
        }
        if (phoneSurfaceSeenAt == 0L) phoneSurfaceSeenAt = now

        val phone = if (phonePickerVisible) {
            VtmanPhonePicker.firstPhone(root.collectPhonePickerTexts())
        } else VtmanFixedBlockParser.findPhone(phoneStrings)
        if (phone.isNullOrBlank()) {
            if (now > deadline) {
                retryCurrentOrFail(mv, "Không đọc được SĐT của $mv", returnToVtman = true)
            } else schedule(140)
            return
        }

        if (phone != phoneCandidate) {
            phoneCandidate = phone
            phoneCandidateSince = now
            schedule(140)
            return
        }

        val looksLikePreviousSessionPhone =
            firstPhoneOfDataSession &&
                previousSessionLastPhone.isNotBlank() &&
                phone == previousSessionLastPhone
        val requiredStableMs = when {
            looksLikePreviousSessionPhone -> 1_800L
            firstPhoneOfDataSession -> 1_200L
            else -> 320L
        }
        val stableFor = now - phoneCandidateSince
        val surfaceFor = now - phoneSurfaceSeenAt
        if (stableFor < requiredStableMs || surfaceFor < requiredStableMs) {
            schedule(140)
            return
        }

        run {
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
            lastAcceptedPhone = phone
            firstPhoneOfDataSession = false
            previousSessionLastPhone = ""
            phoneScreenBaseline = ""
            phoneCandidate = ""
            phoneCandidateSince = 0L
            phoneSurfaceSeenAt = 0L
            returnDeadline = now + 9_000L
            nextBackAt = now + 500L

            if (phonePickerVisible) {
                // Giữ đúng cơ chế VTMan Export thường: đóng bảng chọn số trước.
                performGlobalAction(GLOBAL_ACTION_BACK)
                mode=6
                VtmanQueueController.report("Đã lấy SĐT đầu tiên $phone · đang đóng danh sách số")
            } else {
                // Chưa Back ngay khi vừa đọc được số ở ứng dụng ngoài.
                // Chờ màn hình ổn định rồi waitReturn mới phát lệnh Back.
                mode=5
                VtmanQueueController.report("Đã lấy SĐT $phone · đang quay lại Gạch phát offline")
            }
            schedule(250)
            return
        }

        if (now>deadline) {
            retryCurrentOrFail(mv, "Không đọc được SĐT của $mv", returnToVtman = true)
        } else schedule(180)
    }

    private fun waitPhonePickerDismissed(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        val pickerVisible = root.collectStrings().any(VtmanPhonePicker::isTitle)
        if (pickerVisible) {
            if (now > returnDeadline) {
                VtmanQueueController.fail("Đã lấy SĐT nhưng không đóng được danh sách số")
                mode = 0
                return
            }
            // Exactly one Back was already sent when accepting the first phone.
            // Wait for dismissal; a second Back can leave Gạch phát offline.
            schedule(180)
            return
        }
        if (root.packageName?.toString() != pkg) {
            if (now > returnDeadline) {
                VtmanQueueController.fail("Đã đóng danh sách số nhưng chưa thấy màn hình VTMan. Mở Gạch phát offline rồi bấm Chạy.")
                mode = 0
            } else schedule(180)
            return
        }

        if (VtmanQueueController.nextWaybill() == null) {
            // Đơn cuối cũng phải quay sạch về VTMan giống các đơn giữa danh sách.
            // Không hoàn tất ngay khi vừa đóng popup/dialer, tránh để SĐT cuối còn
            // nằm ở ứng dụng gọi và bị phiên Auto Export sau đọc nhầm.
            returnDeadline = now + 9_000L
            nextBackAt = now + 500L
            mode = 5
            VtmanQueueController.report("Đã lấy SĐT đơn cuối · đang quay lại Gạch phát offline")
            schedule(180)
        } else {
            mode = 2
            VtmanQueueController.report("Đã lưu SĐT đầu tiên · tiếp tục đơn kế")
            schedule(350)
        }
    }

    private fun waitReturn(root: AccessibilityNodeInfo) {
        val currentPkg = root.packageName?.toString()
        if (currentPkg == pkg) {
            mode = if (VtmanQueueController.nextWaybill() == null) 0 else 2
            if (mode == 0) {
                // Chỉ đánh dấu hoàn tất sau khi cửa sổ active đã thật sự trở về VTMan.
                completeAutoRun("Hoàn tất toàn bộ MVĐ")
            } else {
                VtmanQueueController.report("Đã trở lại Gạch phát offline · tiếp tục đơn kế")
                schedule(450)
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
            nextBackAt = now + 1_800L
        }
        schedule(250)
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
            storeCollectedAutoCodes(codes)
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

    private fun storeCollectedAutoCodes(codes: List<String>) {
        if (codes.isEmpty()) {
            VtmanQueueController.fail("Không có MVĐ để lưu")
            mode = 0
            return
        }
        VtmanQueueController.load(codes, preserveAutoLog = true)
        val backupFile = VtmanQueueController.saveWaybillBackupCsv(codes)
        VtmanQueueController.setCallPoint(autoCallX, autoCallY)
        VtmanQueueController.addAutoLog("✓ Đã lưu ${codes.size} MVĐ vào bộ nhớ")
        if (backupFile == null) {
            VtmanQueueController.addAutoLog("⚠ Chưa tạo được file CSV dự phòng trong Download")
        }
        autoTarget = 0
        autoWaybills.clear()
        autoLastSignature = ""
        autoStableTicks = 0
        completeAutoRun("Đã lưu ${codes.size} MVĐ · bấm Export dữ liệu đơn để chạy bước 2")
    }

    private fun List<VtmanScreenText>.visibleWaybillCodes(): List<String> {
        return VtmanWaybillCodes.visible(this)
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

    private fun AccessibilityNodeInfo.collectPhonePickerTexts(): List<VtmanScreenText> {
        val out = mutableListOf<VtmanScreenText>()
        fun walk(node: AccessibilityNodeInfo) {
            val bounds = Rect().also(node::getBoundsInScreen)
            sequenceOf(node.text?.toString(), node.contentDescription?.toString(), node.hintText?.toString())
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .forEach { out += VtmanScreenText(it, bounds.left, bounds.top, bounds.right, bounds.bottom) }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(this)
        return out
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
                            bottom = bounds.bottom,
                            isButton = n.className?.toString()?.endsWith("Button") == true
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
    private fun AccessibilityNodeInfo.phoneScreenSignature(): String {
        val parts = collectStringsTopToBottom()
            .take(24)
            .map { it.trim() }
        return packageName?.toString().orEmpty() + "|" + parts.joinToString("|")
    }

}
