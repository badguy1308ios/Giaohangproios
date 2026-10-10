package com.example.giaohangpro.vtman

import org.junit.Assert.*
import org.junit.Test

class VtmanWaybillCodesTest {
    private val code = "SGSIND09102026-01"
    private fun node(text: String, left: Int, top: Int) = VtmanScreenText(text, left, top, left + 200, top + 40)

    @Test fun reportedCsvLoadsBothCompleteCodes() {
        val csv = "\uFEFFMA_VAN_DON\r\n\"$code\"\r\n\"154069642893\"\r\n"
        assertEquals(listOf(code, "154069642893"), VtmanWaybillCodes.fromCsv(csv))
    }
    @Test fun scanSupportsSeparateAndCombinedStatusNodes() {
        assertEquals(listOf(code, "154069642893"), VtmanWaybillCodes.visible(listOf(
            node(code, 20, 100), node("TT500", 250, 100),
            node("154069642893 TT500 395,000 đ", 20, 400)
        )))
        assertEquals(listOf(code), VtmanWaybillCodes.visible(listOf(node("$code TT500 0 đ", 20, 100))))
    }
    @Test fun suffixIsPartOfIdentity() {
        assertTrue(VtmanFixedBlockParser.containsExpectedWaybill("$code TT500", code))
        assertFalse(VtmanFixedBlockParser.containsExpectedWaybill(code, "SGSIND09102026"))
        assertFalse(VtmanFixedBlockParser.containsExpectedWaybill("$code-02", code))
        assertFalse(VtmanFixedBlockParser.containsExpectedWaybill("SGSIND09102026-02", code))
    }
    @Test fun invalidCodesAreNotPartiallyExtracted() {
        for (bad in listOf("-SGSIND09102026", "SGSIND09102026-", "SGSIND--09102026", "ABCDEFGHIJKLMNOPQRSTUVWXYZ123", "ABCDEFGH")) {
            assertFalse(VtmanWaybillCodes.isValid(bad))
            assertTrue(VtmanWaybillCodes.visible(listOf(node("$bad TT500", 20, 100))).isEmpty())
        }
    }
    @Test fun legacyCodesDuplicatesAndCsvDelimitersStillWork() {
        assertEquals(listOf(code, "TPO1518765587", "154069642893"), VtmanWaybillCodes.fromCsv(
            "MA_VAN_DON\n${code.lowercase()};other\n\"$code\",other\nTPO1518765587\n154069642893\n"))
    }
    @Test fun fullOrderParserKeepsHyphenatedIdentity() {
        val lines = listOf("$code TT500 0 đ", "Shop A", "Khách B", "Đường N39", "Tài liệu", "COD,PXD", "Thành công")
        assertEquals(code, VtmanFixedBlockParser.parse(lines, code)!!.waybill)
        val result = VtmanFixedBlockParser.parsePositioned(lines.mapIndexed { i, value -> node(value, 20, 100 + i * 65) }, code)!!
        assertEquals(code, result.waybill)
        assertEquals("Khách B", result.customer)
    }
}
