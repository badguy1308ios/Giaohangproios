package com.example.giaohangpro.vtman

import org.junit.Assert.*
import org.junit.Test

class VtmanCodTest {
    private val code = "SHOPEEVTPVN262735342833D"

    @Test fun mergedHeaderSkipsWaybillDigits() {
        assertEquals("79,900đ", VtmanFixedBlockParser.findCod(listOf("$code TT506 79,900 đ"), code))
    }
    @Test fun missingAmountNeverBecomesWaybillDigits() {
        assertEquals("", VtmanFixedBlockParser.findCod(listOf(code, "TT506"), code))
        assertEquals("", VtmanFixedBlockParser.findCod(listOf("262735342833D"), "262735342833D"))
    }
    @Test fun groupedAndZeroAmountsRemainSupported() {
        for (amount in listOf("79.900đ", "79,900d", "79900 đ", "0đ")) {
            assertEquals(amount.replace(" ", "").replace("d", "đ"),
                VtmanFixedBlockParser.findCod(listOf(code, "TT506", amount), code))
        }
    }
    @Test fun labelsWorkWithoutAcceptingEmbeddedAlphanumericNumbers() {
        assertEquals("79.900đ", VtmanFixedBlockParser.findCod(listOf(code, "COD: 79.900đ"), code))
        assertEquals("", VtmanFixedBlockParser.findCod(listOf("$code TT506 SKU123DXYZ"), code))
        assertEquals("", VtmanFixedBlockParser.findCod(listOf("$code TT506 ABC-123D"), code))
    }
}
