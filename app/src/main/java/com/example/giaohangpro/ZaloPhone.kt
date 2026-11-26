package com.example.giaohangpro

// Orders without a linked customer can still open their delivery phone in Zalo.
// Linked customers must respect the Zalo choices saved in their profile.
internal fun zaloPhone(customer: Customer?, orderPhone: String = ""): String? {
    val raw = if (customer == null) orderPhone else {
        if (customer.primaryCanZalo && customer.phone.isNotBlank()) customer.phone
        else customer.extraPhones.firstOrNull { it.canZalo && it.number.isNotBlank() }?.number
    }
    return raw?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }
}
