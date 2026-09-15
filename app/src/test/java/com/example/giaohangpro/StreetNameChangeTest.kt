package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Test

class StreetNameChangeTest {
    private val customer = Customer(
        id = 7, name = "Khách A", phone = "0901234567", address = "Địa chỉ nguyên gốc",
        streetName = " N25 ", latitude = "10.8", longitude = "107.0",
        initials = "KA", aliases = listOf("Shop A"), note = "Ghi chú",
        photoUri = "content://photo/7", primaryCanCall = false,
        extraPhones = listOf(CustomerPhone("0912345678")),
        extraAddresses = listOf(CustomerAddress("Địa chỉ phụ", "10.9", "107.1"))
    )

    @Test fun renameUpdatesEveryAssignedCustomerAndPreservesOtherFields() {
        val sameStreet = customer.copy(id = 8, streetName = "n25")
        val other = customer.copy(id = 9, streetName = "D39")
        val result = StreetNameChange.prepare("N25", " N26 ", listOf("N25", "D39"),
            listOf(customer, sameStreet, other), emptyList())
        assertEquals(listOf("N26", "D39"), result.names)
        assertEquals(listOf(customer.copy(streetName = "N26"), sameStreet.copy(streetName = "N26"), other), result.customers)
        assertEquals(" N25 ", customer.streetName)
    }

    @Test fun supportsCaseOnlyRename() {
        val result = StreetNameChange.prepare("N25", "n25", listOf("N25"), listOf(customer), emptyList())
        assertEquals(listOf("n25"), result.names)
        assertEquals("n25", result.customers.single().streetName)
    }

    @Test fun unusedStreetCanBeRenamed() {
        val result = StreetNameChange.prepare("N25", "N26", listOf("N25"), emptyList(), emptyList())
        assertEquals(listOf("N26"), result.names)
        assertTrue(result.customers.isEmpty())
    }

    @Test fun blankOrDuplicateOrMissingSourceIsRejected() {
        for ((old, new) in listOf("N25" to " ", "N25" to " d39 ", "missing" to "N26")) {
            try {
                StreetNameChange.prepare(old, new, listOf("N25", "D39"), listOf(customer), emptyList())
                fail("Invalid rename accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun learningLabelsFollowRenameWithoutChangingVisitOrderOrCoordinates() {
        val point = StreetRouteLearning.Point(10.8, 107.0)
        val valid = StreetRouteLearning.Visit(7, "n25", point)
        val differentCustomer = StreetRouteLearning.Visit(9, "N25", point)
        val differentHistoricalStreet = StreetRouteLearning.Visit(7, "D39", point)
        val history = listOf(listOf(valid, differentCustomer, differentHistoricalStreet))
        val result = StreetNameChange.prepare("N25", "N26", listOf("N25"), listOf(customer), history)
        assertEquals(listOf(listOf(valid.copy(street = "N26"), differentCustomer, differentHistoricalStreet)), result.lessons)
        assertEquals("n25", history.first().first().street)
    }
}
