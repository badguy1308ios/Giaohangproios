package com.example.giaohangpro.dialer

import android.content.Context
import org.json.JSONArray

data class DialerCustomer(val name:String,val phone:String,val address:String,val photoUri:String)

object DialerCustomerLookup {
    private const val PREFS="giaohangpro_persistent_data_v1"
    fun normalize(v:String):String {
        var n=v.filter(Char::isDigit)
        if(n.startsWith("84") && n.length>=11) n="0"+n.drop(2)
        return n
    }
    fun find(context:Context, phone:String):DialerCustomer? {
        val wanted=normalize(phone); if(wanted.isBlank()) return null
        val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("customers","[]") ?: "[]"
        val arr=runCatching { JSONArray(raw) }.getOrNull() ?: return null
        for(i in 0 until arr.length()) {
            val c=arr.optJSONObject(i) ?: continue
            val phones=mutableListOf(c.optString("phone"))
            val extras=c.optJSONArray("extraPhones")
            if(extras!=null) for(j in 0 until extras.length()) phones += extras.optJSONObject(j)?.optString("number").orEmpty()
            if(phones.any { normalize(it)==wanted }) return DialerCustomer(c.optString("name"),phone,c.optString("address"),c.optString("photoUri"))
        }
        return null
    }
}
