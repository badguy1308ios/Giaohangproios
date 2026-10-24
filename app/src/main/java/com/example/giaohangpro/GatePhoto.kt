package com.example.giaohangpro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

internal data class GatePhoto(val bitmap: Bitmap? = null, val loading: Boolean = false)

internal suspend fun storeGatePhoto(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "customer_gate_photos")
    val target = File(dir, "selected_${java.util.UUID.randomUUID()}.img")
    val temporary = File(dir, target.name + ".part")
    try {
        check(dir.isDirectory || dir.mkdirs())
        openGatePhoto(context, source.toString())?.use { input ->
            java.io.FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() }
        } ?: error("Cannot read selected photo")
        val decoded = loadGatePhoto(context, temporary.absolutePath)
            ?: error("Unsupported or damaged photo")
        decoded.recycle()
        check(temporary.renameTo(target))
        "customer_gate_photos/${target.name}"
    } catch (e: Exception) {
        temporary.delete()
        Log.w("GatePhoto", "Cannot store selected photo", e)
        null
    }
}

internal fun openGatePhoto(context: Context, source: String): InputStream? {
    GatePhotoArchive.find(context.filesDir, source)?.let { return it.inputStream() }
    val uri = Uri.parse(source)
    val path = if (uri.scheme == null) source else uri.path.orEmpty()
    // Reuse restored app-owned bytes even when the old absolute path/provider
    // authority points at a previous installation. Exact path only.
    if (uri.scheme == null || uri.scheme == "file" ||
        (uri.scheme == "content" && uri.authority.orEmpty().endsWith(".fileprovider"))) {
        GatePhotoFiles.local(context.filesDir, path)?.let { return it.inputStream() }
    }
    return if (uri.scheme == "content") context.contentResolver.openInputStream(uri) else null
}

internal fun gatePhotoBackupBytes(context: Context, source: String): ByteArray? {
    val bitmap = loadGatePhoto(context, source) ?: return null
    return try {
        java.io.ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 90, output))
            output.toByteArray()
        }
    } finally { bitmap.recycle() }
}

private fun decodeGatePhoto(context: Context, source: String, stage: (String) -> Unit = {}): Bitmap? {
    stage("OPEN_DIMENSIONS")
    // Read dimensions first. Never allocate/render a full camera-resolution image.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val header = openGatePhoto(context, source) ?: run { stage("SOURCE_NOT_FOUND"); return null }
    stage("DECODE_DIMENSIONS")
    header.use { BitmapFactory.decodeStream(it, null, bounds) }
    stage("IMAGE_DIMENSIONS_INVALID")
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = gatePhotoSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    stage("OPEN_PIXELS")
    val pixels = openGatePhoto(context, source) ?: run { stage("SOURCE_DISAPPEARED"); return null }
    stage("DECODE_PIXELS")
    val bitmap = pixels.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    stage("READ_ORIENTATION")
    val orientation = runCatching {
        openGatePhoto(context, source)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    stage("APPLY_ORIENTATION")
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}

internal fun loadGatePhoto(context: Context, source: String): Bitmap? {
    if (source.isBlank()) return null
    var stage = "START"
    return try {
        decodeGatePhoto(context, source) { stage = it }.also {
            if (it == null) GatePhotoDebug.failure(context, source, stage, "NO_IMAGE_RETURNED")
        }
    } catch (e: Exception) {
        GatePhotoDebug.failure(context, source, stage, e.javaClass.simpleName)
        null
    } catch (e: OutOfMemoryError) {
        GatePhotoDebug.failure(context, source, stage, "OutOfMemoryError")
        null
    }
}

internal fun gatePhotoDiagnosis(context: Context, source: String): String {
    if (source.isBlank()) return "CHƯA GẮN ẢNH: bản ghi không có đường dẫn"
    val uri = Uri.parse(source)
    val kind = when (uri.scheme) {
        "content" -> if (uri.authority == context.packageName + ".fileprovider") "ảnh chụp nội bộ" else "thư viện/nhà cung cấp bên ngoài"
        "file" -> "đường dẫn file cũ"
        null -> "đường dẫn nội bộ"
        else -> "định dạng đường dẫn ${uri.scheme}"
    }
    val archived = GatePhotoArchive.find(context.filesDir, source) != null
    val result = try {
        val rawFile = when (uri.scheme) {
            null -> File(source).let { if (it.isAbsolute) it else File(context.filesDir, source) }
            "file" -> uri.path?.let(::File)
            "content" -> if (uri.authority == context.packageName + ".fileprovider")
                uri.path?.removePrefix("/customer_photos/")?.let { File(context.filesDir, it) } else null
            else -> null
        }
        if (!archived && rawFile?.isFile == true && rawFile.length() == 0L)
            return "$kind · FILE RỖNG (0 byte)"
        val count = openGatePhoto(context, source)?.use { input ->
            var count = 0L
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; count += n }
            count
        }
        when {
            count == null -> "KHÔNG TÌM THẤY FILE tại đường dẫn đã lưu"
            count == 0L -> "FILE RỖNG (0 byte)"
            else -> {
                val bitmap = decodeGatePhoto(context, source)
                if (bitmap == null) "CÓ FILE ($count byte) nhưng không giải mã được định dạng/nội dung ảnh"
                else { bitmap.recycle(); "ĐỌC TỐT ($count byte)" }
            }
        }
    } catch (e: SecurityException) { "MẤT QUYỀN ĐỌC nguồn ảnh" }
    catch (e: java.io.FileNotFoundException) { "NGUỒN KHÔNG MỞ ĐƯỢC: file mất/di chuyển hoặc nhà cung cấp từ chối" }
    catch (e: OutOfMemoryError) { "THIẾU BỘ NHỚ khi giải mã; không đồng nghĩa file bị mất" }
    catch (e: Exception) { "LỖI ĐỌC: ${e.javaClass.simpleName}" }
    return "$kind · bản lưu nội bộ cũ: ${if (archived) "có" else "không"} · $result"
}

