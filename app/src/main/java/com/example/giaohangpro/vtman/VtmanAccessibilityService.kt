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
    private val h = Handler(Looper.getMainLooper())
    private var pkg: String? = null
    private var mode = 0
    private var deadline = 0L
    private var returnDeadline = 0L
    private var nextBackAt = 0L
    private var resultDeadline = 0L
    private var rootMissingSince: Long? = null
    private var phoneReadNotBefore = 0L
    private var tickScheduled = false
    private val tick = Runnable { tickScheduled = false; safely { process() } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        VtmanQueueController.service = this
        VtmanOverlayService.notifyAccessibilityConnected()
        diagnostic("connected")
    }
    override fun onInterrupt() = Unit
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { if (mode > 1) schedule(120) }
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
        val r = rootInActiveWindow
        pkg = r?.packageName?.toString(); r?.recycle()
        if (VtmanQueueController.nextWaybill() == null) { VtmanQueueController.fail("Hãy nạp danh sách MVĐ trước"); return }
        if (pkg.isNullOrBlank() || pkg == packageName) { VtmanQueueController.fail("Mở VTMan ở Gạch phát offline rồi bấm Chạy"); return }
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

    fun stop() { mode=0; h.removeCallbacksAndMessages(null); tickScheduled=false; rootMissingSince=null; phoneReadNotBefore=0L; VtmanQueueController.clearCallPoint(); VtmanOverlayService.clearCallPointUi(); VtmanQueueController.report("Đã dừng") }

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
            mode = 0
            VtmanQueueController.report("Đã bỏ qua $waybill · hoàn tất toàn bộ MVĐ")
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
        if (!ok) { VtmanQueueController.fail("Không nhập được MVĐ vào Search"); mode=0; return }
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
            mode=0
            VtmanQueueController.fail("Tìm thấy $mv nhưng chưa đọc được $missing. Đơn vẫn được giữ; bấm Chạy để thử lại.")
            return
        }
        if (!VtmanQueueController.stage(rec)) { VtmanQueueController.fail("MVĐ đọc được không trùng mã đang chờ"); mode=0; return }

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

        if (!tap(p.x,callY)) { VtmanQueueController.fail("Không bấm được nút gọi của $mv"); mode=0; return }
        val now = System.currentTimeMillis()
        phoneReadNotBefore = now + 250L
        deadline=now+4000L
        mode=4
        VtmanQueueController.report("Đã đọc ${rec.shop.ifBlank { "shop chưa rõ" }} · ${rec.customer}; đang lấy SĐT")
        schedule(250)
    }

    private fun skipNoDataAndContinue(mv:String) {
        VtmanQueueController.skipNoData()
        val next=VtmanQueueController.nextWaybill()
        if (next==null) {
            mode=0
            VtmanQueueController.report("Hoàn tất toàn bộ MVĐ · $mv không có dữ liệu")
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
                VtmanQueueController.fail("Đơn $mv còn thiếu dữ liệu")
                mode=0
                return
            }
            if (VtmanQueueController.finalizeCurrent() == null) {
                VtmanQueueController.fail("Không thể lưu SĐT vì MVĐ hiện tại đã thay đổi")
                mode=0
                return
            }
            returnDeadline = now + 9000L
            nextBackAt = now + 500L

            if (phonePickerVisible) {
                // Lấy đúng số đầu tiên từ trên xuống rồi đóng bảng, không thực hiện cuộc gọi.
                performGlobalAction(GLOBAL_ACTION_BACK)
                mode=6
                VtmanQueueController.report("Đã lấy SĐT đầu tiên $phone · đang đóng danh sách số")
            } else {
                mode=5
                VtmanQueueController.report("Đã lấy SĐT $phone · đang quay lại Gạch phát offline")
            }
            schedule(250)
            return
        }

        if (now>deadline) {
            VtmanQueueController.fail("Không đọc được SĐT của $mv")
            mode=0
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
                nextBackAt = now + 700L
            }
            schedule(180)
            return
        }

        mode = if (VtmanQueueController.nextWaybill()==null) 0 else 2
        if (mode==0) VtmanQueueController.report("Hoàn tất toàn bộ MVĐ")
        else {
            VtmanQueueController.report("Đã lưu SĐT đầu tiên · tiếp tục đơn kế")
            schedule(350)
        }
    }

    private fun waitReturn(root: AccessibilityNodeInfo) {
        val currentPkg = root.packageName?.toString()
        if (currentPkg == pkg) {
            mode = if (VtmanQueueController.nextWaybill()==null) 0 else 2
            if (mode==0) VtmanQueueController.report("Hoàn tất toàn bộ MVĐ")
            else {
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
            nextBackAt = now + 1800L
        }
        schedule(250)
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
