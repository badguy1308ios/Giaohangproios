package com.example.giaohangpro.dialer

import android.content.Context
import com.example.giaohangpro.vtman.VtmanQueueController

object AutoExportDialerBridge {
    fun consumeIfNeeded(context:Context, phone:String):Boolean {
        VtmanQueueController.attachContext(context)
        if(!VtmanQueueController.hasPendingDataExport() && !VtmanQueueController.isAutoSession()) return false
        if(!VtmanQueueController.activeMatchesCurrent()) return false
        val normalized=DialerCustomerLookup.normalize(phone)
        if(normalized.length < 9) return false
        VtmanQueueController.updatePhone(normalized)
        VtmanQueueController.addAutoLog("✓ SĐT nhận trực tiếp qua GiaoHangPro Dialer")
        VtmanQueueController.report("Đã nhận SĐT cho " + VtmanQueueController.nextWaybill().orEmpty())
        return true
    }
}
