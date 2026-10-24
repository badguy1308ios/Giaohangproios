package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GatePhotoArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val source = "content://gallery/document/old-photo"
    private val bytes = "valid photo bytes".toByteArray()

    @Test fun oldUriStillResolvesAfterSourcePermissionIsLost() {
        val stored = GatePhotoArchive.preserve(temp.root, source, { bytes.inputStream() }) { it.readBytes().contentEquals(bytes) }
        val reopened = GatePhotoArchive.preserve(temp.root, source, { throw SecurityException("revoked") }) { false }
        assertEquals(stored, reopened)
        assertArrayEquals(bytes, GatePhotoArchive.find(temp.root, source)!!.readBytes())
    }
    @Test fun interruptedCopyNeverPublishesPartialPhotoAndCanRetry() {
        try {
            GatePhotoArchive.preserve(temp.root, source, { object : java.io.InputStream() {
                override fun read(): Int = throw java.io.IOException("interrupted")
            } }) { true }
            fail("Expected failed write")
        } catch (_: java.io.IOException) { }
        assertNull(GatePhotoArchive.find(temp.root, source))
        assertTrue(temp.root.walkTopDown().none { it.extension == "part" })
        GatePhotoArchive.preserve(temp.root, source, { bytes.inputStream() }) { true }
        assertArrayEquals(bytes, GatePhotoArchive.find(temp.root, source)!!.readBytes())
    }
    @Test fun invalidAndEmptyFilesAreNotPublished() {
        for (data in listOf(byteArrayOf(), bytes)) {
            try {
                GatePhotoArchive.preserve(temp.root, source, { data.inputStream() }) { false }
                fail("Expected invalid photo rejection")
            } catch (_: IllegalStateException) { }
            assertNull(GatePhotoArchive.find(temp.root, source))
        }
    }
    @Test fun differentFullUrisNeverBorrowAnotherCustomersSameFilename() {
        GatePhotoArchive.preserve(temp.root, source, { bytes.inputStream() }) { true }
        assertNull(GatePhotoArchive.find(temp.root, "content://other-gallery/document/old-photo"))
        assertNull(GatePhotoArchive.find(temp.root, "content://gallery/another/old-photo"))
    }
    @Test fun deletedPhotoIsNotRecreatedByLegacyMigration() {
        val file = GatePhotoArchive.preserve(temp.root, source, { bytes.inputStream() }) { true }
        GatePhotoArchive.discard(temp.root, source)
        assertFalse(file.exists())
        assertNull(GatePhotoArchive.find(temp.root, source))
        try {
            GatePhotoArchive.preserve(temp.root, source, { bytes.inputStream() }) { true }
            fail("Replaced photo must stay deleted")
        } catch (_: IllegalStateException) { }
    }
}
