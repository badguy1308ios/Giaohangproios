package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Test

class CustomerCoordinateSafetyTest {
    private val customer = Customer(7, "Khách", "0901234567", "Nhà",
        latitude = "10.797606666666665", longitude = "106.99663333333335",
        extraAddresses = listOf(CustomerAddress("Kho", "10.9", "107.1")))

    @Test fun changingPhotoPreservesEveryCoordinateAndOtherCurrentFields() {
        val current = customer.copy(latitude = "10.800001", longitude = "106.950002", note = "Mới")
        assertEquals(current.copy(photoUri = "file:///new.jpg"), CustomerCoordinateSafety.updatePhoto(current, "file:///new.jpg"))
    }

    @Test fun staleFormCannotOverwriteNewFixOrRestoreDeletedCustomer() {
        assertFalse(CustomerCoordinateSafety.canSave(customer.copy(latitude = "10.81"), customer))
        assertFalse(CustomerCoordinateSafety.canSave(null, customer))
        assertTrue(CustomerCoordinateSafety.canSave(customer.copy(), customer))
    }

    @Test fun importRejectsConcurrentEditsAdditionsAndDeletions() {
        val original = listOf(customer)
        assertFalse(CustomerCoordinateSafety.unchangedSinceImport(listOf(customer.copy(longitude = "107")), original))
        assertFalse(CustomerCoordinateSafety.unchangedSinceImport(original + customer.copy(id = 8), original))
        assertFalse(CustomerCoordinateSafety.unchangedSinceImport(emptyList(), original))
        assertTrue(CustomerCoordinateSafety.unchangedSinceImport(original.toList(), original))
    }

    @Test fun importNeverMixesAxesOrOverwritesExistingFix() {
        for ((lat, lng) in listOf("10.8" to "", "" to "107.1", "10.8" to "107.1")) {
            assertEquals(lat to lng, CustomerCoordinateSafety.fillEmptyPair(lat, lng, "20.1", "105.5"))
        }
        assertEquals("20.1" to "105.5", CustomerCoordinateSafety.fillEmptyPair("", "", "20.1", "105.5"))
        assertEquals("" to "", CustomerCoordinateSafety.fillEmptyPair("", "", "NaN", "105.5"))
    }

    @Test fun longSingleCoordinateRemainsExact() {
        assertEquals(customer.latitude to customer.longitude,
            CustomerCoordinateSafety.input(customer.latitude, true, "", customer.longitude))
        assertEquals(customer.latitude to customer.longitude,
            CustomerCoordinateSafety.input(customer.longitude, false, customer.latitude, ""))
    }

    @Test fun decimalCommaAndScientificNotationDoNotChangeOtherAxis() {
        assertEquals("10.8" to "107.1", CustomerCoordinateSafety.input("10,8", true, "", "107.1"))
        assertEquals("1.08e1" to "107.1", CustomerCoordinateSafety.input("1.08e1", true, "", "107.1"))
    }

    @Test fun pastePairSupportsBothFieldsReverseOrderAndLabels() {
        for (raw in listOf("10.796597, 106.950227", "106.950227 10.796597", "Vĩ độ: 10.796597 Kinh độ: 106.950227")) {
            for (latitudeField in listOf(true, false)) {
                assertEquals("10.796597" to "106.950227", CustomerCoordinateSafety.input(raw, latitudeField, "", ""))
            }
        }
    }

    @Test fun malformedTextCannotSilentlyExtractUnrelatedNumbers() {
        for (raw in listOf("10° 47' 51\" 106° 59'", "nhà 12 đường 34", "1000.5", "10.8 106.9 50", "10.8.5")) {
            val result = CustomerCoordinateSafety.input(raw, true, "10.1", "107.2")
            assertEquals("107.2", result.second)
            assertFalse(CustomerCoordinateSafety.valid(result.first, result.second))
        }
    }

    @Test fun rejectsNonFiniteIncompleteAndOutOfRangePairs() {
        for ((lat, lng) in listOf("NaN" to "106", "10" to "Infinity", "91" to "106", "10" to "181", "" to "106")) {
            assertFalse(CustomerCoordinateSafety.valid(lat, lng))
        }
        assertTrue(CustomerCoordinateSafety.valid(customer.latitude, customer.longitude))
    }

    @Test fun confirmationIncludesPrimarySwitchAndExtraAddressChanges() {
        val before = CustomerCoordinateSafety.positions(customer)
        assertEquals(before, CustomerCoordinateSafety.positions(customer.copy(name = "Tên mới", photoUri = "new")))
        assertNotEquals(before, CustomerCoordinateSafety.positions(customer.copy(latitude = "10.9", longitude = "107.1")))
        assertNotEquals(before, CustomerCoordinateSafety.positions(customer.copy(extraAddresses = emptyList())))
    }

    @Test fun savedCustomerFixWinsOverStaleOrderCoordinatesWithoutMixingAxes() {
        assertEquals(customer.latitude to customer.longitude,
            CustomerCoordinateSafety.preferredCoordinates(customer, "17.4689", "106.6220"))
        assertEquals("17.4689" to "106.6220",
            CustomerCoordinateSafety.preferredCoordinates(customer.copy(longitude = ""), "17.4689", "106.6220"))
        assertEquals("" to "",
            CustomerCoordinateSafety.preferredCoordinates(customer.copy(longitude = ""), "", "106.6220"))
        assertEquals("17.4689" to "106.6220",
            CustomerCoordinateSafety.preferredCoordinates(null, "17.4689", "106.6220"))
    }
}
