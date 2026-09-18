package com.example.giaohangpro.vtman

data class VtmanQueueSnapshot(
    val total: Int = 0,
    val processed: Int = 0,
    val written: Int = 0,
    val skipped: Int = 0,
    val currentWaybill: String = "",
    val status: String = "Chưa nạp danh sách MVĐ",
    val error: String = "",
    val runLog: List<String> = emptyList(),
)

object VtmanQueueController {
    private val queue = mutableListOf<String>()
    private val completed = linkedMapOf<String, VtmanOrderRecord>()
    private val skipped = mutableListOf<String>()
    private var index = 0
    private var active: VtmanOrderRecord? = null
    private var callPoint: CallPoint? = null
    private var pendingAutoCount = 0
    private var autoLogEnabled = false
    private val autoRunLog = mutableListOf<String>()
    @Volatile var service: VtmanAccessibilityService? = null
    private var status = "Chưa nạp danh sách MVĐ"
    private var error = ""

    @Synchronized fun load(waybills: List<String>, preserveAutoLog: Boolean = false) {
        queue.clear()
        queue.addAll(waybills.map(String::trim).filter(String::isNotBlank).distinct())
        completed.clear(); skipped.clear(); index = 0; active = null; callPoint = null; pendingAutoCount = 0; error = ""
        if (!preserveAutoLog) {
            autoLogEnabled = false
            autoRunLog.clear()
        }
        status = if (queue.isEmpty()) "Không tìm thấy MVĐ hợp lệ" else "Đã nạp ${queue.size} MVĐ. Mở VTMan/Gạch phát offline rồi bấm Chạy."
    }

    @Synchronized fun prepareAutoExport(count: Int) {
        queue.clear(); completed.clear(); skipped.clear()
        index = 0; active = null; callPoint = null; error = ""
        pendingAutoCount = count.coerceIn(1, 500)
        autoLogEnabled = true
        autoRunLog.clear()
        appendAutoLog("Bắt đầu · $pendingAutoCount MVĐ")
        status = "Auto Export đã sẵn sàng · cần lấy $pendingAutoCount MVĐ. Mở Gạch phát offline."
    }

    private fun appendAutoLog(message: String) {
        if (!autoLogEnabled) return
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "$time  $message"
        if (autoRunLog.lastOrNull() != line) autoRunLog += line
    }

    @Synchronized fun addAutoLog(message: String) { appendAutoLog(message) }

    @Synchronized fun clearAutoLog() {
        autoRunLog.clear()
    }

    @Synchronized fun hasPendingAutoExport(): Boolean = pendingAutoCount > 0

    @Synchronized fun consumePendingAutoExport(): Int {
        val count = pendingAutoCount
        pendingAutoCount = 0
        return count
    }

    @Synchronized fun nextWaybill(): String? = queue.getOrNull(index)
    @Synchronized fun setCallPoint(x: Float, y: Float) { callPoint = CallPoint(x, y) }
    @Synchronized fun callPoint(): CallPoint? = callPoint
    @Synchronized fun clearCallPoint() { callPoint = null }

    @Synchronized fun stage(record: VtmanOrderRecord): Boolean {
        if (record.waybill != nextWaybill()) { active = null; return false }
        active = record
        return true
    }

    @Synchronized fun activeMatchesCurrent(): Boolean = active?.waybill == nextWaybill()
    @Synchronized fun updatePhone(phone: String) { active = active?.copy(phone = phone) }

    @Synchronized fun missingActiveFields(): List<String> {
        val record = active ?: return listOf("block dữ liệu")
        return buildList {
            if (record.phone.isBlank()) add("SĐT")
            if (record.shop.isBlank()) add("shop")
            if (record.customer.isBlank()) add("khách hàng")
            if (record.goods.isBlank()) add("mặt hàng")
            if (record.status.isBlank()) add("trạng thái")
            if (record.cod.isBlank()) add("COD")
            if (record.address.isBlank()) add("địa chỉ")
        }
    }

    @Synchronized fun finalizeCurrent(): VtmanOrderRecord? {
        val record = active ?: return null
        if (record.waybill != nextWaybill() || missingActiveFields().isNotEmpty()) return null
        completed[record.waybill] = record
        index++
        active = null
        status = "Đã lấy ${completed.size}/${queue.size}: ${record.waybill}"
        appendAutoLog("✓ ${record.waybill} · Đã lưu")
        if (index >= queue.size) appendAutoLog("Hoàn tất · Lưu ${completed.size}/${queue.size} · Bỏ qua ${skipped.size}")
        error = ""
        return record
    }

    @Synchronized fun skipNoData(): String? {
        val waybill = queue.getOrNull(index) ?: return null
        skipped += waybill
        index++
        active = null
        status = "Bỏ qua $waybill: VTMan không có dữ liệu"
        appendAutoLog("⚠ $waybill · Không có dữ liệu")
        return nextWaybill()
    }

    @Synchronized fun skipCurrentByUser(): Pair<String, String?>? {
        val waybill = queue.getOrNull(index) ?: return null
        skipped += waybill
        index++
        active = null
        error = ""
        val next = queue.getOrNull(index)
        status = if (next == null) {
            "Đã bỏ qua $waybill · hoàn tất toàn bộ MVĐ"
        } else {
            "Đã bỏ qua $waybill · tiếp tục $next"
        }
        appendAutoLog("⚠ $waybill · Người dùng bỏ qua")
        if (next == null) appendAutoLog("Hoàn tất · Lưu ${completed.size}/${queue.size} · Bỏ qua ${skipped.size}")
        return waybill to next
    }

    @Synchronized fun records(): List<VtmanOrderRecord> = completed.values.toList()
    @Synchronized fun report(message: String) { status = message; error = "" }
    @Synchronized fun fail(message: String) {
        status = message
        error = message
        appendAutoLog("✕ Lỗi · $message")
    }
    @Synchronized fun serviceUnavailable() = fail("Trợ năng Giao Hàng Pro chưa kết nối. Hãy bật dịch vụ trợ năng rồi thử lại.")

    @Synchronized fun snapshot() = VtmanQueueSnapshot(
        total = queue.size,
        processed = index,
        written = completed.size,
        skipped = skipped.size,
        currentWaybill = nextWaybill().orEmpty(),
        status = status,
        error = error,
        runLog = autoRunLog.toList(),
    )
}

data class CallPoint(val x: Float, val y: Float)
