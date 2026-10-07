package com.example.giaohangpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatePhotoSizingTest {
    @Test fun cameraPhotosAreBoundedInBothOrientations() {
        for ((width, height) in listOf(12000 to 9000, 9000 to 12000, 4000 to 3000, 1281 to 720, Int.MAX_VALUE to 1)) {
            val sample = gatePhotoSampleSize(width, height)
            assertTrue((maxOf(width, height).toLong() + sample - 1) / sample <= 1280)
            assertEquals(0, sample and (sample - 1))
        }
    }

    @Test fun smallPhotosKeepTheirResolution() {
        assertEquals(1, gatePhotoSampleSize(800, 600))
        assertEquals(1, gatePhotoSampleSize(1280, 720))
    }

    @Test fun invalidDimensionsDoNotLoop() {
        assertEquals(1, gatePhotoSampleSize(-1, -1))
        assertEquals(1, gatePhotoSampleSize(0, 100))
    }
}
