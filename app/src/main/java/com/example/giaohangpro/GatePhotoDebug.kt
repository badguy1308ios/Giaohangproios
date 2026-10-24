package com.example.giaohangpro

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Bounded persistent diagnostics. No customer names, phones, image bytes or full URIs. */
internal object GatePhotoDebug {
    private const val PREFS = "gate_photo_debug_v1"

    @Synchronized
    fun failure(context: Context, source: String, stage: String, error: String) {
        // Diagnostics must never turn a failed preview into an application crash.
        try {
            val key = GatePhotoArchive.sourceKey(source).take(12)
            val uri = Uri.parse(source)
            val row = JSONObject().apply {
                put("time", java.util.Date().toString())
                put("version", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
                put("photo", key); put("stage", stage); put("error", error)
                put("sourceType", uri.scheme ?: "local")
                val path = if (uri.scheme == null) source else uri.path.orEmpty()
                val resolved = GatePhotoArchive.find(context.filesDir, source)
                    ?: GatePhotoFiles.local(context.filesDir, path)
                val direct = when (uri.scheme) {
                    "file" -> java.io.File(path)
                    null -> java.io.File(path).let { if (it.isAbsolute) it else java.io.File(context.filesDir, path) }
                    else -> null
                }
                val file = resolved ?: direct
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
                if (old.optString("photo") == key && old.optString("stage") == stage && old.optString("error") == error)
                    row.put("count", old.optInt("count", 1) + 1)
                else result.put(old)
            }
            result.put(row)
            val bounded = JSONArray()
            for (i in maxOf(0, result.length() - 40) until result.length()) bounded.put(result.getJSONObject(i))
            prefs.edit().putString("events", bounded.toString()).apply()
            Log.e("GatePhotoDebug", row.toString())
        } catch (_: Exception) { Log.e("GatePhotoDebug", "Diagnostic persistence failed: $stage / $error") }
        catch (_: OutOfMemoryError) { Log.e("GatePhotoDebug", "Low memory: $stage / $error") }
    }

    fun report(context: Context): String = runCatching {
        val events = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("events", "[]"))
        if (events.length() == 0) "Chưa ghi nhận lỗi bộ đọc kể từ khi cài bản có debug."
        else (0 until events.length()).joinToString("\n") { events.getJSONObject(it).toString() }
    }.getOrDefault("Không đọc được nhật ký debug")
}
