package com.example.giaohangpro

internal data class BackupPhotoReference(val customerId: Long, val sourceKey: String, val path: String)

internal object PhotoBackupFallback {
    // Customer/address position alone is not proof that this is the same photo.
    fun match(customerId: Long, sourceKey: String, previous: List<BackupPhotoReference>): String? =
        previous.firstOrNull {
            sourceKey.isNotBlank() && it.customerId == customerId && it.sourceKey == sourceKey &&
                it.path.startsWith("photos/customer_gate/") && !it.path.contains("..")
        }?.path
}
