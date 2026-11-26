package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Test

class ZaloPhoneTest {
    private val customer = Customer(
        id = 1L, name = "Customer", phone = "0901 234 567", address = "",
        primaryCanZalo = false,
        extraPhones = listOf(
            CustomerPhone("0902 234 567", canZalo = false),
            CustomerPhone("", canZalo = true),
            CustomerPhone("0903 234 567", canZalo = true)
        )
    )

    @Test fun orderAndCustomerUseTheSameEnabledExtraPhone() {
        assertEquals("0903234567", zaloPhone(customer))
        assertEquals(zaloPhone(customer), zaloPhone(customer, "0904 234 567"))
    }

    @Test fun enabledPrimaryPhoneKeepsPriority() {
        assertEquals("0901234567", zaloPhone(customer.copy(primaryCanZalo = true)))
    }

    @Test fun linkedCustomerWithZaloDisabledDoesNotFallBackToOrderPhone() {
        assertNull(zaloPhone(customer.copy(extraPhones = emptyList()), "0904 234 567"))
    }

    @Test fun unlinkedOrderUsesItsOwnPhone() {
        assertEquals("0904234567", zaloPhone(null, "0904 234 567"))
        assertNull(zaloPhone(null, ""))
    }
}
