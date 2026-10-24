package com.example.giaohangpro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CustomerSyncSession(
    val id: Long,
    val createdAt: Long,
    val upserts: Int,
    val deletes: Int,
    val customerCount: Int
)

object CustomerLocalSync {
    private const val APP_PREFS = "giaohangpro_persistent_data_v1"
    private const val SYNC_PREFS = "customer_local_sync_v1"
    private const val KEY_TIMES = "times"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_SESSIONS = 20
    private const val KEY_PORTABLE_URI = "portable_uri"

    private fun dir(context: Context) = File(context.filesDir, "customer_sync").apply { mkdirs() }
    private fun baseFile(context: Context) = File(dir(context), "base.json")
    private fun deltaFile(context: Context, id: Long) = File(dir(context), "delta_$id.json")
    private fun prefs(context: Context) = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
    private fun appPrefs(context: Context) = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    private fun currentArray(context: Context): JSONArray =
        runCatching { JSONArray(appPrefs(context).getString("customers", "[]") ?: "[]") }.getOrElse { JSONArray() }

    private fun mapById(arr: JSONArray): LinkedHashMap<String, JSONObject> {
        val out = linkedMapOf<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", Long.MIN_VALUE)
            if (id != Long.MIN_VALUE) out[id.toString()] = JSONObject(o.toString())
        }
        return out
    }

    private fun readSessions(context: Context): MutableList<CustomerSyncSession> {
        val raw = prefs(context).getString(KEY_SESSIONS, "[]") ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            MutableList(a.length()) { i ->
                val o = a.getJSONObject(i)
                CustomerSyncSession(o.getLong("id"), o.getLong("createdAt"), o.optInt("upserts"), o.optInt("deletes"), o.optInt("customerCount"))
            }
        }.getOrElse { mutableListOf() }
    }

    private fun writeSessions(context: Context, sessions: List<CustomerSyncSession>) {
        val a = JSONArray()
        sessions.forEach { s -> a.put(JSONObject().apply {
            put("id", s.id); put("createdAt", s.createdAt); put("upserts", s.upserts)
            put("deletes", s.deletes); put("customerCount", s.customerCount)
        }) }
        prefs(context).edit().putString(KEY_SESSIONS, a.toString()).apply()
    }

    fun sessions(context: Context): List<CustomerSyncSession> = readSessions(context).sortedByDescending { it.createdAt }

    private fun writeAtomic(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text, Charsets.UTF_8)
        // Verify JSON before replacing the last known-good file.
        JSONObject(temp.readText(Charsets.UTF_8))
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Không thể hoàn tất file đồng bộ" }
    }

    private fun reconstruct(context: Context, throughId: Long? = null): LinkedHashMap<String, JSONObject> {
        val base = baseFile(context)
        if (!base.exists()) return linkedMapOf()
        val root = JSONObject(base.readText(Charsets.UTF_8))
        val state = mapById(root.optJSONArray("customers") ?: JSONArray())
        val chronological = readSessions(context).sortedBy { it.createdAt }
        for (s in chronological) {
            if (throughId != null && s.createdAt > (chronological.firstOrNull { it.id == throughId }?.createdAt ?: Long.MIN_VALUE)) break
            val f = deltaFile(context, s.id)
            if (!f.exists()) continue
            val d = JSONObject(f.readText(Charsets.UTF_8))
            val deletes = d.optJSONArray("deletes") ?: JSONArray()
            for (i in 0 until deletes.length()) state.remove(deletes.optString(i))
            val upserts = d.optJSONArray("upserts") ?: JSONArray()
            for (i in 0 until upserts.length()) {
                val o = upserts.optJSONObject(i) ?: continue
                state[o.optLong("id").toString()] = JSONObject(o.toString())
            }
            if (throughId == s.id) break
        }
        return state
    }


    fun portableUri(context: Context): android.net.Uri? =
        prefs(context).getString(KEY_PORTABLE_URI, null)?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }

    fun setPortableUri(context: Context, uri: android.net.Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        prefs(context).edit().putString(KEY_PORTABLE_URI, uri.toString()).apply()
    }

    fun hasCustomers(context: Context): Boolean = currentArray(context).length() > 0

    fun portableStatus(context: Context): String = prefs(context).getString("portable_status", "File bên ngoài: chưa có kết quả ghi nhận")!!

    @Synchronized
    fun writePortableBackup(context: Context) {
        val uri = portableUri(context) ?: return
        val work = File(context.cacheDir, "sync_pack_${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val previousFile = File(work, "previous.zip")
        var previousZip: java.util.zip.ZipFile? = null
        var previousCopied = false
        try {
            // Preserve the old bytes before opening the destination for writing.
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    previousFile.outputStream().use { input.copyTo(it) }
                    previousCopied = true
                }
                previousZip = java.util.zip.ZipFile(previousFile)
            }
            val previous = mutableListOf<BackupPhotoReference>()
            runCatching {
                val zip = previousZip ?: return@runCatching
                val m = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() })
                check(m.optString("format") == "giaohangpro-customer-sync")
                val old = JSONArray(zip.getInputStream(zip.getEntry("customers.json")).bufferedReader().use { it.readText() })
                for (i in 0 until old.length()) {
                    val c = old.getJSONObject(i)
                    fun add(o: JSONObject) { previous += BackupPhotoReference(c.optLong("id"), o.optString("photoSourceKey"), o.optString("photoUri")) }
                    add(c)
                    val addresses = c.optJSONArray("extraAddresses") ?: JSONArray()
                    for (j in 0 until addresses.length()) add(addresses.getJSONObject(j))
                }
            }.onFailure { previous.clear() }
            val customers = currentArray(context) // Detached JSON; never edit the live customer records.
            val missing = JSONArray()
            val entries = mutableListOf<Pair<String, File>>()
            var reused = 0
            fun pack(o: JSONObject, customer: JSONObject, label: String) {
                val source = o.optString("photoUri")
                if (source.isBlank()) return
                val key = GatePhotoArchive.sourceKey(source)
                o.put("photoSourceKey", key)
                var bytes = runCatching { gatePhotoBackupBytes(context, source) }.getOrNull()
                if (bytes == null) {
                    val path = PhotoBackupFallback.match(customer.optLong("id"), key, previous)
                    if (path != null) runCatching {
                        val zip = previousZip ?: return@runCatching
                        val entry = zip.getEntry(path) ?: return@runCatching
                        val oldPhoto = File(work, "fallback.img")
                        zip.getInputStream(entry).use { input -> oldPhoto.outputStream().use { input.copyTo(it) } }
                        bytes = gatePhotoBackupBytes(context, oldPhoto.absolutePath)
                        if (bytes != null) reused++
                    }
                }
                if (bytes == null) {
                    missing.put(JSONObject().put("id", customer.optLong("id")).put("name", customer.optString("name")).put("addressSlot", label))
                    o.put("photoMissing", true).put("photoOriginalUri", source).put("photoUri", "")
                } else {
                    val path = "photos/customer_gate/photo_${entries.size + 1}.webp"
                    val file = File(work, "photo_${entries.size + 1}.webp").apply { writeBytes(bytes!!) }
                    entries += path to file
                    o.put("photoUri", path).remove("photoMissing")
                    o.remove("photoOriginalUri")
                }
            }
            for (i in 0 until customers.length()) {
                val c = customers.getJSONObject(i)
                pack(c, c, "chính")
                val addresses = c.optJSONArray("extraAddresses") ?: JSONArray()
                for (j in 0 until addresses.length()) pack(addresses.getJSONObject(j), c, "phụ ${j + 1}")
            }
            val manifest = JSONObject().put("format", "giaohangpro-customer-sync").put("schemaVersion", 3)
                .put("updatedAt", System.currentTimeMillis()).put("customers", customers.length())
                .put("images", entries.size).put("reusedImages", reused).put("missingPhotos", missing)
            val staged = File(work, "ready.zip")
            java.util.zip.ZipOutputStream(staged.outputStream().buffered()).use { zip ->
                fun entry(path: String, write: () -> Unit) { zip.putNextEntry(java.util.zip.ZipEntry(path)); write(); zip.closeEntry() }
                entry("customers.json") { zip.write(customers.toString().toByteArray(Charsets.UTF_8)) }
                entries.forEach { (path, file) -> entry(path) { file.inputStream().use { it.copyTo(zip) } } }
                entry("manifest.json") { zip.write(manifest.toString().toByteArray(Charsets.UTF_8)) }
            }
            // The archive is complete locally before the external provider is touched.
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out -> staged.inputStream().use { it.copyTo(out) } }
                    ?: error("Không mở được file đồng bộ đã chọn")
            } catch (e: Exception) {
                val recovered = previousCopied && previousFile.length() > 0 && runCatching {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Không mở được file cũ")
                    out.use { previousFile.inputStream().use { input -> input.copyTo(it) } }
                }.isSuccess
                error(if (recovered) "Ghi file bên ngoài thất bại; đã ghi trả bản cũ." else "Ghi file bên ngoài thất bại; cần kiểm tra lại file đã chọn.")
            }
            val details = (0 until missing.length()).joinToString("\n") {
                val item = missing.getJSONObject(it)
                "• ${item.optString("name")} (ID ${item.optLong("id")}, ${item.optString("addressSlot")})"
            }
            val status = "File bên ngoài đã cập nhật: ${customers.length()} khách, ${entries.size} ảnh; giữ lại $reused ảnh từ bản trước." +
                if (missing.length() > 0) "\nThiếu ${missing.length()} ảnh; dữ liệu khách vẫn đã lưu:\n$details" else "\nĐủ ảnh đang được tham chiếu."
            prefs(context).edit().putString("portable_status", status).apply()
        } catch (e: Exception) {
            prefs(context).edit().putString("portable_status", "File bên ngoài chưa cập nhật: ${e.message}").apply()
            throw e
        } finally { previousZip?.close(); work.deleteRecursively() }
    }

    fun importPortableBackup(context: Context, uri: android.net.Uri): Int {
        val temp = File(context.cacheDir, "customer_sync_restore_" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(raw)).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        val clean = e.name.replace('\\', '/')
                        if (clean.contains("..") || clean.startsWith("/")) error("File đồng bộ không hợp lệ")
                        val out = File(temp, clean)
                        check(out.canonicalPath.startsWith(temp.canonicalPath + File.separator)) { "File đồng bộ không hợp lệ" }
                        if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { zin.copyTo(it) } }
                        zin.closeEntry()
                    }
                }
            } ?: error("Không mở được file đồng bộ")
            val manifestFile = File(temp, "manifest.json")
            val customersFile = File(temp, "customers.json")
            check(manifestFile.exists() && customersFile.exists()) { "Không đúng file đồng bộ GiaoHangPro" }
            val manifest = JSONObject(manifestFile.readText())
            check(manifest.optString("format") == "giaohangpro-customer-sync") { "Không đúng file đồng bộ GiaoHangPro" }
            val customers = JSONArray(customersFile.readText())
            val restoredDir = File(context.filesDir, "customer_gate_photos").apply { mkdirs() }

            fun restorePhoto(path: String): String {
                if (path.isBlank()) return ""
                if (!path.startsWith("photos/customer_gate/")) {
                    // Old archives may contain a device-only URI, not photo bytes.
                    check(gatePhotoBackupBytes(context, path) != null) {
                        "File sao lưu chỉ có đường dẫn ảnh cũ, không có dữ liệu ảnh; chưa thay dữ liệu khách."
                    }
                    return path
                }
                val src = File(temp, path)
                check(src.isFile && src.length() > 0) { "File sao lưu thiếu ảnh $path; chưa thay dữ liệu khách." }
                val dst = File(restoredDir, "restored_" + java.util.UUID.randomUUID() + ".webp")
                check(gatePhotoBackupBytes(context, android.net.Uri.fromFile(src).toString()) != null) {
                    "Ảnh trong file sao lưu bị hỏng; chưa thay dữ liệu khách."
                }
                src.copyTo(dst, overwrite = true)
                return android.net.Uri.fromFile(dst).toString()
            }

            for (i in 0 until customers.length()) {
                val customer = customers.optJSONObject(i) ?: error("Dữ liệu khách hàng bị lỗi")
                check(customer.has("id")) { "Dữ liệu khách hàng bị lỗi" }
                fun restoreField(o: JSONObject) {
                    if (o.optBoolean("photoMissing") && o.optString("photoUri").isBlank()) {
                        // Explicitly incomplete backups restore customer data without pretending the image exists.
                        o.put("photoUri", o.optString("photoOriginalUri"))
                    } else o.put("photoUri", restorePhoto(o.optString("photoUri")))
                }
                restoreField(customer)
                val addresses = customer.optJSONArray("extraAddresses") ?: JSONArray()
                for (j in 0 until addresses.length()) {
                    val a = addresses.optJSONObject(j) ?: continue
                    restoreField(a)
                }
            }
            appPrefs(context).edit().putString("customers", customers.toString()).commit()
            setPortableUri(context, uri)
            dir(context).deleteRecursively()
            prefs(context).edit().remove(KEY_SESSIONS).apply()
            // Do not rewrite the source backup merely by restoring it.
            val missing = manifest.optJSONArray("missingPhotos")?.length() ?: 0
            prefs(context).edit().putString("portable_status", "Đã khôi phục ${customers.length()} khách; file nguồn báo thiếu $missing ảnh.").apply()
            return customers.length()
        } finally { temp.deleteRecursively() }
    }

    @Synchronized
    fun syncNow(context: Context): CustomerSyncSession? {
        val current = mapById(currentArray(context))
        val base = baseFile(context)
        if (!base.exists()) {
            val baseRoot = JSONObject().put("schemaVersion", 1).put("createdAt", System.currentTimeMillis())
                .put("customers", JSONArray(current.values.map { JSONObject(it.toString()) }))
            writeAtomic(base, baseRoot.toString())
            val session = CustomerSyncSession(System.currentTimeMillis(), System.currentTimeMillis(), current.size, 0, current.size)
            val marker = JSONObject().put("schemaVersion", 1).put("createdAt", session.createdAt)
                .put("upserts", JSONArray()).put("deletes", JSONArray())
            writeAtomic(deltaFile(context, session.id), marker.toString())
            writeSessions(context, listOf(session))
            if (portableUri(context) != null) writePortableBackup(context)
            return session
        }

        val previous = reconstruct(context)
        val upserts = current.filter { (id, value) -> previous[id]?.toString() != value.toString() }.values
        val deletes = previous.keys.filter { it !in current.keys }
        if (upserts.isEmpty() && deletes.isEmpty()) {
            // A prior portable write may have failed after the local snapshot committed.
            // Retry even when customer JSON is unchanged (for example after photo access recovers).
            if (portableUri(context) != null) writePortableBackup(context)
            return null
        }

        val now = System.currentTimeMillis()
        val root = JSONObject().put("schemaVersion", 1).put("createdAt", now)
            .put("upserts", JSONArray(upserts.map { JSONObject(it.toString()) }))
            .put("deletes", JSONArray(deletes))
        writeAtomic(deltaFile(context, now), root.toString())
        val session = CustomerSyncSession(now, now, upserts.size, deletes.size, current.size)
        val sessions = readSessions(context).apply { add(session) }
        writeSessions(context, sessions)
        compactIfNeeded(context)
        if (portableUri(context) != null) writePortableBackup(context)
        return session
    }

    private fun compactIfNeeded(context: Context) {
        val sessions = readSessions(context).sortedBy { it.createdAt }
        if (sessions.size <= MAX_SESSIONS) return
        val cut = sessions.size - MAX_SESSIONS
        val anchor = sessions[cut - 1]
        val state = reconstruct(context, anchor.id)
        val root = JSONObject().put("schemaVersion", 1).put("createdAt", anchor.createdAt)
            .put("customers", JSONArray(state.values.map { JSONObject(it.toString()) }))
        writeAtomic(baseFile(context), root.toString())
        sessions.take(cut).forEach { deltaFile(context, it.id).delete() }
        writeSessions(context, sessions.drop(cut))
    }

    @Synchronized
    fun restore(context: Context, sessionId: Long): Int {
        // Protect the current state first; a failed restore never destroys the previous good backup.
        syncNow(context)
        val state = reconstruct(context, sessionId)
        check(state.isNotEmpty() || currentArray(context).length() == 0) { "Phiên đồng bộ không đọc được" }
        val arr = JSONArray(state.values.map { JSONObject(it.toString()) })
        // Validate before committing to app data.
        JSONArray(arr.toString())
        appPrefs(context).edit().putString("customers", arr.toString()).commit()
        return arr.length()
    }

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        reschedule(context)
    }

    fun scheduledTimes(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_TIMES, null) ?: return listOf("08:00", "18:00", "23:00")
        return raw.split(",").map(String::trim).filter { Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(it) }.distinct().sorted()
    }

    fun setScheduledTimes(context: Context, times: List<String>) {
        prefs(context).edit().putString(KEY_TIMES, times.distinct().sorted().joinToString(",")).apply()
        reschedule(context)
    }

    private fun pending(context: Context, index: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, 9100 + index, Intent(context, CustomerSyncReceiver::class.java).setAction("CUSTOMER_LOCAL_SYNC").putExtra("slot", index), flags)

    fun reschedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until 24) pending(context, i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let(alarm::cancel)
        if (!isEnabled(context)) return
        scheduledTimes(context).forEachIndexed { index, time ->
            val (h, m) = time.split(":").map(String::toInt)
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val pi = pending(context, index, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) ?: return@forEachIndexed
            alarm.set(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
        }
    }

    fun nextSchedule(context: Context): String? {
        if (!isEnabled(context)) return null
        val now = Calendar.getInstance()
        return scheduledTimes(context).map { time ->
            val (h, m) = time.split(":").map(String::toInt)
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
        }.minByOrNull { it.timeInMillis }?.let { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(it.timeInMillis)) }
    }
}

class CustomerSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            CustomerLocalSync.reschedule(context)
            return
        }
        if (intent?.action == "CUSTOMER_LOCAL_SYNC") {
            runCatching { CustomerLocalSync.syncNow(context) }
            // Each slot schedules its next occurrence after firing.
            CustomerLocalSync.reschedule(context)
        }
    }
}
