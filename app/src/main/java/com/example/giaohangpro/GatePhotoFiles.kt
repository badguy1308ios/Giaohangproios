package com.example.giaohangpro

import java.io.File

/** Rebind only the exact app-photo relative path; never guess by customer name. */
internal object GatePhotoFiles {
    fun local(filesDir: File, path: String): File? {
        val direct = File(path)
        if (direct.isAbsolute && direct.isFile && direct.length() > 0) return direct
        val relative = when {
            !direct.isAbsolute -> path
            "/files/" in path -> path.substringAfter("/files/")
            path.startsWith("/customer_photos/") -> path.removePrefix("/customer_photos/")
            else -> return null
        }
        if (!(relative.startsWith("customer_gate_photos/") ||
                (relative.startsWith("customer_gate_") && '/' !in relative))) return null
        val candidate = File(filesDir, relative).canonicalFile
        if (!candidate.path.startsWith(filesDir.canonicalPath + File.separator)) return null
        return candidate.takeIf { it.isFile && it.length() > 0 }
    }
}
