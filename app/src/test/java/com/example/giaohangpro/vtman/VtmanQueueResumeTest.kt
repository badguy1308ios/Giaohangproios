package com.example.giaohangpro.vtman

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VtmanQueueResumeTest {
    @Before fun beginSession() {
        VtmanQueueController.load(listOf("154069642893", "SGSIND09102026-01"))
        VtmanQueueController.setCallPoint(621.25f, 543.5f)
        VtmanQueueController.requestDataExport()
        VtmanQueueController.consumePendingDataExport()
    }

    @After fun clearSession() { VtmanQueueController.load(emptyList()) }

    @Test fun failureAndRestartKeepExactPointAndCurrentWaybill() {
        VtmanQueueController.stage(VtmanOrderRecord("154069642893", phone = "0900000000"))
        VtmanQueueController.fail("Chưa đọc được địa chỉ")
        VtmanQueueController.clearActiveForRetry()
        assertTrue(VtmanQueueController.requestDataExport())
        assertTrue(VtmanQueueController.consumePendingDataExport())
        assertEquals(CallPoint(621.25f, 543.5f), VtmanQueueController.callPoint())
        assertEquals("154069642893", VtmanQueueController.nextWaybill())
        assertFalse(VtmanQueueController.activeMatchesCurrent())
    }

    @Test fun statusUpdatesAndConnectionErrorPreserveSavedPoint() {
        VtmanQueueController.report("Đã tạm dừng")
        assertEquals(CallPoint(621.25f, 543.5f), VtmanQueueController.callPoint())
        VtmanQueueController.report("Đã dừng")
        VtmanQueueController.serviceUnavailable()
        assertTrue(VtmanQueueController.hasResumableSession())
        assertEquals(CallPoint(621.25f, 543.5f), VtmanQueueController.callPoint())
    }

    @Test fun newWaybillListStartsNewPointSelection() {
        VtmanQueueController.load(listOf("153913729574"))
        assertNull(VtmanQueueController.callPoint())
    }

    @Test fun missingServiceDoesNotBlockSavingAnOtherwiseCompleteOrder() {
        val record = VtmanOrderRecord("154069642893", shop = "Shop", phone = "0900000000",
            customer = "Khách", address = "Địa chỉ", goods = "Hàng", status = "TT505", cod = "0đ")
        assertTrue(VtmanQueueController.stage(record))
        assertTrue(VtmanQueueController.missingActiveFields().isEmpty())
        assertNotNull(VtmanQueueController.finalizeCurrent())
        assertEquals("SGSIND09102026-01", VtmanQueueController.nextWaybill())
        assertEquals(CallPoint(621.25f, 543.5f), VtmanQueueController.callPoint())
    }
}
