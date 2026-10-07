package com.example.giaohangpro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        return openGatePhoto(context, source)?.use { BitmapFactory.decodeStream(it, null, options) }
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
