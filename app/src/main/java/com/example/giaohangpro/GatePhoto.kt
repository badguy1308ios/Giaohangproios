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
        context.contentResolver.openInputStream(source)?.use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read selected photo")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temporary.path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged photo" }
        check(temporary.renameTo(target))
        Uri.fromFile(target).toString()
    } catch (e: Exception) {
        temporary.delete()
        Log.w("GatePhoto", "Cannot store selected photo", e)
        null
    }
}

private fun openGatePhoto(context: Context, source: String): InputStream? {
    val uri = Uri.parse(source)
    return when (uri.scheme) {
        "content" -> context.contentResolver.openInputStream(uri)
        "file" -> uri.path?.let { File(it).inputStream() }
        null -> File(source).inputStream()
        else -> null
    }
}

private fun loadGatePhoto(context: Context, source: String): Bitmap? {
    if (source.isBlank()) return null
    try {
        // Read dimensions first. Never allocate/render a full camera-resolution image.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openGatePhoto(context, source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = gatePhotoSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = openGatePhoto(context, source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val orientation = runCatching {
            openGatePhoto(context, source)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
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
    } catch (e: Exception) {
        Log.w("GatePhoto", "Cannot read gate photo", e)
    } catch (e: OutOfMemoryError) {
        Log.w("GatePhoto", "Not enough memory for gate photo", e)
    }
    return null
}

@Composable
internal fun rememberGatePhoto(source: String): State<GatePhoto> {
    val context = LocalContext.current.applicationContext
    return produceState(GatePhoto(loading = source.isNotBlank()), context, source) {
        value = GatePhoto(loading = source.isNotBlank())
        val bitmap = withContext(Dispatchers.IO) { loadGatePhoto(context, source.trim()) }
        value = GatePhoto(bitmap = bitmap)
    }
}
