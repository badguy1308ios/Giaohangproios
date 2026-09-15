package com.example.giaohangpro.vtman

/** A user-requested start waits for Android binding, never changes accessibility settings. */
internal class VtmanConnectionWait(private val timeoutMs: Long = 8000L) {
    enum class Result { IDLE, DISABLED, WAITING, READY, TIMED_OUT }
    private var startedAt: Long? = null

    fun start(now: Long) { if (startedAt == null) startedAt = now }
    fun cancel() { startedAt = null }
    fun poll(enabled: Boolean, connected: Boolean, now: Long): Result {
        val start = startedAt ?: return Result.IDLE
        val result = when {
            !enabled -> Result.DISABLED
            connected -> Result.READY
            now - start >= timeoutMs -> Result.TIMED_OUT
            else -> Result.WAITING
        }
        if (result != Result.WAITING) cancel()
        return result
    }
}
