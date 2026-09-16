package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Test

class SearchMatchingTest {
    @Test fun findsVietnameseNamesWithoutAccentsAndByPartialName() {
        val name = normalizeCustomerSearch("Phan Phước")
        for (query in listOf("phan phuoc", "phuoc", "phan", " PHAN   PHƯỚC ")) {
            assertTrue(query, name.contains(normalizeCustomerSearch(query)))
        }
        assertTrue(normalizeCustomerSearch("Đặng ĐỨC").contains(normalizeCustomerSearch("dang duc")))
        assertEquals(normalizeCustomerSearch("Phước"), normalizeCustomerSearch("Phu\u031bo\u031b\u0301c"))
        assertFalse(name.contains(normalizeCustomerSearch("Nguyễn")))
    }

    @Test fun matchesCodWithCommonCurrencyFormats() {
        for (amount in listOf("234000", "234.000", "234,000 đ", "234 000 VND")) {
            for (query in listOf("234000", "234.000", "234,000", "234 000", "234000đ", "234")) {
                assertTrue("$amount / $query", matchesCodSearch(amount, query))
            }
        }
        assertTrue(matchesCodSearch("0 đ", "0"))
        assertFalse(matchesCodSearch("234000", "235000"))
    }

    @Test fun textCodesAndEmptyQueriesDoNotMatchCodAccidentally() {
        for (query in listOf("", " ", ".,", "đ", "TT500", "TP234000", "Phan 234")) {
            assertFalse(query, matchesCodSearch("234000", query))
        }
        assertFalse(matchesCodSearch("", "0"))
    }
}
