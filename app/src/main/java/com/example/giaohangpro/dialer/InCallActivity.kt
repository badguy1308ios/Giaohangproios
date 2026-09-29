package com.example.giaohangpro.dialer

import android.app.Activity
import android.os.Bundle
import android.telecom.Call
import android.view.Gravity
import android.widget.*

class InCallActivity : Activity() {
    private val call get() = GhpInCallService.currentCall
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(30,60,30,30) }
        root.addView(TextView(this).apply { text=call?.details?.handle?.schemeSpecificPart ?: "Cuộc gọi"; textSize=30f })
        root.addView(Button(this).apply { text="TRẢ LỜI"; setOnClickListener { call?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) } })
        root.addView(Button(this).apply { text="TỪ CHỐI"; setOnClickListener { call?.reject(false,null) } })
        root.addView(Button(this).apply { text="KẾT THÚC"; setOnClickListener { call?.disconnect(); finish() } })
        root.addView(Button(this).apply { text="GIỮ / TIẾP TỤC"; setOnClickListener { if(call?.state==Call.STATE_HOLDING) call?.unhold() else call?.hold() } })
        setContentView(root)
    }
}