internal fun gatePhotoAudit(context: Context): String {
    val raw = context.getSharedPreferences("giaohangpro_persistent_data_v1", Context.MODE_PRIVATE)
        .getString("customers", "[]") ?: "[]"
    val customers = org.json.JSONArray(raw)
    return buildString {
        appendLine("GiaoHangPro ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} · kiểm tra ảnh ${java.util.Date()}")
        appendLine("Khách: ${customers.length()}; không sửa/xóa dữ liệu trong lúc kiểm tra.")
        for (i in 0 until customers.length()) {
            val c = customers.getJSONObject(i)
            fun report(label: String, source: String) {
                if (source.isNotBlank()) {
                    appendLine("ID ${c.optLong("id")} $label [${GatePhotoArchive.sourceKey(source).take(12)}]: ${gatePhotoDiagnosis(context, source)}")
                }
            }
            report("chính", c.optString("photoUri"))
            val extras = c.optJSONArray("extraAddresses")
            if (extras != null) for (j in 0 until extras.length()) report("địa chỉ ${j + 2}", extras.getJSONObject(j).optString("photoUri"))
        }
        appendLine("\nDEBUG TẠI BỘ ĐỌC ẢNH (40 lỗi gần nhất, gộp lỗi lặp):")
        appendLine(GatePhotoDebug.report(context))
    }
}

@Composable
internal fun rememberGatePhoto(source: String): State<GatePhoto> {
    val context = LocalContext.current.applicationContext
    return produceState(GatePhoto(loading = source.isNotBlank()), context, source) {
        value = GatePhoto(loading = source.isNotBlank())
        val bitmap = withContext(Dispatchers.IO) {
            preserveLegacyGatePhotos(context, listOf(source.trim()))
            loadGatePhoto(context, source.trim())
        }
        value = GatePhoto(bitmap = bitmap)
    }
}

/** Protect legacy gallery/external-file references without changing customer records or coordinates. */
internal fun preserveLegacyGatePhotos(context: Context, sources: List<String>) {
    sources.filter(String::isNotBlank).distinct().forEach { source ->
        val uri = Uri.parse(source)
        val localPath = uri.path.orEmpty()
        val external = (uri.scheme == "content" && uri.authority != context.packageName + ".fileprovider") ||
            ((uri.scheme == "file" || (uri.scheme == null && File(source).isAbsolute)) &&
                !localPath.startsWith(context.filesDir.absolutePath + "/"))
        if (external) runCatching {
            GatePhotoArchive.preserve(context.filesDir, source, { openGatePhoto(context, source) }) { file ->
                val bitmap = loadGatePhoto(context, file.absolutePath)
                bitmap?.recycle()
                bitmap != null
            }
        }.onFailure { Log.w("GatePhoto", "Legacy photo could not be preserved: ${it.javaClass.simpleName}") }
    }
}
