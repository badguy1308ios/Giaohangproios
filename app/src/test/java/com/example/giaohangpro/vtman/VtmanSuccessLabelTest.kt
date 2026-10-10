package com.example.giaohangpro.vtman

import org.junit.Assert.*
import org.junit.Test

class VtmanSuccessLabelTest {
    private val code = "154069642893"
    private val address = "D19 - KHU TÁI ĐỊNH CƯ LỘC AN - X. Bình Sơn - H. Long Thành - T. Đồng Nai"
    private val goods = "SHOP NGỌC GIÀU(0339753975) KHÁCH MUỐN XEM HÀNG THU SHIP 30K NẾU KO NHẬN HÀNG - XỬ LÝ ĐH LH: 0339753975 KO TỰ Ý HOÀN ĐƠN"
    private fun node(text: String, top: Int, button: Boolean = false) =
        VtmanScreenText(text, 90, top, 640, top + 40, button)
    private fun rows(customer: String = "LONG LAST") = listOf(
        node("$code TT500 395,000 đ", 400), node("Thành công", 460),
        node(customer, 520), node(address, 580), node(goods, 680),
        node("COD,PXD", 850), node("Thành công", 920)
    )
    private fun checkRecord(record: VtmanOrderRecord?) {
        assertNotNull(record)
        record!!
        assertEquals(code, record.waybill)
        assertEquals("Thành công", record.shop)
        assertEquals("LONG LAST", record.customer)
        assertEquals(address, record.address)
        assertEquals(goods, record.goods)
        assertEquals("TT500", record.status)
        assertEquals("395,000đ", record.cod)
        assertEquals("COD PXD", record.service)
        assertEquals("", record.phone) // Phone in goods must never become the recipient's phone.
    }
    @Test fun reportedVideoCardPreservesShopNamedSuccess() {
        checkRecord(VtmanFixedBlockParser.parsePositioned(rows(), code))
    }
    @Test fun textFallbackPreservesSameShopAndExcludesFooter() {
        checkRecord(VtmanFixedBlockParser.parse(rows().map { it.value }, code))
    }
    @Test fun equalShopAndRecipientNamesAreSeparateRows() {
        val result = VtmanFixedBlockParser.parsePositioned(rows("Thành công"), code)!!
        assertEquals("Thành công", result.shop)
        assertEquals("Thành công", result.customer)
        assertEquals(address, result.address)
        assertEquals(goods, result.goods)
    }
    @Test fun nextCardDoesNotLeakWhenCurrentCardHasNoFooter() {
        val next = listOf(node("154271048248 TT500 0 đ", 1000), node("Other shop", 1060), node("Other customer", 1120))
        checkRecord(VtmanFixedBlockParser.parsePositioned(rows().dropLast(1) + next, code))
        checkRecord(VtmanFixedBlockParser.parse((rows().dropLast(1) + next).map { it.value }, code))
    }
    @Test fun explicitButtonEndsIncompleteCardWithoutBorrowingNextCard() {
        val partial = rows().take(3) + node("Thành công", 600, button = true) +
            listOf(node("154271048248 TT500 0 đ", 680), node(address, 740), node(goods, 820))
        assertNull(VtmanFixedBlockParser.parsePositioned(partial, code))
    }
    @Test fun goodsNamedSuccessIsNotFooter() {
        val nodes = rows().map { if (it.value == goods) it.copy(value = "Thành công") else it }
        assertEquals("Thành công", VtmanFixedBlockParser.parsePositioned(nodes, code)!!.goods)
        assertEquals("Thành công", VtmanFixedBlockParser.parse(nodes.map { it.value }, code)!!.goods)
    }
}
