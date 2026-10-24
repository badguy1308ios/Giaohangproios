package com.example.giaohangpro

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Stable source-to-file binding also works when an old sync version restores the old URI. */
internal object GatePhotoArchive {
    fun sourceKey(source: String): String = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun target(filesDir: File, source: String): File =
        File(filesDir, "customer_gate_photos/legacy_${sourceKey(source)}.img")

    private fun retired(filesDir: File, source: String) = File(filesDir, "customer_gate_photos/retired_${sourceKey(source)}")

    @Synchronized
    fun discard(filesDir: File, source: String) {
        val marker = retired(filesDir, source)
        check(marker.parentFile.isDirectory || marker.parentFile.mkdirs())
        // Prevent a concurrent legacy migration from recreating a deliberately replaced image.
        if (!marker.exists()) check(marker.createNewFile())
        val photo = target(filesDir, source)
        check(!photo.exists() || photo.delete()) { "Cannot delete replaced archive photo" }
    }

    fun find(filesDir: File, source: String): File? = target(filesDir, source)
        .takeIf { it.isFile && it.length() > 0 }

    @Synchronized
    fun preserve(filesDir: File, source: String, open: () -> InputStream?, valid: (File) -> Boolean): File {
        check(!retired(filesDir, source).exists()) { "Photo was explicitly replaced" }
        find(filesDir, source)?.let { return it }
        val dest = target(filesDir, source)
        check(dest.parentFile.isDirectory || dest.parentFile.mkdirs())
        val temp = File(dest.parentFile, dest.name + ".part")
        try {
            open()?.use { input ->
                java.io.FileOutputStream(temp).use { output -> input.copyTo(output); output.fd.sync() }
            } ?: error("Photo source unavailable")
            check(temp.length() > 0 && valid(temp)) { "Photo source is empty or undecodable" }
            check(temp.renameTo(dest)) { "Photo archive could not be committed" }
            return dest
        } finally { temp.delete() }
    }
}
