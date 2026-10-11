package com.example.giaohangpro.vtman

import org.junit.Assert.*
import org.junit.Test

class VtmanIconBlockParserTest {
    private val code = "SGSIND09102026-01"
    private fun snapshot(
        service: Boolean = true, emptyService: Boolean = false,
        address: List<String> = listOf("đường d33, tại - Khu Dân Cư Lộc An", "X. Lộc An - H. Long Thành - T. Đồng Nai")
    ): MutableList<VtmanElement> {
        val nodes = mutableListOf(
            VtmanElement(-1, 0, 0, 600, 1000),
            VtmanElement(0, 20, 100, 300, 140, code),
            VtmanElement(0, 310, 100, 380, 140, "TT505"),
            VtmanElement(0, 400, 100, 590, 140, "350,000 đ")
        )
        val fields = mutableListOf(listOf("Thành công"), listOf("Thành công"),
            address, listOf("VIÊN Xương khớp", "hộp lớn"))
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
    @Test fun missingTextIsLeftEmptyWithoutAnIconCountFailure() {
        val nodes = snapshot().map { if (it.text == "Thành công") it.copy(text = "") else it }
        val record = VtmanIconBlockParser.parse(nodes, code)!!
        assertEquals("", record.shop)
        assertEquals("", record.customer)
        assertEquals("COD,PXD", record.service)
    }
    @Test fun flattenedAccessibilityRowsUseIconPositions() {
        val nodes = snapshot().map { if (it.parent >= 0) it.copy(parent = 0) else it }
        val result = VtmanIconBlockParser.parse(nodes, code)!!
        assertEquals("Thành công", result.customer)
        assertEquals("đường d33, tại - Khu Dân Cư Lộc An X. Lộc An - H. Long Thành - T. Đồng Nai", result.address)
        assertEquals("VIÊN Xương khớp hộp lớn", result.goods)
        assertEquals("COD,PXD", result.service)
    }
    @Test fun unrelatedImagesOutsideCardDoNotChangeFieldCount() {
        val nodes = snapshot(service = false)
        nodes += VtmanElement(0, 0, 20, 40, 60, image = true)
        nodes += VtmanElement(0, 0, 900, 40, 940, image = true)
        nodes += VtmanElement(0, 25, 900, 49, 924, image = true)
        val result = VtmanIconBlockParser.parse(nodes, code)!!
        assertEquals("", result.service)
        assertEquals("VIÊN Xương khớp hộp lớn", result.goods)
    }

    @Test fun iconsMayHaveDifferentHorizontalPositionsAndSizes() {
        val nodes = snapshot().mapIndexed { i, node ->
            if (node.image && node.left < 100) node.copy(left = 15 + i % 15, right = 60 + i % 10)
            else node
        }
        val result = VtmanIconBlockParser.parse(nodes, code)!!
        assertEquals("Thành công", result.shop)
        assertEquals("Thành công", result.customer)
        assertEquals("COD,PXD", result.service)
    }

    @Test fun nestedImagesCountOnceAndExtraIconsDoNotRejectTheOrder() {
        val nodes = snapshot()
        val firstIcon = nodes.first { it.image }
        nodes += firstIcon.copy(left = firstIcon.left + 2, top = firstIcon.top + 2,
            right = firstIcon.right - 2, bottom = firstIcon.bottom - 2)
        val footer = nodes.indexOfLast { it.text == "Thành công" }
        val top = nodes[footer].top
        nodes[footer] = nodes[footer].copy(top = top + 80, bottom = top + 125)
        nodes += VtmanElement(0, 25, top, 49, top + 24, image = true)
        nodes += VtmanElement(0, 80, top, 500, top + 35, "Thông tin khác")
        val result = VtmanIconBlockParser.parse(nodes, code)!!
        assertEquals("Thành công", result.customer)
        assertEquals("VIÊN Xương khớp hộp lớn", result.goods)
        assertEquals("COD,PXD", result.service)
    }

    @Test fun allAddressLinesStayBeforeTheGoodsIconEvenWhenIconIsTopAligned() {
        val address = listOf("đường d33", "Khu Dân Cư Lộc An", "X. Lộc An", "H. Long Thành - Đồng Nai")
        val original = snapshot(service = false, address = address)
        val nodes = original.map { node ->
            if (node.image && node.left < 100) {
                val top = original[node.parent].top
                node.copy(top = top + 4, bottom = top + 28)
            } else node
        }
        val result = VtmanIconBlockParser.parse(nodes, code)!!
        assertEquals(address.joinToString(" "), result.address)
        assertEquals("VIÊN Xương khớp hộp lớn", result.goods)
        assertEquals("", result.service)
    }

    @Test fun oneTextNodeMayContainTheWholeWrappedAddress() {
        val nodes = snapshot().map { node ->
            if (node.parent >= 0 && node.text.startsWith("X. Lộc An")) node.copy(text = "")
            else if (node.text == "đường d33, tại - Khu Dân Cư Lộc An") node.copy(text = "")
            else node
        }
        val result = VtmanIconBlockParser.parse(nodes, code)!!
        assertEquals("đường d33, tại - Khu Dân Cư Lộc An X. Lộc An - H. Long Thành - T. Đồng Nai", result.address)
        assertEquals("VIÊN Xương khớp hộp lớn", result.goods)
    }

    @Test fun callPointIsTheCenterOfRightImageOnSecondIconRow() {
        val nodes = snapshot()
        nodes += VtmanElement(0, 540, 160, 580, 200, image = true)
        nodes += VtmanElement(0, 540, 310, 580, 350, image = true)
        val card = VtmanIconBlockParser.parseCard(nodes, code)!!
        assertEquals(CallPoint(560f, 255f), card.callPoint)
        assertEquals(card.record.shop, card.record.customer)
    }

    @Test fun callPointWorksWithoutServiceAndWithoutRowParents() {
        val nodes = snapshot(service = false).map { if (it.parent >= 0) it.copy(parent = 0) else it }
        val card = VtmanIconBlockParser.parseCard(nodes, code)!!
        assertEquals(CallPoint(560f, 255f), card.callPoint)
        assertEquals("", card.record.service)
    }

    @Test fun missingRightImageDoesNotInventAScreenPercentagePoint() {
        val nodes = snapshot().filterNot { it.image && it.left > 500 }
        val card = VtmanIconBlockParser.parseCard(nodes, code)!!
        assertNull(card.callPoint)
        assertEquals("Thành công", card.record.customer)
    }

}
