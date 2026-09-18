from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Coroutine launch used by backup screen.
if 'import kotlinx.coroutines.launch' not in s:
    s = s.replace('import kotlinx.coroutines.withContext\n', 'import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.launch\n', 1)

# Keep house photo per extra address as well as the primary customer photo.
s = s.replace(
'''data class CustomerAddress(\n    val address: String,\n    val latitude: String = "",\n    val longitude: String = "",\n    val isPrimary: Boolean = false\n)''',
'''data class CustomerAddress(\n    val address: String,\n    val latitude: String = "",\n    val longitude: String = "",\n    val isPrimary: Boolean = false,\n    val photoUri: String = ""\n)''', 1)

# Navigation.
s = s.replace(
'enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER, VTMAN_EXPORT }',
'enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER, VTMAN_EXPORT, CUSTOMER_BACKUP }', 1)
s = s.replace(
'            AppScreen.VTMAN_EXPORT -> screen = AppScreen.SETTINGS',
'            AppScreen.VTMAN_EXPORT -> screen = AppScreen.SETTINGS\n            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS', 1)
s = s.replace(
'AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER }, onVtmanExport = { screen = AppScreen.VTMAN_EXPORT })',
'AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER }, onVtmanExport = { screen = AppScreen.VTMAN_EXPORT }, onCustomerBackup = { screen = AppScreen.CUSTOMER_BACKUP })', 1)
s = s.replace(
'        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })',
'        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })', 1)

# Settings entry opens real feature.
s = s.replace(
'private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit, onVtmanExport: () -> Unit) {',
'private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit, onVtmanExport: () -> Unit, onCustomerBackup: () -> Unit) {', 1)
s = s.replace(
'SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Sao lưu khách hàng hoặc quản lý đơn hàng") { Toast.makeText(context,"Import / Export dữ liệu",Toast.LENGTH_SHORT).show() }',
'SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Nhập / xuất khách hàng dạng ZIP, gồm tọa độ và ảnh cổng") { onCustomerBackup() }', 1)

# Preserve per-address photo through the edit form.
s = s.replace(
'private data class AddressDraft(val address: String, val latitude: String, val longitude: String, val isPrimary: Boolean)',
'private data class AddressDraft(val address: String, val latitude: String, val longitude: String, val isPrimary: Boolean, val photoUri: String = "")', 1)
s = s.replace(
'add(AddressDraft(customer.address, customer.latitude, customer.longitude, true))\n                addAll(customer.extraAddresses.map { AddressDraft(it.address, it.latitude, it.longitude, false) })',
'add(AddressDraft(customer.address, customer.latitude, customer.longitude, true, customer.photoUri))\n                addAll(customer.extraAddresses.map { AddressDraft(it.address, it.latitude, it.longitude, false, it.photoUri) })', 1)
s = s.replace(
'CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false)',
'CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false, it.photoUri)', 1)

# Persist per-address photo.
s = s.replace(
'add(CustomerAddress(optString(a,"address"),optString(a,"latitude"),optString(a,"longitude"),a.optBoolean("isPrimary",false)))',
'add(CustomerAddress(optString(a,"address"),optString(a,"latitude"),optString(a,"longitude"),a.optBoolean("isPrimary",false),optString(a,"photoUri")))', 1)
s = s.replace(
'put("address",a.address); put("latitude",a.latitude); put("longitude",a.longitude); put("isPrimary",a.isPrimary)',
'put("address",a.address); put("latitude",a.latitude); put("longitude",a.longitude); put("isPrimary",a.isPrimary); put("photoUri",a.photoUri)', 1)

