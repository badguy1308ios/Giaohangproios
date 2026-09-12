package com.example.giaohangpro

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView

/**
 * Camera scanner riêng cho VTMan Export.
 * - Camera luôn mở và quét liên tục nhiều QR / barcode.
 * - Mã mới được giữ trong phiên và trả về VTMan Export khi đóng camera.
 * - Mã trùng hiện Toast nhưng camera không đóng.
 * - Bấm X / Back để đóng; 30 giây không đọc được mã nào thì tự đóng.
 */
class ContinuousVtmanScanActivity : Activity() {
    companion object {
        const val EXTRA_TEXTBOX_CODES = "vtman_textbox_codes"
        const val EXTRA_ORDER_CODES = "vtman_order_codes"
        const val EXTRA_NEW_CODES = "vtman_new_codes"
        private const val IDLE_TIMEOUT_MS = 30_000L
        private const val SAME_FRAME_DEBOUNCE_MS = 1_500L
    }

    private lateinit var barcodeView: CompoundBarcodeView
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val textboxCodes = linkedSetOf<String>()
    private val existingOrderCodes = linkedSetOf<String>()
    private val newCodes = linkedSetOf<String>()
    private var lastDecodeAt = System.currentTimeMillis()
    private var lastRawCode = ""
    private var lastRawAt = 0L
    private var finished = false

    private val idleCheck = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - lastDecodeAt >= IDLE_TIMEOUT_MS) {
                Toast.makeText(this@ContinuousVtmanScanActivity, "Không quét được mã trong 30 giây · tự đóng camera", Toast.LENGTH_SHORT).show()
                finishWithCodes()
            } else {
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        intent.getStringArrayListExtra(EXTRA_TEXTBOX_CODES)
            ?.map(String::trim)?.filter(String::isNotBlank)
            ?.forEach { textboxCodes.add(it.uppercase()) }
        intent.getStringArrayListExtra(EXTRA_ORDER_CODES)
            ?.map(String::trim)?.filter(String::isNotBlank)
            ?.forEach { existingOrderCodes.add(it.uppercase()) }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        barcodeView = CompoundBarcodeView(this).apply {
            statusView.visibility = android.view.View.GONE
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    val code = result?.text?.trim().orEmpty()
                    if (code.isBlank()) return
                    val now = System.currentTimeMillis()
                    if (code.equals(lastRawCode, ignoreCase = true) && now - lastRawAt < SAME_FRAME_DEBOUNCE_MS) return
                    lastRawCode = code
                    lastRawAt = now
                    lastDecodeAt = now
                    handleCode(code)
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
            })
        }
        root.addView(barcodeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val close = Button(this).apply {
            text = "✕"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setOnClickListener { finishWithCodes() }
        }
        root.addView(close, FrameLayout.LayoutParams(dp(58), dp(52), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(18); marginEnd = dp(14)
        })

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xAA000000.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            text = "Đưa QR / mã vạch vào khung · 0 mã mới"
        }
        root.addView(statusText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(24)
        })

        setContentView(root)
        lastDecodeAt = System.currentTimeMillis()
        handler.postDelayed(idleCheck, 1_000L)
    }

    private fun handleCode(code: String) {
        val key = code.uppercase()
        when {
            existingOrderCodes.contains(key) -> Toast.makeText(this, "Mã $code đã có trong Chi tiết đơn", Toast.LENGTH_SHORT).show()
            textboxCodes.contains(key) || newCodes.any { it.equals(code, ignoreCase = true) } -> Toast.makeText(this, "Mã $code đã được quét", Toast.LENGTH_SHORT).show()
            else -> {
                newCodes.add(code)
                textboxCodes.add(key)
                statusText.text = "Đã thêm $code · ${newCodes.size} mã mới"
                Toast.makeText(this, "Đã thêm $code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishWithCodes() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        barcodeView.pause()
        setResult(RESULT_OK, Intent().putStringArrayListExtra(EXTRA_NEW_CODES, ArrayList(newCodes)))
        finish()
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        barcodeView.pause()
        super.onPause()
    }

    override fun onBackPressed() {
        finishWithCodes()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
