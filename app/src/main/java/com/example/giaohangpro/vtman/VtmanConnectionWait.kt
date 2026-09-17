package com.example.giaohangpro.vtman

/** A user-requested start waits for Android binding, never changes accessibility settings. */
internal class VtmanConnectionWait(private val timeoutMs: Long = 8000L) {
    enum class Result { IDLE, DISABLED, WAITING, READY, TIMED_OUT }
    private var startedAt: Long? = null

    fun start(now: Long) { if (startedAt == null) startedAt = now }
    fun cancel() { startedAt = null }
    fun poll(enabled: Boolean, connected: Boolean, now: Long): Result {
        val start = startedAt ?: return Result.IDLE
        return when {
            // Kết nối sống là bằng chứng chắc chắn nhất. Một số máy HiOS trả về
            // danh sách Settings.Secure chậm hoặc sai dù service đã kết nối.
            connected -> Result.READY.also { cancel() }
            !enabled -> Result.DISABLED.also { cancel() }
            // TIMED_OUT chỉ đổi thông báo thành "đang chờ lâu". Yêu cầu Chạy vẫn
            // được giữ để onServiceConnected đến trễ vẫn tự khởi động Export.
            now - start >= timeoutMs -> Result.TIMED_OUT
            else -> Result.WAITING
        }
    }
}