backup_ui = r'''

data class CustomerBackupResult(
    val total: Int,
    val added: Int,
    val updated: Int,
    val skipped: Int = 0,
    val images: Int = 0,
    val missingImages: Int = 0
)

@Composable
private fun CustomerBackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Sẵn sàng") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            busy = true
            status = "Đang nhập dữ liệu khách hàng..."
            runCatching { vm.importCustomerBackup(context, uri) }
                .onSuccess { r ->
                    status = "Nhập xong: +${r.added} khách mới, cập nhật ${r.updated}, ${r.images} ảnh"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    status = "Không nhập được file: ${e.message ?: "lỗi không xác định"}"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
            busy = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "Đang xuất dữ liệu khách hàng..."
            runCatching { vm.exportCustomerBackup(context, uri) }
                .onSuccess { r ->
                    status = "Xuất xong ${r.total} khách, ${r.images} ảnh${if (r.missingImages > 0) ", thiếu ${r.missingImages} ảnh" else ""}"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    status = "Không xuất được file: ${e.message ?: "lỗi không xác định"}"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("IMPORT / EXPORT DỮ LIỆU", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                Modifier.fillMaxWidth(), RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DỮ LIỆU KHÁCH HÀNG", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Định dạng ZIP tương thích cấu trúc: manifest.json + customers.json + photos/customer_gate/.", color = TextGray, fontSize = 12.sp)
                    Text("Đối chiếu theo SĐT. Khách đã có sẽ được gộp thêm tên phụ, SĐT phụ, địa chỉ, tọa độ và ảnh cổng mới; dữ liệu trống không ghi đè dữ liệu cũ.", color = TextGray, fontSize = 12.sp)
                }
            }

            Button(
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("NHẬP DỮ LIỆU KHÁCH HÀNG", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                    exportLauncher.launch("GiaoHangPro_KhachHang_${stamp}.zip")
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("XUẤT DỮ LIỆU KHÁCH HÀNG", fontWeight = FontWeight.Bold)
            }

            Card(
                Modifier.fillMaxWidth(), RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OrangeLight)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(status, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Text("Quy tắc nhập", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("• SĐT trùng → cập nhật/gộp vào khách hiện có.\n• Tên hoặc biệt danh mới → thêm vào tên phụ.\n• SĐT mới → thêm SĐT phụ.\n• Địa chỉ mới → thêm địa chỉ phụ.\n• Địa chỉ đã có nhưng thiếu tọa độ → bổ sung tọa độ.\n• Ảnh cổng trong ZIP → lưu vào đúng địa chỉ tương ứng.\n• Không tự xóa dữ liệu đang có.", color = TextGray, fontSize = 12.sp)
        }
    }
}
'''

marker = '@Composable\nprivate fun SettingsSection('
if 'private fun CustomerBackupScreen(' not in s:
    pos = s.find(marker)
    if pos < 0:
        raise SystemExit('SettingsSection marker not found')
    s = s[:pos] + backup_ui + '\n' + s[pos:]

