package com.example.giaohangpro.dialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.app.ActivityCompat

class DialerActivity : Activity() {
    companion object {
        private const val REQUEST_ROLE_DIALER = 4001
        private const val REQUEST_CALL_PHONE = 4002
        private const val REQUEST_CONTACTS = 4003
        private const val REQUEST_PICK_CONTACT = 4004

        private val BRAND = Color.rgb(201, 82, 25)
        private val BRAND_DARK = Color.rgb(167, 61, 14)
        private val GREEN = Color.rgb(25, 180, 83)
        private val TEXT = Color.rgb(48, 48, 48)
        private val MUTED = Color.rgb(120, 120, 120)
        private val SOFT = Color.rgb(247, 247, 247)
        private val BORDER = Color.rgb(228, 228, 228)
    }

    private lateinit var number: EditText
    private lateinit var customerCard: LinearLayout
    private lateinit var customerName: TextView
    private lateinit var customerAddress: TextView
    private lateinit var defaultDialerButton: TextView
    private var pendingCallNumber: String? = null
    private var openContactsAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BRAND_DARK
        window.navigationBarColor = Color.WHITE

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
            setBackgroundColor(Color.WHITE)
        }

        root.addView(buildHeader())

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        customerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.WHITE, dp(18).toFloat(), BORDER, dp(1))
            visibility = View.GONE
        }

        val avatar = TextView(this).apply {
            text = "●"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(103, 162, 232))
            background = rounded(Color.rgb(236, 244, 255), dp(28).toFloat())
        }
        customerCard.addView(avatar, LinearLayout.LayoutParams(dp(54), dp(54)))

        val customerTextWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        customerName = TextView(this).apply {
            textSize = 17f
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }
        customerAddress = TextView(this).apply {
            textSize = 13f
            setTextColor(MUTED)
            maxLines = 2
        }
        customerTextWrap.addView(customerName)
        customerTextWrap.addView(customerAddress)
        customerCard.addView(customerTextWrap, LinearLayout.LayoutParams(0, -2, 1f))

        customerCard.addView(TextView(this).apply {
            text = "⌖"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(30, 139, 232))
        }, LinearLayout.LayoutParams(dp(42), dp(42)))

        content.addView(customerCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        number = EditText(this).apply {
            textSize = 28f
            gravity = Gravity.CENTER
            hint = "Nhập số điện thoại"
            setHintTextColor(Color.rgb(170, 170, 170))
            setTextColor(TEXT)
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText(incomingPhone)
            setSelection(text.length)
            showSoftInputOnFocus = false
            background = rounded(SOFT, dp(18).toFloat(), BORDER, dp(1))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        content.addView(number, LinearLayout.LayoutParams(-1, dp(64)))

        customerAddress.addTextChangedListener(SimpleTextWatcher())
        number.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshCustomer()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        val keypadWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(4))
        }
        val keys = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )
        keys.forEach { row ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEachIndexed { index, (digit, letters) ->
                val key = TextView(this).apply {
                    gravity = Gravity.CENTER
                    text = if (letters.isBlank()) digit else "$digit\n$letters"
                    textSize = if (letters.isBlank()) 27f else 24f
                    setTextColor(TEXT)
                    setLineSpacing(-dp(2).toFloat(), 0.94f)
                    background = rounded(Color.rgb(246, 246, 246), dp(24).toFloat())
                    setOnClickListener { appendDigit(digit) }
                    setOnLongClickListener {
                        if (digit == "0") {
                            number.append("+")
                            true
                        } else false
                    }
                }
                rowView.addView(key, LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                    if (index > 0) marginStart = dp(12)
                })
            }
            keypadWrap.addView(rowView, LinearLayout.LayoutParams(-1, dp(80)))
        }
        content.addView(keypadWrap, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }

        val addContact = actionTile("♙", "Thêm liên hệ") { openContacts() }
        actions.addView(addContact, LinearLayout.LayoutParams(0, dp(78), 1f))

        val callButton = TextView(this).apply {
            text = "☎"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(GREEN, dp(38).toFloat())
            setOnClickListener { placeCall() }
        }
        actions.addView(callButton, LinearLayout.LayoutParams(dp(78), dp(78)).apply {
            marginStart = dp(18)
            marginEnd = dp(18)
        })

        val delete = actionTile("⌫", "Xóa số") {
            val editable = number.text
            if (editable.isNotEmpty()) editable.delete(editable.length - 1, editable.length)
        }.apply {
            setOnLongClickListener {
                number.setText("")
                true
            }
        }
        actions.addView(delete, LinearLayout.LayoutParams(0, dp(78), 1f))
        content.addView(actions, LinearLayout.LayoutParams(-1, -2))

        defaultDialerButton = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(MUTED)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.rgb(250, 250, 250), dp(12).toFloat())
            setOnClickListener { requestDialerRole() }
        }
        content.addView(defaultDialerButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        root.addView(buildBottomNav())
        return root
    }

    private fun buildHeader(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(14), dp(10))
            background = rounded(BRAND, 0f)
        }

        val title = TextView(this).apply {
            text = "◈  GiaoHangPro"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })

        bar.addView(TextView(this).apply {
            text = "⚙"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(46), dp(46)))

        return bar
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(6))
            background = rounded(Color.WHITE, 0f, BORDER, dp(1))
        }
        nav.addView(navItem("▦", "Bàn phím", true), LinearLayout.LayoutParams(0, dp(62), 1f))
        nav.addView(navItem("◷", "Gần đây", false), LinearLayout.LayoutParams(0, dp(62), 1f))
        nav.addView(navItem("♟", "Khách hàng", false), LinearLayout.LayoutParams(0, dp(62), 1f))
        nav.addView(navItem("⚙", "Cài đặt", false), LinearLayout.LayoutParams(0, dp(62), 1f))
        return nav
    }

    private fun navItem(icon: String, label: String, active: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(this@DialerActivity).apply {
                text = icon
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(if (active) BRAND else MUTED)
            })
            addView(TextView(this@DialerActivity).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (active) BRAND else MUTED)
                if (active) setTypeface(typeface, Typeface.BOLD)
            })
        }

    private fun actionTile(icon: String, label: String, action: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { action() }
            addView(TextView(this@DialerActivity).apply {
                text = icon
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(TEXT)
            })
            addView(TextView(this@DialerActivity).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(TEXT)
            })
        }

    private fun appendDigit(digit: String) {
        val start = number.selectionStart.coerceAtLeast(0)
        number.text.insert(start, digit)
    }

    private fun refreshCustomer() {
        if (!::customerCard.isInitialized || !::number.isInitialized) return
        val phone = number.text?.toString().orEmpty()

        val customer = DialerCustomerLookup.find(this, phone)
        if (customer != null) {
            customerName.text = customer.name.ifBlank { "Khách hàng GiaoHangPro" }
            customerAddress.text = customer.address
            customerCard.visibility = View.VISIBLE
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val contact = SystemContactLookup.findByPhone(this, phone)
            if (contact != null) {
                customerName.text = contact.name.ifBlank { "Liên hệ" }
                customerAddress.text = contact.phone
                customerCard.visibility = View.VISIBLE
            } else {
                customerCard.visibility = View.GONE
            }
        } else {
            customerCard.visibility = View.GONE
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
            "Đặt GiaoHangPro làm ứng dụng gọi điện mặc định"
        }
        defaultDialerButton.setTextColor(if (isDefault) Color.rgb(130, 130, 130) else BRAND)
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

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius
            if (stroke != null && strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private class SimpleTextWatcher : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}
