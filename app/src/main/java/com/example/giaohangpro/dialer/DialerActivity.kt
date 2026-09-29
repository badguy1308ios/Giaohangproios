package com.example.giaohangpro.dialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.*
import androidx.core.app.ActivityCompat

class DialerActivity : Activity() {
    private lateinit var number: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingPhone = intent?.data?.schemeSpecificPart.orEmpty()
        if (incomingPhone.isNotBlank() && AutoExportDialerBridge.consumeIfNeeded(this, incomingPhone)) { finish(); return }
        number = EditText(this).apply {
            textSize = 28f
            hint = "Nhập số điện thoại"
            setText(intent?.data?.schemeSpecificPart.orEmpty())
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 28) }
        root.addView(TextView(this).apply { text = "GiaoHangPro • Điện thoại"; textSize = 22f })
        root.addView(number, LinearLayout.LayoutParams(-1, -2))
        val customerInfo = TextView(this).apply { textSize=16f }
        root.addView(customerInfo)
        fun refreshCustomer() { val x=DialerCustomerLookup.find(this, number.text.toString()); customerInfo.text = if(x==null) "" else x.name+"\n"+x.address }
        refreshCustomer()
        number.addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}; override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){refreshCustomer()}; override fun afterTextChanged(e:android.text.Editable?){} })
        val grid = GridLayout(this).apply { columnCount = 3 }
        listOf("1","2","3","4","5","6","7","8","9","*","0","#").forEach { d ->
            grid.addView(Button(this).apply { text=d; textSize=24f; setOnClickListener { number.append(d) } },
                GridLayout.LayoutParams().apply { width=0; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) })
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text="GỌI"; textSize=20f; setOnClickListener { placeCall() } })
        root.addView(Button(this).apply { text="ĐẶT GIAOHANGPRO LÀM ỨNG DỤNG GỌI MẶC ĐỊNH"; setOnClickListener { requestDialerRole() } })
        setContentView(root)
    }

    private fun requestDialerRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val rm=getSystemService(RoleManager::class.java)
            if (!rm.isRoleHeld(RoleManager.ROLE_DIALER)) startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 4001)
        } else {
            startActivity(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName))
        }
    }

    private fun placeCall() {
        val n=number.text.toString().trim()
        if(n.isBlank()) return
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CALL_PHONE),4002); return
        }
        getSystemService(TelecomManager::class.java).placeCall(Uri.parse("tel:"+Uri.encode(n)), Bundle())
    }
}
