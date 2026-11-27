package com.example.giaohangpro

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Bounded persistent diagnostics. No customer names, phones, image bytes or full URIs. */
internal object GatePhotoDebug {
    private const val PREFS = "gate_photo_debug_v1"

    fun failure(context: Context, source: String, stage: String, error: String) {
        record(context, source, stage, error)
    }

    @Synchronized
    fun record(context: Context, source: String, stage: String, error: String = "OK", customerId: Long? = null) {
        // Diagnostics must never turn a failed preview into an application crash.
        try {
            val key = GatePhotoArchive.sourceKey(source).take(12)
            val uri = Uri.parse(source)
            val row = JSONObject().apply {
                put("time", java.util.Date().toString())
                put("version", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
                put("photo", key); put("stage", stage); put("error", error)
                customerId?.let { put("customerId", it) }
                put("sourceType", uri.scheme ?: "local")
                val path = if (uri.scheme == null) source else uri.path.orEmpty()
                val resolved = GatePhotoArchive.find(context.filesDir, source)
                    ?: GatePhotoFiles.local(context.filesDir, path)
                val direct = when (uri.scheme) {
                    "file" -> java.io.File(path)
                    null -> java.io.File(path).let { if (it.isAbsolute) it else java.io.File(context.filesDir, path) }
                    else -> null
                }
                val file = resolved ?: GatePhotoFiles.owned(context.filesDir, path) ?: direct
                if (file != null) { put("fileExists", file.isFile); put("fileBytes", file.length()) }
                put("archived", GatePhotoArchive.find(context.filesDir, source) != null)
                put("persistedReadGrant", if (uri.scheme == "content")
                    context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } else false)
                put("count", 1)
            }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = runCatching { JSONArray(prefs.getString("events", "[]")) }.getOrElse { JSONArray() }
            val result = JSONArray()
            for (i in 0 until previous.length()) {
                val old = previous.getJSONObject(i)
                if (old.optString("photo") == key && old.optString("stage") == stage && old.optString("error") == error &&
                    old.optString("customerId") == row.optString("customerId"))
                    row.put("count", old.optInt("count", 1) + 1)
                else result.put(old)
            }
            result.put(row)
            val bounded = JSONArray()
            for (i in maxOf(0, result.length() - 80) until result.length()) bounded.put(result.getJSONObject(i))
            prefs.edit().putString("events", bounded.toString()).apply()
            if (error == "OK") Log.i("GatePhotoDebug", row.toString()) else Log.e("GatePhotoDebug", row.toString())
        } catch (_: Exception) { Log.e("GatePhotoDebug", "Diagnostic persistence failed: $stage / $error") }
        catch (_: OutOfMemoryError) { Log.e("GatePhotoDebug", "Low memory: $stage / $error") }
    }

    fun changed(context: Context, before: Customer, after: Customer) {
        val old = (listOf(before.photoUri) + before.extraAddresses.map { it.photoUri }).filter(String::isNotBlank).toSet()
        val new = (listOf(after.photoUri) + after.extraAddresses.map { it.photoUri }).filter(String::isNotBlank).toSet()
        (old - new).forEach { record(context, it, "LINK_REMOVED", customerId = after.id) }
        (new - old).forEach { record(context, it, "LINK_SAVED", customerId = after.id) }
    }

    // Read local metadata and saved events only: no gallery provider reads or whole-library scan.
    fun quickReport(context: Context, customer: Customer): String = buildString {
        appendLine("Khách ID ${customer.id} · kiểm tra nhanh ảnh đang lưu")
        val sources = listOf(customer.photoUri) + customer.extraAddresses.map { it.photoUri }
        sources.forEachIndexed { index, source ->
            appendLine(if (index == 0) "Ảnh chính:" else "Ảnh địa chỉ ${index + 1}:")
            if (source.isBlank()) appendLine("Bản ghi không có đường dẫn ảnh.") else {
                val uri = Uri.parse(source)
                val path = if (uri.scheme == null) source else uri.path.orEmpty()
                val archived = GatePhotoArchive.find(context.filesDir, source)
                val file = archived ?: GatePhotoFiles.local(context.filesDir, path)
                    ?: GatePhotoFiles.owned(context.filesDir, path)
                appendLine("Mã ảnh: ${GatePhotoArchive.sourceKey(source).take(12)}")
                when {
                    file != null && file.isFile -> appendLine("File còn: ${file.length()} byte. ${if (archived != null) "Có bản lưu nội bộ cũ." else ""}")
                    file != null -> appendLine("Không tìm thấy file tại đường dẫn nội bộ đã lưu.")
                    uri.scheme == "content" -> appendLine("Đường dẫn nhà cung cấp ảnh; chưa có bản nội bộ đọc được. Kiểm tra nhanh không mở lại nguồn này.")
                    else -> appendLine("Không tìm thấy file đọc được theo đường dẫn đã lưu.")
                }
            }
        }
        appendLine("\nNhật ký liên quan (tối đa 80 sự kiện gần nhất toàn app):")
        val keys = sources.filter(String::isNotBlank).map { GatePhotoArchive.sourceKey(it).take(12) }.toSet()
        appendLine(runCatching {
            val events = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("events", "[]"))
            val matching = (0 until events.length()).map { events.getJSONObject(it) }.filter {
                it.optString("photo") in keys || (it.has("customerId") && it.optLong("customerId") == customer.id)
            }
            matching.joinToString("\n") { it.toString() }.ifBlank { "Chưa có sự kiện cho ảnh/khách này. Không đủ dữ liệu để kết luận nguyên nhân." }
        }.getOrDefault("Không đọc được nhật ký."))
        appendLine("File còn không đồng nghĩa ảnh giải mã được. Lỗi giải mã và quyền đọc được ghi khi mở ảnh.")
    }

    fun report(context: Context): String = runCatching {
        val events = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("events", "[]"))
        if (events.length() == 0) "Chưa ghi nhận lỗi bộ đọc kể từ khi cài bản có debug."
        else (0 until events.length()).joinToString("\n") { events.getJSONObject(it).toString() }
    }.getOrDefault("Không đọc được nhật ký debug")
}
