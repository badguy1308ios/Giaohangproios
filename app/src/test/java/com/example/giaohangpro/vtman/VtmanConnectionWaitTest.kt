package com.example.giaohangpro.vtman

import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.giaohangpro.vtman.VtmanConnectionWait.Result.*

class VtmanConnectionWaitTest {
    @Test fun enabledServiceCanConnectLaterAndStartOnlyOnce() {
        val wait = VtmanConnectionWait()
        wait.start(100)
        assertEquals(WAITING, wait.poll(true, false, 100))
        assertEquals(READY, wait.poll(true, true, 600))
        assertEquals(IDLE, wait.poll(true, true, 700))
    }
    @Test fun disabledPermissionIsDistinctFromDisconnectedService() {
        val wait = VtmanConnectionWait()
        wait.start(100)
        assertEquals(DISABLED, wait.poll(false, false, 100))
        assertEquals(IDLE, wait.poll(true, true, 200))
    }

    @Test fun liveConnectionOverridesStaleDisabledSetting() {
        val wait = VtmanConnectionWait()
        wait.start(100)
        assertEquals(READY, wait.poll(false, true, 100))
        assertEquals(IDLE, wait.poll(false, true, 200))
    }
    @Test fun repeatedStartCannotExtendTimeoutAndLateConnectionStillStarts() {
        val wait = VtmanConnectionWait()
        wait.start(100)
        wait.start(7000)
        assertEquals(WAITING, wait.poll(true, false, 8099))
        assertEquals(TIMED_OUT, wait.poll(true, false, 8100))
        assertEquals(TIMED_OUT, wait.poll(true, false, 15000))
        assertEquals(READY, wait.poll(true, true, 16000))
        assertEquals(IDLE, wait.poll(true, true, 16100))
    }
    @Test fun stopOrCloseCancelsDelayedStart() {
        val wait = VtmanConnectionWait()
        wait.start(100)
        wait.cancel()
        assertEquals(IDLE, wait.poll(true, true, 500))
    }
    @Test fun permissionRevokedDuringWaitDoesNotStartStaleService() {
        val wait = VtmanConnectionWait()
        wait.start(100)
        assertEquals(WAITING, wait.poll(true, false, 200))
        assertEquals(DISABLED, wait.poll(false, true, 300))
    }
    @Test fun connectionAfterTimeoutDoesNotRequireAnotherStart() {
        val wait = VtmanConnectionWait()
        wait.start(0)
        assertEquals(TIMED_OUT, wait.poll(true, false, 8000))
        assertEquals(READY, wait.poll(true, true, 9500))
        assertEquals(IDLE, wait.poll(true, true, 9600))
    }
}
