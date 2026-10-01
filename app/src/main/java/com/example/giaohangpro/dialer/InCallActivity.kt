package com.example.giaohangpro.dialer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class InCallActivity : Activity() {
    private val call get() = GhpInCallService.currentCall

    companion object {
        private val DARK = Color.rgb(28, 31, 36)
        private val PANEL = Color.rgb(48, 52, 59)
        private val GREEN = Color.rgb(28, 190, 92)
        private val RED = Color.rgb(237, 62, 55)
        private val MUTED = Color.rgb(196, 196, 196)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = DARK
        window.navigationBarColor = DARK
        setContentView(buildCallView())
    }

    override fun onResume() {
        super.onResume()
        if (call == null) finish()
    }

    private fun buildCallView(): LinearLayout {
        val phone = call?.details?.handle?.schemeSpecificPart.orEmpty()
        val customer = DialerCustomerLookup.find(this, phone)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(34), dp(24), dp(28))
            setBackgroundColor(DARK)
        }

        root.addView(TextView(this).apply {
            text = if (call?.state == Call.STATE_RINGING) "Cuộc gọi đến" else "Đang gọi..."
            textSize = 15f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = phone.ifBlank { "Cuộc gọi" }
            textSize = 31f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })

        if (customer != null) {
            root.addView(TextView(this).apply {
                text = customer.name.ifBlank { "Khách hàng GiaoHangPro" }
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
            })
            if (customer.address.isNotBlank()) {
                root.addView(TextView(this).apply {
                    text = customer.address
                    textSize = 14f
                    setTextColor(MUTED)
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(6), dp(12), dp(12))
                    maxLines = 2
                })
            }
        }

        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circle(Color.rgb(69, 73, 80))
            if (customer?.photoUri?.isNotBlank() == true) {
                runCatching { setImageURI(Uri.parse(customer.photoUri)) }
            } else {
                setImageDrawable(null)
            }
        }
        root.addView(photo, LinearLayout.LayoutParams(dp(150), dp(150)).apply {
            topMargin = dp(20)
            bottomMargin = dp(26)
        })

        if (customer?.photoUri.isNullOrBlank()) {
            root.addView(TextView(this).apply {
                text = customer?.name?.trim()?.take(1)?.uppercase().orEmpty().ifBlank { "☎" }
                textSize = 48f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                translationY = -dp(176).toFloat()
            }, LinearLayout.LayoutParams(dp(150), dp(0)))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row1.addView(control("⌁", "Tắt tiếng") { }, weighted())
        row1.addView(control("▦", "Bàn phím") { }, weighted())
        row1.addView(control("◖", "Loa") { }, weighted())
        controls.addView(row1, LinearLayout.LayoutParams(-1, dp(98)))

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row2.addView(control("+", "Thêm cuộc gọi") { }, weighted())
        row2.addView(control("Ⅱ", "Giữ cuộc gọi") {
            if (call?.state == Call.STATE_HOLDING) call?.unhold() else call?.hold()
        }, weighted())
        row2.addView(control("▤", "Ghi chú") { }, weighted())
        controls.addView(row2, LinearLayout.LayoutParams(-1, dp(98)))

        root.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f))

        if (call?.state == Call.STATE_RINGING) {
            val incoming = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            incoming.addView(bigCallButton("☎", GREEN, "Trả lời") {
                call?.answer(VideoProfile.STATE_AUDIO_ONLY)
                recreate()
            }, LinearLayout.LayoutParams(0, dp(104), 1f))
            incoming.addView(bigCallButton("☎", RED, "Từ chối") {
                call?.reject(false, null)
                finish()
            }, LinearLayout.LayoutParams(0, dp(104), 1f).apply {
                marginStart = dp(38)
            })
            root.addView(incoming, LinearLayout.LayoutParams(-1, -2))
        } else {
            root.addView(bigCallButton("☎", RED, "Kết thúc") {
                call?.disconnect()
                finish()
            }, LinearLayout.LayoutParams(dp(116), dp(116)))
        }

        return root
    }

    private fun control(icon: String, label: String, action: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { action() }
            addView(TextView(this@InCallActivity).apply {
                text = icon
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = circle(PANEL)
            }, LinearLayout.LayoutParams(dp(58), dp(58)))
            addView(TextView(this@InCallActivity).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, 0)
            })
        }

    private fun bigCallButton(icon: String, color: Int, label: String, action: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { action() }
            addView(TextView(this@InCallActivity).apply {
                text = icon
                textSize = 31f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                rotation = if (label == "Kết thúc" || label == "Từ chối") 135f else 0f
                background = circle(color)
            }, LinearLayout.LayoutParams(dp(74), dp(74)))
            addView(TextView(this@InCallActivity).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, dp(5), 0, 0)
            })
        }

    private fun weighted() = LinearLayout.LayoutParams(0, -1, 1f)

    private fun circle(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
