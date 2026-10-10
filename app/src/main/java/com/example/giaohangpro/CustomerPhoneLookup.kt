package com.example.giaohangpro

/** Use the same phone identity for order groups and customer details. Never match by name/address. */
internal class CustomerPhoneLookup(customers: List<Customer>) {
    private val byPhone = buildMap<String, Customer> {
        customers.forEach { customer ->
            (listOf(customer.phone) + customer.extraPhones.map { it.number }).forEach { raw ->
                val key = normalize(raw)
                if (key.isNotBlank() && key !in this) put(key, customer)
            }
        }
    }
    fun find(phone: String): Customer? = byPhone[normalize(phone)]

    companion object {
        fun normalize(raw: String): String {
            val digits = raw.filter(Char::isDigit)
            return when {
                digits.startsWith("0084") && digits.length > 4 -> "0" + digits.drop(4)
                digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
                else -> digits
            }
        }
    }
}
