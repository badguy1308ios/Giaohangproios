package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Test

class PhotoBackupFallbackTest {
    private val reference = BackupPhotoReference(42, "same-photo", "photos/customer_gate/photo_1.webp")
    @Test fun recoversOnlyTheSamePhotoForTheSameCustomer() {
        assertEquals(reference.path, PhotoBackupFallback.match(42, "same-photo", listOf(reference)))
        assertNull(PhotoBackupFallback.match(43, "same-photo", listOf(reference)))
    }
    @Test fun replacedPhotoDoesNotResurrectAnOldImage() {
        assertNull(PhotoBackupFallback.match(42, "new-photo", listOf(reference)))
    }
    @Test fun olderBackupWithoutIdentityCannotBeGuessedByCustomerId() {
        assertNull(PhotoBackupFallback.match(42, "same-photo", listOf(reference.copy(sourceKey = ""))))
        assertNull(PhotoBackupFallback.match(42, "", listOf(reference.copy(sourceKey = ""))))
    }
    @Test fun archiveTraversalIsRejected() {
        assertNull(PhotoBackupFallback.match(42, "same-photo", listOf(reference.copy(path = "photos/customer_gate/../../wrong.webp"))))
    }
}
