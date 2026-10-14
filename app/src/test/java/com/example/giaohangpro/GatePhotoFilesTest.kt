package com.example.giaohangpro

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GatePhotoFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun photo(path: String): File = File(temp.root, path).apply {
        parentFile.mkdirs(); writeText("photo bytes")
    }
    @Test fun stableRelativeReferenceResolves() {
        val file = photo("customer_gate_photos/selected_123.img")
        assertEquals(file, GatePhotoFiles.local(temp.root, "customer_gate_photos/selected_123.img"))
    }
    @Test fun restoredFileRebindsOldInstallationRoot() {
        val file = photo("customer_gate_photos/restored_123.webp")
        assertEquals(file, GatePhotoFiles.local(temp.root, "/data/user/0/old.app/files/customer_gate_photos/restored_123.webp"))
    }
    @Test fun cameraProviderPathResolvesExactFile() {
        val file = photo("customer_gate_42_123.jpg")
        assertEquals(file, GatePhotoFiles.local(temp.root, "/customer_photos/customer_gate_42_123.jpg"))
    }
    @Test fun missingPhotoDoesNotSelectAnotherCustomerPhoto() {
        photo("customer_gate_43_123.jpg")
        assertNull(GatePhotoFiles.local(temp.root, "/customer_photos/customer_gate_42_123.jpg"))
    }
    @Test fun emptyFileIsNotAStoredPhoto() {
        photo("customer_gate_photos/empty.img").writeBytes(byteArrayOf())
        assertNull(GatePhotoFiles.local(temp.root, "customer_gate_photos/empty.img"))
    }
    @Test fun traversalCannotRebindOutsideFilesDirectory() {
        assertNull(GatePhotoFiles.local(temp.root, "customer_gate_photos/../../escape.jpg"))
    }
    @Test fun existingExternalFileRemainsReadableForImport() {
        val file = photo("import_cache.jpg")
        assertEquals(file, GatePhotoFiles.local(temp.root, file.absolutePath))
    }
}
