package com.example.giaohangpro.vtman

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val tick = Runnable { process() }

    override fun onServiceConnected() { VtmanQueueController.service = this }
    override fun onInterrupt() = Unit
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { if (mode > 1) schedule(120) }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); if (VtmanQueueController.service === this) VtmanQueueController.service = null; super.onDestroy() }

    fun begin() {
        val r = rootInActiveWindow
        pkg = r?.packageName?.toString(); r?.recycle()
        if (VtmanQueueController.nextWaybill() == null) { VtmanQueueController.fail("Hãy nạp danh sách MVĐ trước"); return }
        if (pkg.isNullOrBlank() || pkg == packageName) { VtmanQueueController.fail("Mở VTMan ở Gạch phát offline rồi bấm Chạy"); return }
        mode = 1
        VtmanQueueController.report("Chạm đúng biểu tượng gọi của đơn đầu tiên")
        if (!VtmanOverlayService.requestCallPointSelection { x,y -> VtmanQueueController.setCallPoint(x,y); mode=2; schedule(100) }) {
            VtmanQueueController.fail("Popup VTMan Export chưa mở"); mode=0
        }
    }

    fun stop() { mode=0; h.removeCallbacksAndMessages(null); VtmanQueueController.clearCallPoint(); VtmanOverlayService.clearCallPointUi(); VtmanQueueController.report("Đã dừng") }

    private fun process() {
        val root=rootInActiveWindow ?: run { schedule(180); return }
        try {
            val mv=VtmanQueueController.nextWaybill() ?: run { mode=0; VtmanQueueController.report("Hoàn tất toàn bộ MVĐ"); return }
            when(mode) { 2->search(root,mv); 3->readBlock(root,mv); 4->readPhone(root,mv); 5->waitReturn(root) }
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

        val rec=VtmanFixedBlockParser.parse(resultStrings,mv)
        if (rec==null || rec.customer.isBlank() || rec.address.isBlank() || rec.cod.isBlank()) {
            if (System.currentTimeMillis()<resultDeadline) { schedule(250); return }
            // A matching order is visible. A parsing failure is not an empty result:
            // retain the current queue item so Chạy retries it instead of losing it.
            val missing = buildList {
                if (rec == null) add("khối đơn")
                else {
                    if (rec.customer.isBlank()) add("tên khách")
                    if (rec.address.isBlank()) add("địa chỉ")
                    if (rec.cod.isBlank()) add("COD")
                }
            }.joinToString(", ")
            mode=0
            VtmanQueueController.fail("Tìm thấy $mv nhưng chưa đọc được $missing. Đơn vẫn được giữ; bấm Chạy để thử lại.")
            return
        }
        if (!VtmanQueueController.stage(rec)) { VtmanQueueController.fail("MVĐ đọc được không trùng mã đang chờ"); mode=0; return }
        val p=VtmanQueueController.callPoint() ?: run { VtmanQueueController.fail("Chưa chọn nút gọi"); mode=0; return }
        if (!tap(p.x,p.y)) { VtmanQueueController.fail("Không bấm được nút gọi"); mode=0; return }
        deadline=System.currentTimeMillis()+3000; mode=4
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
        if (root.packageName?.toString()!=pkg) {
            VtmanFixedBlockParser.findPhone(root.collectStrings())?.let { phone ->
                VtmanQueueController.updatePhone(phone)
                if (VtmanQueueController.missingActiveFields().isNotEmpty()) { VtmanQueueController.fail("Đơn $mv còn thiếu dữ liệu"); mode=0; return }
                VtmanQueueController.finalizeCurrent()
                mode=5
                val now = System.currentTimeMillis()
                returnDeadline = now + 9000L
                nextBackAt = now + 700L
                VtmanQueueController.report("Đã lấy SĐT $phone · đang quay lại Gạch phát offline")
                schedule(250)
                return
            }
        }
        if (System.currentTimeMillis()>deadline) { VtmanQueueController.fail("Không đọc được SĐT của $mv"); mode=0 } else schedule(180)
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
    private fun schedule(ms:Long){ if(mode>1){ h.removeCallbacks(tick); h.postDelayed(tick,ms) } }
}
