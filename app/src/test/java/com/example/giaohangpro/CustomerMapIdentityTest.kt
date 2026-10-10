package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Test

class CustomerMapIdentityTest {
    private val right = Customer(2, "Thanh Nguyen", "0901234567", "D18",
        latitude = "10.808106", longitude = "106.999992")
    private val wrong = right.copy(id = 1, phone = "0912345678", latitude = "10.800000", longitude = "106.990000")
    private val order = Order("PKE1540020182", "Thanh Nguyen", right.phone, "D18", "Hàng", "117,000đ", emptyList())
    private fun marker(customers: List<Customer>, source: Order = order): MapOrderMarker {
        val group = buildDeliveryGroups(listOf(source), customers).single()
        return buildMapOrderMarkers(listOf(groupRepresentative(group)), MapPoint(0.0, 0.0), true, mapOf(source.code to 41)).single()
    }
    @Test fun earlierCustomerWithSameNameAndAddressCannotStealPin() {
        val customers = listOf(wrong, right)
        val point = marker(customers)
        assertEquals(CustomerPhoneLookup(customers).find(order.phone)!!.id,
            buildDeliveryGroups(listOf(order), customers).single().customer!!.id)
        assertEquals(MapPoint(10.808106, 106.999992), point.point)
        assertEquals(41, point.number)
        assertEquals(order.code, point.order.code)
    }
    @Test fun phoneFormatsAndSecondaryNumbersResolveSameSavedCustomer() {
        val customers = listOf(wrong, right.copy(extraPhones = listOf(CustomerPhone("0987654321"))))
        for (phone in listOf("+84 901 234 567", "0084901234567", "0901.234.567", "+84987654321")) {
            assertEquals(right.id, CustomerPhoneLookup(customers).find(phone)!!.id)
            assertEquals(MapPoint(10.808106, 106.999992), marker(customers, order.copy(phone = phone)).point)
        }
    }
    @Test fun duplicatePhoneUsesSameFirstRecordAsCustomerDetails() {
        val customers = listOf(right, wrong.copy(phone = right.phone))
        assertEquals(right.id, CustomerPhoneLookup(customers).find(order.phone)!!.id)
        assertEquals(right.id, buildDeliveryGroups(listOf(order), customers).single().customer!!.id)
        assertEquals(MapPoint(10.808106, 106.999992), marker(customers).point)
    }
    @Test fun unknownOrBlankPhoneCannotBorrowCoordinatesFromSameName() {
        for (phone in listOf("", "0999999999")) {
            val result = marker(listOf(wrong, right), order.copy(phone = phone))
            assertFalse(result.hasRealCoordinate)
            assertEquals(MapPoint(0.0, 0.0), result.point)
        }
    }
    @Test fun savedCoordinateEditUpdatesPinWhileGpsDoesNotReplaceIt() {
        val edited = right.copy(latitude = "10.810001", longitude = "107.000002")
        assertEquals(MapPoint(10.810001, 107.000002), marker(listOf(wrong, edited)).point)
        val display = groupRepresentative(buildDeliveryGroups(listOf(order), listOf(wrong, edited)).single())
        assertEquals(MapPoint(10.810001, 107.000002),
            buildMapOrderMarkers(listOf(display), MapPoint(21.0, 105.0), true, emptyMap()).single().point)
    }
    @Test fun unmatchedOrderCanKeepItsOwnCoordinates() {
        assertEquals(MapPoint(11.0, 107.0), marker(listOf(wrong), order.copy(latitude = "11", longitude = "107")).point)
    }
}
