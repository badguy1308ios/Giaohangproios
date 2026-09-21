package com.example.giaohangpro.vtman

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

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
    private const val PREFS = "vtman_export_checkpoint"
    private const val KEY_STATE = "active_state"
    private val queue = mutableListOf<String>()
    private val completed = linkedMapOf<String, VtmanOrderRecord>()
    private val skipped = mutableListOf<String>()
    private var index = 0
    private var active: VtmanOrderRecord? = null
    private var callPoint: CallPoint? = null
    private var pendingAutoCount = 0
    private var pendingDataExport = false
    private var autoLogEnabled = false
    private val autoRunLog = mutableListOf<String>()
    private var appContext: Context? = null
    @Volatile var service: VtmanAccessibilityService? = null
    private var status = "Chưa nạp danh sách MVĐ"
    private var error = ""

    @Synchronized fun attachContext(context: Context) {
        appContext = context.applicationContext
        if (queue.isEmpty() && completed.isEmpty() && pendingAutoCount == 0) restoreCheckpoint()
    }

    @Synchronized fun load(waybills: List<String>, preserveAutoLog: Boolean = false) {
        queue.clear()
        queue.addAll(waybills.map(String::trim).filter(String::isNotBlank).distinct())
        completed.clear(); skipped.clear(); index = 0; active = null; callPoint = null
        pendingAutoCount = 0; pendingDataExport = false; error = ""
        if (!preserveAutoLog) {
            autoLogEnabled = false
            autoRunLog.clear()
        }
        status = if (queue.isEmpty()) "Không tìm thấy MVĐ hợp lệ" else "Đã nạp ${queue.size} MVĐ. Mở VTMan/Gạch phát offline rồi bấm Chạy."
        persistCheckpoint()
    }

    @Synchronized fun prepareAutoExport(count: Int) {
        queue.clear(); completed.clear(); skipped.clear()
        index = 0; active = null; callPoint = null; error = ""
        pendingDataExport = false
        pendingAutoCount = count.coerceIn(1, 500)
        autoLogEnabled = true
        autoRunLog.clear()
        appendAutoLog("Bắt đầu · $pendingAutoCount MVĐ")
        status = "Auto Export đã sẵn sàng · cần lấy $pendingAutoCount MVĐ. Mở Gạch phát offline."
        persistCheckpoint()
    }

    private fun appendAutoLog(message: String) {
        if (!autoLogEnabled) return
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "$time  $message"
        if (autoRunLog.lastOrNull() != line) autoRunLog += line
    }

    @Synchronized fun addAutoLog(message: String) {
        appendAutoLog(message)
        persistCheckpoint()
    }

    @Synchronized fun clearAutoLog() {
        autoRunLog.clear()
        persistCheckpoint()
    }

    @Synchronized fun hasPendingAutoExport(): Boolean = pendingAutoCount > 0

    @Synchronized fun consumePendingAutoExport(): Int {
        val count = pendingAutoCount
        pendingAutoCount = 0
        return count
    }

    @Synchronized fun hasLoadedWaybills(): Boolean = queue.getOrNull(index) != null

    @Synchronized fun importWaybillsFromCsv(text: String): Int {
        val codes = text.lineSequence()
            .map { line ->
                val raw = line.trim().removePrefix("\uFEFF")
                val first = if (raw.startsWith("\"")) {
                    raw.drop(1).substringBefore("\"").replace("\"\"", "\"")
                } else raw.substringBefore(',').substringBefore(';')
                first.trim().uppercase()
            }
            .filter { it.isNotBlank() }
            .filterNot { it in setOf("MA_VAN_DON", "MÃ VẬN ĐƠN", "MVĐ", "MVD", "WAYBILL") }
            .filter { code -> code.length in 8..24 && code.any(Char::isDigit) && code.all { it.isLetterOrDigit() } }
            .distinct()
            .toList()
        if (codes.isEmpty()) {
            fail("File CSV không có MVĐ hợp lệ")
            return 0
        }
        load(codes)
        autoLogEnabled = true
        appendAutoLog("↥ Đã nạp ${codes.size} MVĐ từ file CSV")
        status = "Đã nạp ${codes.size} MVĐ từ file · sẵn sàng Export dữ liệu đơn"
        persistCheckpoint()
        return codes.size
    }

    @Synchronized fun saveWaybillBackupCsv(waybills: List<String>): String? {
        val context = appContext ?: return null
        val codes = waybills.map(String::trim).filter(String::isNotBlank).distinct()
        if (codes.isEmpty()) return null
        val fileName = "AutoExport_MVD_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"
        val csv = buildString {
            append("MA_VAN_DON\n")
            codes.forEach { append('\"').append(it.replace("\"", "\"\"")).append("\"\n") }
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Không tạo được file trong Download")
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    ?: error("Không mở được file CSV")
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeText(csv, Charsets.UTF_8)
            }
            appendAutoLog("✓ Đã lưu dự phòng $fileName")
            status = "Đã lưu ${codes.size} MVĐ · file dự phòng: Download/$fileName"
            persistCheckpoint()
            fileName
        }.getOrElse {
            appendAutoLog("⚠ Không lưu được file CSV: ${it.message.orEmpty()}")
            persistCheckpoint()
            null
        }
    }

    @Synchronized fun requestDataExport(): Boolean {
        if (queue.getOrNull(index) == null) {
            fail("Chưa có danh sách MVĐ để Export dữ liệu")
            return false
        }
        pendingAutoCount = 0
        pendingDataExport = true
        error = ""
        autoLogEnabled = true
        appendAutoLog("Bắt đầu bước 2 · Export dữ liệu ${queue.size - index} MVĐ")
        status = "Đã có ${queue.size} MVĐ · mở Gạch phát offline để Export dữ liệu"
        persistCheckpoint()
        return true
    }

    @Synchronized fun hasPendingDataExport(): Boolean = pendingDataExport
    @Synchronized fun hasPendingAutoAction(): Boolean =
        pendingAutoCount > 0 || pendingDataExport

    @Synchronized fun consumePendingDataExport(): Boolean {
        val requested = pendingDataExport
        pendingDataExport = false
        return requested
    }

    @Synchronized fun isAutoSession(): Boolean = autoLogEnabled
    @Synchronized fun hasResumableSession(): Boolean = autoLogEnabled && queue.getOrNull(index) != null
    @Synchronized fun nextWaybill(): String? = queue.getOrNull(index)
    @Synchronized fun setCallPoint(x: Float, y: Float) {
        callPoint = CallPoint(x, y)
        persistCheckpoint()
    }
    @Synchronized fun callPoint(): CallPoint? = callPoint
    @Synchronized fun clearCallPoint() { callPoint = null }
    @Synchronized fun clearActiveForRetry() { active = null }

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

    private fun appendCompletionLog() {
        appendAutoLog("Hoàn tất · Lưu ${completed.size}/${queue.size} · Bỏ qua ${skipped.size}")
        if (skipped.isNotEmpty()) appendAutoLog("Cần kiểm tra lại · ${skipped.joinToString(", ")}")
    }

    @Synchronized fun finalizeCurrent(): VtmanOrderRecord? {
        val record = active ?: return null
        if (record.waybill != nextWaybill() || missingActiveFields().isNotEmpty()) return null
        completed[record.waybill] = record
        index++
        active = null
        status = "Đã lấy ${completed.size}/${queue.size}: ${record.waybill}"
        appendAutoLog("✓ ${record.waybill} · Đã lưu")
        if (index >= queue.size) appendCompletionLog()
        error = ""
        persistCheckpoint()
        return record
    }

    @Synchronized fun skipNoData(): String? {
        val waybill = queue.getOrNull(index) ?: return null
        skipped += waybill
        index++
        active = null
        status = "Bỏ qua $waybill: VTMan không có dữ liệu"
        appendAutoLog("⚠ $waybill · Không có dữ liệu")
        if (index >= queue.size) appendCompletionLog()
        persistCheckpoint()
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
        persistCheckpoint()
        return waybill to next
    }

    @Synchronized fun records(): List<VtmanOrderRecord> = completed.values.toList()
    @Synchronized fun report(message: String) { status = message; error = "" }
    @Synchronized fun fail(message: String) {
        status = message
        error = message
        appendAutoLog("✕ Lỗi · $message")
        persistCheckpoint()
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

    private fun recordToJson(record: VtmanOrderRecord) = JSONObject().apply {
        put("waybill", record.waybill); put("shop", record.shop); put("phone", record.phone)
        put("customer", record.customer); put("goods", record.goods); put("status", record.status)
        put("cod", record.cod); put("address", record.address); put("service", record.service)
    }

    private fun jsonToRecord(value: JSONObject) = VtmanOrderRecord(
        waybill = value.optString("waybill"),
        shop = value.optString("shop"),
        phone = value.optString("phone"),
        customer = value.optString("customer"),
        goods = value.optString("goods"),
        status = value.optString("status"),
        cod = value.optString("cod"),
        address = value.optString("address"),
        service = value.optString("service"),
    )

    private fun persistCheckpoint() {
        val context = appContext ?: return
        val activeSession = pendingAutoCount > 0 || index < queue.size
        val state = JSONObject().apply {
            put("active", activeSession)
            put("queue", JSONArray().apply { queue.forEach(::put) })
            put("completed", JSONArray().apply { completed.values.forEach { put(recordToJson(it)) } })
            put("skipped", JSONArray().apply { skipped.forEach(::put) })
            put("index", index)
            put("pendingAutoCount", pendingAutoCount)
            put("autoLogEnabled", autoLogEnabled)
            put("runLog", JSONArray().apply { autoRunLog.forEach(::put) })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATE, state.toString()).apply()
    }

    private fun restoreCheckpoint() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null) ?: return
        runCatching {
            val state = JSONObject(raw)
            if (!state.optBoolean("active", false)) return
            queue.clear()
            state.optJSONArray("queue")?.let { values ->
                for (i in 0 until values.length()) values.optString(i).takeIf(String::isNotBlank)?.let(queue::add)
            }
            completed.clear()
            state.optJSONArray("completed")?.let { values ->
                for (i in 0 until values.length()) {
                    val record = jsonToRecord(values.getJSONObject(i))
                    if (record.waybill.isNotBlank()) completed[record.waybill] = record
                }
            }
            skipped.clear()
            state.optJSONArray("skipped")?.let { values ->
                for (i in 0 until values.length()) values.optString(i).takeIf(String::isNotBlank)?.let(skipped::add)
            }
            index = state.optInt("index", 0).coerceIn(0, queue.size)
            pendingAutoCount = state.optInt("pendingAutoCount", 0).coerceIn(0, 500)
            autoLogEnabled = state.optBoolean("autoLogEnabled", true)
            autoRunLog.clear()
            state.optJSONArray("runLog")?.let { values ->
                for (i in 0 until values.length()) values.optString(i).takeIf(String::isNotBlank)?.let(autoRunLog::add)
            }
            active = null
            callPoint = null
            error = ""
            status = "Đã khôi phục tiến độ · tiếp tục ${queue.getOrNull(index).orEmpty()}"
            appendAutoLog("↻ Khôi phục tiến độ tại ${queue.getOrNull(index).orEmpty()}")
        }.onFailure {
            prefs.edit().remove(KEY_STATE).apply()
        }
    }
}

data class CallPoint(val x: Float, val y: Float)
