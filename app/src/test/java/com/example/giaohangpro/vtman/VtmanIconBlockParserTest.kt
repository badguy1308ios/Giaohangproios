package com.example.giaohangpro.vtman

import org.junit.Assert.*
import org.junit.Test

class VtmanIconBlockParserTest {
    private val code = "SGSIND09102026-01"
    private fun snapshot(service: Boolean = true, emptyService: Boolean = false): MutableList<VtmanElement> {
        val nodes = mutableListOf(
            VtmanElement(-1, 0, 0, 600, 1000),
            VtmanElement(0, 20, 100, 300, 140, code),
            VtmanElement(0, 310, 100, 380, 140, "TT505"),
            VtmanElement(0, 400, 100, 590, 140, "350,000 đ")
        )
        val fields = mutableListOf(listOf("Thành công"), listOf("Thành công"),
            listOf("đường d33, tại - Khu Dân Cư Lộc An", "X. Lộc An - H. Long Thành - T. Đồng Nai"),
            listOf("VIÊN Xương khớp", "hộp lớn"))
        if (service) fields += if (emptyService) emptyList<String>() else listOf("COD,PXD")
        var top = 160
        fields.forEachIndexed { field, lines ->
            val row = nodes.size
            val height = maxOf(60, lines.size * 40)
            nodes += VtmanElement(0, 20, top, 590, top + height)
            // Centered icon within a wrapped row, with an extra wrapper.
            val wrapper = nodes.size
            nodes += VtmanElement(row, 20, top, 55, top + height)
            nodes += VtmanElement(wrapper, 25, top + height / 2 - 12, 49, top + height / 2 + 12, image = true)
            val textParent = nodes.size
            nodes += VtmanElement(row, 80, top, 500, top + height, lines.joinToString(" "))
            lines.forEachIndexed { i, text ->
                nodes += VtmanElement(textParent, 80, top + i * 40, 500, top + i * 40 + 35, text)
            }
            if (field == 1) nodes += VtmanElement(row, 540, top, 580, top + 40, image = true)
            top += height + 15
        }
        nodes += VtmanElement(0, 200, top, 400, top + 45, "Thành công")
        return nodes
    }

    @Test fun usesIconsPreservesWrappedFieldsAndDuplicateNames() {
        val record = VtmanIconBlockParser.parse(snapshot(), code)!!
        assertEquals("Thành công", record.shop)
        assertEquals("Thành công", record.customer)
        assertEquals("đường d33, tại - Khu Dân Cư Lộc An X. Lộc An - H. Long Thành - T. Đồng Nai", record.address)
        assertEquals("VIÊN Xương khớp hộp lớn", record.goods)
        assertEquals("COD,PXD", record.service)
        assertEquals("TT505", record.status)
        assertEquals("350,000đ", record.cod)
    }
    @Test fun absentServiceIsAllowed() {
        val record = VtmanIconBlockParser.parse(snapshot(service = false), code)!!
        assertEquals("", record.service)
        assertEquals("VIÊN Xương khớp hộp lớn", record.goods)
    }
    @Test fun emptyServiceIsAllowed() {
        assertEquals("", VtmanIconBlockParser.parse(snapshot(emptyService = true), code)!!.service)
    }
    @Test fun missingIconsDoesNotFallBackToCountingText() {
        val nodes = snapshot().map { it.copy(image = false) }
        assertNull(VtmanIconBlockParser.parse(nodes, code))
    }
    @Test fun wrongWaybillCannotBeParsed() {
        assertNull(VtmanIconBlockParser.parse(snapshot(), "154069642893"))
    }
    @Test fun missingRequiredTextIsRejected() {
        val nodes = snapshot().map { if (it.text == "Thành công") it.copy(text = "") else it }
        assertNull(VtmanIconBlockParser.parse(nodes, code))
    }
    @Test fun unstructuredWholeCardDoesNotGetAssignedToOneField() {
        val nodes = snapshot().map { if (it.parent >= 0) it.copy(parent = 0) else it }
        assertNull(VtmanIconBlockParser.parse(nodes, code))
    }
}
