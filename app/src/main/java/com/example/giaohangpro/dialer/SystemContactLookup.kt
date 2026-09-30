package com.example.giaohangpro.dialer

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class SystemContact(
    val name: String,
    val phone: String,
    val photoUri: String?
)

object SystemContactLookup {
    fun findByPhone(context: Context, phone: String): SystemContact? {
        val normalized = DialerCustomerLookup.normalize(phone)
        if (normalized.isBlank()) return null

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NUMBER,
            ContactsContract.PhoneLookup.PHOTO_URI
        )

        return runCatching {
            context.contentResolver.query(
                lookupUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                while (cursor.moveToNext()) {
                    val candidate = cursor.getString(numberIndex).orEmpty()
                    if (DialerCustomerLookup.normalize(candidate) == normalized) {
                        return@use SystemContact(
                            name = cursor.getString(nameIndex).orEmpty(),
                            phone = candidate,
                            photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                        )
                    }
                }
                null
            }
        }.getOrNull()
    }
}
