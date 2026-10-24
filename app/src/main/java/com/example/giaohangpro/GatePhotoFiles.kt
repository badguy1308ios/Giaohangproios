package com.example.giaohangpro

import java.io.File

/** Rebind only the exact app-photo relative path; never guess by customer name. */
internal object GatePhotoFiles {
    /** Only application-owned photo files may be deleted, never a gallery original. */
    fun owned(filesDir: File, path: String): File? {
        val root = filesDir.canonicalFile
        val relative = when {
            path.startsWith(root.path + "/") -> path.removePrefix(root.path + "/")
            path.startsWith("/customer_photos/") -> path.removePrefix("/customer_photos/")
            "/files/" in path -> path.substringAfter("/files/")
            !File(path).isAbsolute -> path
            else -> return null
        }
        if (relative.split('/').any { it == ".." || it == "." }) return null
        if (!(relative.startsWith("customer_gate_photos/") ||
                (relative.startsWith("customer_gate_") && '/' !in relative))) return null
        val candidate = File(root, relative).canonicalFile
        return candidate.takeIf { it.path.startsWith(root.path + "/") && !it.isDirectory }
    }

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
