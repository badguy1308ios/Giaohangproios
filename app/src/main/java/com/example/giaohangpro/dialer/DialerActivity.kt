package com.example.giaohangpro.dialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat

class DialerActivity : Activity() {
    companion object {
        private const val REQUEST_ROLE_DIALER = 4001
        private const val REQUEST_CALL_PHONE = 4002
        private const val REQUEST_CONTACTS = 4003
        private const val REQUEST_PICK_CONTACT = 4004
    }

    private lateinit var number: EditText
    private lateinit var customerInfo: TextView
    private lateinit var defaultDialerButton: Button
    private var pendingCallNumber: String? = null
    private var openContactsAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingPhone = intent?.data?.schemeSpecificPart.orEmpty()
        if (incomingPhone.isNotBlank() && AutoExportDialerBridge.consumeIfNeeded(this, incomingPhone)) {
            finish()
            return
        }

        setContentView(buildDialerView(incomingPhone))
        maybeRequestDefaultDialerOnFirstOpen()
        refreshDefaultDialerState()
        refreshCustomer()
    }

    override fun onResume() {
        super.onResume()
        if (::defaultDialerButton.isInitialized) refreshDefaultDialerState()
    }

    private fun buildDialerView(incomingPhone: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(18))
        }

        root.addView(TextView(this).apply {
            text = "Điện thoại"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "GiaoHangPro"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(18))
        })

        number = EditText(this).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            hint = "Nhập số điện thoại"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText(incomingPhone)
            setSelection(text.length)
            showSoftInputOnFocus = false
        }
        root.addView(number, LinearLayout.LayoutParams(-1, -2))

        customerInfo = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(14))
            minHeight = dp(58)
        }
        root.addView(customerInfo, LinearLayout.LayoutParams(-1, -2))

        number.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshCustomer()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        val keypad = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            useDefaultMargins = true
        }
        listOf(
            "1" to "",
            "2" to "ABC",
            "3" to "DEF",
            "4" to "GHI",
            "5" to "JKL",
            "6" to "MNO",
            "7" to "PQRS",
            "8" to "TUV",
            "9" to "WXYZ",
            "*" to "",
            "0" to "+",
            "#" to ""
        ).forEach { (digit, letters) ->
            val label = if (letters.isBlank()) digit else "$digit\n$letters"
            keypad.addView(Button(this).apply {
                text = label
                textSize = 22f
                minHeight = dp(64)
                isAllCaps = false
                setOnClickListener { appendDigit(digit) }
                setOnLongClickListener {
                    if (digit == "0") {
                        number.append("+")
                        true
                    } else false
                }
            }, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(72)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        root.addView(keypad, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }

        val contactsButton = Button(this).apply {
            text = "Danh bạ"
            isAllCaps = false
            setOnClickListener { openContacts() }
        }
        actions.addView(contactsButton, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            marginEnd = dp(10)
        })

        val callButton = Button(this).apply {
            text = "☎  GỌI"
            textSize = 20f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(25, 135, 84))
            setOnClickListener { placeCall() }
        }
        actions.addView(callButton, LinearLayout.LayoutParams(0, dp(58), 1.4f).apply {
            marginEnd = dp(10)
        })

        val deleteButton = Button(this).apply {
            text = "⌫"
            textSize = 22f
            contentDescription = "Xóa số"
            setOnClickListener {
                val editable = number.text
                if (editable.isNotEmpty()) editable.delete(editable.length - 1, editable.length)
            }
            setOnLongClickListener {
                number.setText("")
                true
            }
        }
        actions.addView(deleteButton, LinearLayout.LayoutParams(0, dp(58), 0.7f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))

        defaultDialerButton = Button(this).apply {
            text = "Đặt GiaoHangPro làm ứng dụng gọi mặc định"
            isAllCaps = false
            setOnClickListener { requestDialerRole() }
        }
        root.addView(defaultDialerButton, LinearLayout.LayoutParams(-1, -2))

        return root
    }

    private fun appendDigit(digit: String) {
        val start = number.selectionStart.coerceAtLeast(0)
        number.text.insert(start, digit)
    }

    private fun refreshCustomer() {
        if (!::customerInfo.isInitialized) return
        val phone = number.text?.toString().orEmpty()

        val customer = DialerCustomerLookup.find(this, phone)
        if (customer != null) {
            customerInfo.text = buildString {
                append(customer.name.ifBlank { "Khách hàng GiaoHangPro" })
                if (customer.address.isNotBlank()) append("\n").append(customer.address)
            }
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val contact = SystemContactLookup.findByPhone(this, phone)
            customerInfo.text = if (contact != null) {
                buildString {
                    append(contact.name.ifBlank { "Liên hệ" })
                    if (contact.phone.isNotBlank()) append("\n").append(contact.phone)
                }
            } else ""
        } else {
            customerInfo.text = ""
        }
    }

    private fun maybeRequestDefaultDialerOnFirstOpen() {
        val prefs = getSharedPreferences("giaohangpro_dialer", MODE_PRIVATE)
        if (prefs.getBoolean("asked_default_dialer", false)) return

        val telecom = getSystemService(TelecomManager::class.java)
        if (telecom.defaultDialerPackage == packageName) {
            prefs.edit().putBoolean("asked_default_dialer", true).apply()
            return
        }

        prefs.edit().putBoolean("asked_default_dialer", true).apply()
        requestDialerRole()
    }

    private fun refreshDefaultDialerState() {
        if (!::defaultDialerButton.isInitialized) return
        val telecom = getSystemService(TelecomManager::class.java)
        val isDefault = telecom.defaultDialerPackage == packageName
        defaultDialerButton.text = if (isDefault) {
            "✓ GiaoHangPro đang là ứng dụng gọi mặc định"
        } else {
            "Đặt GiaoHangPro làm ứng dụng gọi mặc định"
        }
        defaultDialerButton.isEnabled = !isDefault
    }

    private fun requestDialerRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    REQUEST_ROLE_DIALER
                )
            }
        } else {
            startActivity(
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            )
        }
    }

    private fun placeCall() {
        val phone = number.text.toString().trim()
        if (phone.isBlank()) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCallNumber = phone
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CALL_PHONE
            )
            return
        }

        getSystemService(TelecomManager::class.java)
            .placeCall(Uri.parse("tel:" + Uri.encode(phone)), Bundle())
    }

    private fun openContacts() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            openContactsAfterPermission = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CONTACTS
            )
            return
        }
        launchContactPicker()
    }

    private fun launchContactPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
            REQUEST_PICK_CONTACT
        )
    }

    @Deprecated("Deprecated in Android API, kept for minSdk-compatible contact picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ROLE_DIALER) {
            refreshDefaultDialerState()
            return
        }

        if (requestCode != REQUEST_PICK_CONTACT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex < 0) return@use
            val selectedPhone = cursor.getString(numberIndex).orEmpty()
            number.setText(selectedPhone)
            number.setSelection(number.text.length)
            refreshCustomer()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            REQUEST_CALL_PHONE -> {
                if (granted) {
                    pendingCallNumber?.let {
                        number.setText(it)
                        number.setSelection(number.text.length)
                    }
                    pendingCallNumber = null
                    placeCall()
                } else {
                    pendingCallNumber = null
                }
            }

            REQUEST_CONTACTS -> {
                if (granted) {
                    refreshCustomer()
                    if (openContactsAfterPermission) launchContactPicker()
                }
                openContactsAfterPermission = false
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
