package com.example.giaohangpro.dialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class GhpInCallService : InCallService() {
    companion object { @Volatile var currentCall: Call? = null }
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call); currentCall=call
        startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    override fun onCallRemoved(call: Call) { if(currentCall===call) currentCall=null; super.onCallRemoved(call) }
}