# Add ZIP import/export methods to MainViewModel immediately before its final class brace.
methods = r'''

    suspend fun importCustomerBackup(context: android.content.Context, uri: android.net.Uri): CustomerBackupResult {
        val existing = customerState.toList()
        val pair = withContext(Dispatchers.IO) {
            val tempRoot = java.io.File(context.cacheDir, "customer_backup_import_${System.currentTimeMillis()}").apply { mkdirs() }
            var customersBytes: ByteArray? = null
            java.util.zip.ZipInputStream(context.contentResolver.openInputStream(uri) ?: error("Không mở được file ZIP")).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    val clean = entry.name.replace('\\', '/').trimStart('/')
                    if (!entry.isDirectory && clean == "customers.json") {
                        customersBytes = zin.readBytes()
                    } else if (!entry.isDirectory && clean.startsWith("photos/customer_gate/")) {
                        val name = java.io.File(clean).name
                        if (name.isNotBlank()) {
                            java.io.File(tempRoot, name).outputStream().use { out -> zin.copyTo(out) }
                        }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
            val raw = customersBytes ?: error("ZIP không có customers.json")
            val arr = org.json.JSONArray(String(raw, Charsets.UTF_8))
            val merged = existing.toMutableList()
            var nextId = (merged.maxOfOrNull { it.id } ?: 0L) + 1L
            var added = 0
            var updated = 0
            var imageCount = 0
            var missingImages = 0

            fun normPhone(v: String): String {
                val digits = v.filter(Char::isDigit)
                return when {
                    digits.startsWith("0084") && digits.length > 4 -> "0" + digits.drop(4)
                    digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
                    else -> digits
                }
            }
            fun normText(v: String): String = v.trim().lowercase(java.util.Locale.getDefault()).replace(Regex("\\s+"), " ")
            fun methods(p: org.json.JSONObject): Triple<Boolean, Boolean, Boolean> {
                val a = p.optJSONArray("methods") ?: org.json.JSONArray()
                val set = buildSet { for (x in 0 until a.length()) add(a.optString(x).uppercase()) }
                return Triple("CALL" in set, "ZALO" in set, "SMS" in set)
            }
            fun importPhoto(path: String?): String {
                if (path.isNullOrBlank()) return ""
                val source = java.io.File(tempRoot, java.io.File(path).name)
                if (!source.exists()) { missingImages++; return "" }
                val ext = source.extension.ifBlank { "webp" }
                val dest = java.io.File(context.filesDir, "customer_gate_import_${System.currentTimeMillis()}_${imageCount}.${ext}")
                source.copyTo(dest, overwrite = true)
                imageCount++
                return android.net.Uri.fromFile(dest).toString()
            }

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val namesArr = o.optJSONArray("names") ?: org.json.JSONArray()
                val names = buildList {
                    val primary = o.optString("name", "").trim()
                    if (primary.isNotBlank()) add(primary)
                    for (j in 0 until namesArr.length()) namesArr.optString(j).trim().takeIf(String::isNotBlank)?.let { if (none { x -> normText(x) == normText(it) }) add(it) }
                }
                val phonesArr = o.optJSONArray("phones") ?: org.json.JSONArray()
                val importedPhones = buildList {
                    for (j in 0 until phonesArr.length()) {
                        val po = phonesArr.optJSONObject(j) ?: continue
                        val num = po.optString("number", "").trim()
                        if (num.isNotBlank()) add(Triple(po, num, po.optBoolean("primary", false)))
                    }
                }
                if (importedPhones.isEmpty()) continue
                val phoneKeys = importedPhones.map { normPhone(it.second) }.filter(String::isNotBlank).toSet()
                val idx = merged.indexOfFirst { c ->
                    phoneKeys.contains(normPhone(c.phone)) || c.extraPhones.any { phoneKeys.contains(normPhone(it.number)) }
                }

                val addrArr = o.optJSONArray("addresses") ?: org.json.JSONArray()
                data class InAddr(val address: String, val lat: String, val lng: String, val primary: Boolean, val photo: String)
                val importedAddresses = buildList {
                    for (j in 0 until addrArr.length()) {
                        val a = addrArr.optJSONObject(j) ?: continue
                        val ad = a.optString("address", "").trim()
                        if (ad.isBlank()) continue
                        val lat = if (a.has("lat") && !a.isNull("lat")) a.optDouble("lat").takeIf { !it.isNaN() }?.toString().orEmpty() else ""
                        val lng = if (a.has("lng") && !a.isNull("lng")) a.optDouble("lng").takeIf { !it.isNaN() }?.toString().orEmpty() else ""
                        add(InAddr(ad, lat, lng, a.optBoolean("primary", false), importPhoto(a.optString("housePhoto", ""))))
                    }
                }
                val note = o.optString("note", "").trim()

                if (idx < 0) {
                    val primaryPhoneRec = importedPhones.firstOrNull { it.third } ?: importedPhones.first()
                    val pm = methods(primaryPhoneRec.first)
                    val primaryAddr = importedAddresses.firstOrNull { it.primary } ?: importedAddresses.firstOrNull()
                    val primaryName = names.firstOrNull().orEmpty().ifBlank { primaryPhoneRec.second }
                    val extrasPhones = importedPhones.filter { it !== primaryPhoneRec }.map { rec ->
                        val m = methods(rec.first); CustomerPhone(rec.second, if (m.first) "Gọi" else if (m.second) "Zalo" else "SMS", m.first, m.second, m.third)
                    }
                    val extrasAddr = importedAddresses.filter { it !== primaryAddr }.map { a -> CustomerAddress(a.address, a.lat, a.lng, false, a.photo) }
                    merged.add(Customer(
                        id = nextId++, name = primaryName, phone = primaryPhoneRec.second,
                        address = primaryAddr?.address.orEmpty(), latitude = primaryAddr?.lat.orEmpty(), longitude = primaryAddr?.lng.orEmpty(),
                        initials = createInitials(primaryName), aliases = names.drop(1), extraPhones = extrasPhones, extraAddresses = extrasAddr,
                        note = note, primaryCanCall = pm.first, primaryCanZalo = pm.second, primaryCanSms = pm.third,
                        photoUri = primaryAddr?.photo.orEmpty()
                    ))
                    added++
                } else {
                    var c = merged[idx]
                    var changed = false
                    val aliases = c.aliases.toMutableList()
                    names.forEach { n ->
                        if (normText(n) != normText(c.name) && aliases.none { normText(it) == normText(n) }) { aliases.add(n); changed = true }
                    }

                    var primaryCall = c.primaryCanCall
                    var primaryZalo = c.primaryCanZalo
                    var primarySms = c.primaryCanSms
                    val extrasPhones = c.extraPhones.toMutableList()
                    importedPhones.forEach { rec ->
                        val key = normPhone(rec.second)
                        val m = methods(rec.first)
                        if (key == normPhone(c.phone)) {
                            val nc = primaryCall || m.first; val nz = primaryZalo || m.second; val ns = primarySms || m.third
                            if (nc != primaryCall || nz != primaryZalo || ns != primarySms) { primaryCall = nc; primaryZalo = nz; primarySms = ns; changed = true }
                        } else {
                            val pi = extrasPhones.indexOfFirst { normPhone(it.number) == key }
                            if (pi < 0 && key.isNotBlank()) {
                                extrasPhones.add(CustomerPhone(rec.second, if (m.first) "Gọi" else if (m.second) "Zalo" else "SMS", m.first, m.second, m.third)); changed = true
                            } else if (pi >= 0) {
                                val old = extrasPhones[pi]
                                val nw = old.copy(canCall = old.canCall || m.first, canZalo = old.canZalo || m.second, canSms = old.canSms || m.third)
                                if (nw != old) { extrasPhones[pi] = nw; changed = true }
                            }
                        }
                    }

                    var mainAddress = c.address
                    var mainLat = c.latitude
                    var mainLng = c.longitude
                    var mainPhoto = c.photoUri
                    val extrasAddr = c.extraAddresses.toMutableList()
                    importedAddresses.forEach { a ->
                        if (normText(a.address) == normText(mainAddress) && mainAddress.isNotBlank()) {
                            if (mainLat.isBlank() && a.lat.isNotBlank()) { mainLat = a.lat; changed = true }
                            if (mainLng.isBlank() && a.lng.isNotBlank()) { mainLng = a.lng; changed = true }
                            if (a.photo.isNotBlank() && a.photo != mainPhoto) { mainPhoto = a.photo; changed = true }
                        } else {
                            val ai = extrasAddr.indexOfFirst { normText(it.address) == normText(a.address) }
                            if (ai >= 0) {
                                val old = extrasAddr[ai]
                                val nw = old.copy(
                                    latitude = if (old.latitude.isBlank()) a.lat else old.latitude,
                                    longitude = if (old.longitude.isBlank()) a.lng else old.longitude,
                                    photoUri = if (a.photo.isNotBlank()) a.photo else old.photoUri
                                )
                                if (nw != old) { extrasAddr[ai] = nw; changed = true }
                            } else if (mainAddress.isBlank()) {
                                mainAddress = a.address; mainLat = a.lat; mainLng = a.lng; mainPhoto = a.photo; changed = true
                            } else {
                                extrasAddr.add(CustomerAddress(a.address, a.lat, a.lng, false, a.photo)); changed = true
                            }
                        }
                    }
                    var mergedNote = c.note
                    if (note.isNotBlank() && !mergedNote.contains(note, ignoreCase = true)) {
                        mergedNote = if (mergedNote.isBlank()) note else mergedNote + "\n" + note
                        changed = true
                    }
                    if (changed) {
                        c = c.copy(
                            aliases = aliases, extraPhones = extrasPhones, extraAddresses = extrasAddr,
                            latitude = mainLat, longitude = mainLng, photoUri = mainPhoto, note = mergedNote,
                            primaryCanCall = primaryCall, primaryCanZalo = primaryZalo, primaryCanSms = primarySms
                        )
                        merged[idx] = c
                        updated++
                    }
                }
            }
            tempRoot.deleteRecursively()
            CustomerBackupResult(arr.length(), added, updated, arr.length() - added - updated, imageCount, missingImages) to merged
        }
        customerState.clear(); customerState.addAll(pair.second); savePersistentData()
        return pair.first
    }

    suspend fun exportCustomerBackup(context: android.content.Context, uri: android.net.Uri): CustomerBackupResult {
        val snapshot = customerState.toList()
        return withContext(Dispatchers.IO) {
            data class PhotoJob(val source: String, val zipPath: String)
            val photoJobs = mutableListOf<PhotoJob>()
            var photoIndex = 0
            var missingImages = 0

            fun registerPhoto(source: String): String? {
                if (source.isBlank()) return null
                photoIndex++
                val path = "photos/customer_gate/customer_gate_${photoIndex}.webp"
                photoJobs.add(PhotoJob(source, path))
                return path
            }
            fun methods(call: Boolean, zalo: Boolean, sms: Boolean): org.json.JSONArray = org.json.JSONArray().apply {
                if (call) put("CALL"); if (sms) put("SMS"); if (zalo) put("ZALO")
            }
            fun phoneJson(number: String, primary: Boolean, call: Boolean, zalo: Boolean, sms: Boolean) = org.json.JSONObject().apply {
                put("number", number); put("primary", primary); put("methods", methods(call, zalo, sms))
            }
            fun addrJson(a: String, lat: String, lng: String, primary: Boolean, photo: String) = org.json.JSONObject().apply {
                put("address", a)
                lat.toDoubleOrNull()?.let { put("lat", it) }
                lng.toDoubleOrNull()?.let { put("lng", it) }
                put("primary", primary)
                registerPhoto(photo)?.let { put("housePhoto", it) }
            }

            val customersJson = org.json.JSONArray()
            snapshot.forEach { c ->
                customersJson.put(org.json.JSONObject().apply {
                    put("id", "ghp-${c.id}")
                    put("name", c.name)
                    put("names", org.json.JSONArray(listOf(c.name) + c.aliases))
                    put("note", c.note)
                    put("phones", org.json.JSONArray().apply {
                        put(phoneJson(c.phone, true, c.primaryCanCall, c.primaryCanZalo, c.primaryCanSms))
                        c.extraPhones.forEach { p -> put(phoneJson(p.number, false, p.canCall, p.canZalo, p.canSms)) }
                    })
                    put("addresses", org.json.JSONArray().apply {
                        if (c.address.isNotBlank()) put(addrJson(c.address, c.latitude, c.longitude, true, c.photoUri))
                        c.extraAddresses.forEach { a -> put(addrJson(a.address, a.latitude, a.longitude, false, a.photoUri)) }
                    })
                })
            }

            val manifestEntries = org.json.JSONArray()
            fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            fun addManifest(path: String, bytes: ByteArray) {
                manifestEntries.put(org.json.JSONObject().apply { put("path", path); put("bytes", bytes.size); put("sha256", sha256(bytes)) })
            }
            fun readPhoto(source: String): ByteArray? = runCatching {
                val u = android.net.Uri.parse(source)
                when (u.scheme) {
                    "content" -> context.contentResolver.openInputStream(u)?.use { it.readBytes() }
                    "file" -> java.io.File(requireNotNull(u.path)).takeIf { it.exists() }?.readBytes()
                    else -> java.io.File(source).takeIf { it.exists() }?.readBytes()
                }
            }.getOrNull()
            fun toWebp(bytes: ByteArray): ByteArray? = runCatching {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                bmp.recycle()
                out.toByteArray()
            }.getOrNull()

            val customersBytes = customersJson.toString().toByteArray(Charsets.UTF_8)
            val evidenceBytes = "[]".toByteArray(Charsets.UTF_8)
            var writtenImages = 0
            context.contentResolver.openOutputStream(uri, "w")?.use { rawOut ->
                java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(rawOut)).use { zout ->
                    fun writeEntry(path: String, bytes: ByteArray, manifest: Boolean = true) {
                        zout.putNextEntry(java.util.zip.ZipEntry(path)); zout.write(bytes); zout.closeEntry()
                        if (manifest) addManifest(path, bytes)
                    }
                    writeEntry("customers.json", customersBytes)
                    writeEntry("transfer_evidence.json", evidenceBytes)
                    photoJobs.forEach { job ->
                        val sourceBytes = readPhoto(job.source)
                        val webp = sourceBytes?.let(::toWebp)
                        if (webp != null) { writeEntry(job.zipPath, webp); writtenImages++ } else missingImages++
                    }
                    val manifest = org.json.JSONObject().apply {
                        put("format", "giaohang-customer-backup")
                        put("schemaVersion", 1)
                        put("createdAt", System.currentTimeMillis())
                        put("customers", snapshot.size)
                        put("transferEvidence", 0)
                        put("images", writtenImages)
                        put("missingImages", missingImages)
                        put("entries", manifestEntries)
                    }.toString().toByteArray(Charsets.UTF_8)
                    writeEntry("manifest.json", manifest, manifest = false)
                }
            } ?: error("Không tạo được file ZIP")
            CustomerBackupResult(snapshot.size, 0, 0, 0, writtenImages, missingImages)
        }
    }
'''

if 'suspend fun importCustomerBackup(' not in s:
    tail = '        if(changed) savePersistentData()\n    }\n}'
    pos = s.rfind(tail)
    if pos < 0:
        raise SystemExit('MainViewModel tail marker not found')
    insert_at = pos + len('        if(changed) savePersistentData()\n    }')
    s = s[:insert_at] + methods + s[insert_at:]

p.write_text(s, encoding='utf-8')
print('customer backup import/export patch applied')
