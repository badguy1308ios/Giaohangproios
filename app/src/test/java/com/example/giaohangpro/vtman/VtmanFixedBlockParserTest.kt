package com.example.giaohangpro.vtman

import org.junit.Assert.*
import org.junit.Test

class VtmanFixedBlockParserTest {
    private val waybill = "TPO1518765587"
    // Transcribed from the reported screen; ellipsis is kept, not invented data.
    private val address = "Đường n54 khu tái định cư Lộc an, bình Sơn, long thành, đồng Nai, Thị trấn Long Thành, ..."
    private val goods = "3xÁo Thun H98(t9) ❌ KHÁCH XEM KHÔNG LẤY PHẢI TRẢ 30k SHIP.❌ KHÔNG ĐỂ HÀNG NHÀ KHÁCH - KHÔNG CHO THỬ ( SHIP LÀM SAI CHỊU COD 100%)"

    private fun block(
        header: List<String> = listOf(waybill, "TT500", "234,000 đ"),
        shop: String = "H.1998 Áo Thun",
        destination: String = address
    ) = header + listOf(shop, "Xuan Thuy Mai", destination, goods, ",COD,PXD,XMG", "Thành công")

    private fun assertReportedOrder(record: VtmanOrderRecord?) {
        assertNotNull(record)
        record!!
        assertEquals(waybill, record.waybill)
        assertEquals("H.1998 Áo Thun", record.shop)
        assertEquals("Xuan Thuy Mai", record.customer)
        assertEquals(address, record.address)
        assertEquals(goods, record.goods)
        assertEquals("TT500", record.status)
        assertEquals("234,000đ", record.cod)
        assertEquals("COD PXD XMG", record.service)
        assertEquals("", record.phone)
    }

    @Test fun reportedOrderWithSeparateHeaderNodes() {
        assertReportedOrder(VtmanFixedBlockParser.parse(block(), waybill))
    }

    @Test fun reportedOrderWithMergedHeaderNode() {
        assertReportedOrder(VtmanFixedBlockParser.parse(block(listOf("$waybill TT500 234,000 đ")), waybill))
    }

    @Test fun reportedOrderWithMergedStatusAndCod() {
        assertReportedOrder(VtmanFixedBlockParser.parse(block(listOf(waybill, "TT500 234,000 đ")), waybill))
    }

    @Test fun numericShopPrefixesAreNotAddresses() {
        for (shop in listOf("H.1998 Áo Thun", "T.2026 Shop", "X.123 Shop")) {
            val result = VtmanFixedBlockParser.parse(block(shop = shop), waybill)!!
            assertEquals(shop, result.shop)
            assertEquals("Xuan Thuy Mai", result.customer)
            assertEquals(address, result.address)
        }
    }

    @Test fun existingAddressHintsStillWork() {
        for (destination in listOf("H. Long Thành", "H.Long Thành", "TP.Hồ Chí Minh", "Đ.N25", "Đ. 54", "X. Lộc An", "@10.7,106.8", "Ấp 2", "Khu A", "TĐC Lộc An")) {
            val result = VtmanFixedBlockParser.parse(block(shop = "Shop Áo Thun", destination = destination), waybill)!!
            assertEquals(destination, result.address)
            assertEquals("Xuan Thuy Mai", result.customer)
        }
    }

    @Test fun similarWaybillsAreNeverSubstituted() {
        for (other in listOf("TP01518765587", "XTPO1518765587", "TPO15187655870")) {
            assertNull(VtmanFixedBlockParser.parse(block(header = listOf(other, "TT500", "234,000 đ")), waybill))
        }
        assertFalse(VtmanFixedBlockParser.containsExpectedWaybill("Search", waybill))
    }

    private fun positioned(
        value: String,
        top: Int,
        left: Int = 90,
        right: Int = 640,
        height: Int = 42
    ) = VtmanScreenText(value, left, top, right, top + height)

    @Test fun positionedRowsFollowFiveIconOrderWithoutTextGuessing() {
        val code = "152023460476"
        val result = VtmanFixedBlockParser.parsePositioned(
            listOf(
                positioned(code, 460, 28, 220),
                positioned("TT500", 460, 230, 330),
                positioned("120,000 đ", 460, 500, 650),
                positioned("Bánh tráng sate phơi sương (Tp.Hà Nội)", 525),
                positioned("Lành Nguyễn", 585),
                positioned("Sđt 0784485971 Địa chỉ đường n11 khu tái định cư", 645),
                positioned("Bánh tráng phơi sương null", 730),
                positioned(",COD,PXD", 795),
                positioned("Thành công", 860)
            ),
            code
        )!!

        assertEquals("Bánh tráng sate phơi sương (Tp.Hà Nội)", result.shop)
        assertEquals("Lành Nguyễn", result.customer)
        assertEquals("Sđt 0784485971 Địa chỉ đường n11 khu tái định cư", result.address)
        assertEquals("Bánh tráng phơi sương null", result.goods)
        assertEquals("COD PXD", result.service)
        assertEquals("TT500", result.status)
        assertEquals("120,000đ", result.cod)
    }

    @Test fun positionedServiceRowIsOptional() {
        val code = "ECOSGN0261K09628"
        val result = VtmanFixedBlockParser.parsePositioned(
            listOf(
                positioned("$code TT500 0 đ", 460, 28, 650),
                positioned("CN Công Ty CP Dược Phẩm ECO (Tp.Hà Nội)", 525),
                positioned("NGUYỄN THÀNH ĐẠT", 585),
                positioned("D18 không chứa từ khóa địa chỉ", 645),
                positioned("Hóa đơn + thuốc", 705),
                positioned("Thành công", 780)
            ),
            code
        )!!

        assertEquals("CN Công Ty CP Dược Phẩm ECO (Tp.Hà Nội)", result.shop)
        assertEquals("NGUYỄN THÀNH ĐẠT", result.customer)
        assertEquals("D18 không chứa từ khóa địa chỉ", result.address)
        assertEquals("Hóa đơn + thuốc", result.goods)
        assertEquals("", result.service)
    }

    @Test fun shopCitySuffixIsNotMistakenForDeliveryAddress() {
        val ecoWaybill = "ECOSGN0261K09628"
        val result = VtmanFixedBlockParser.parse(
            listOf(
                "$ecoWaybill TT500 0 đ",
                "CN Công Ty CP Dược Phẩm ECO (Tp.Hà Nội)",
                "NGUYỄN THÀNH ĐẠT",
                "D18 Khu TĐC Lộc An Bình Sơn Ấp An Bình, X.Lộc An, H.Long Thành, Đồng Nai",
                "Hóa đơn + 00109114 Thuốc + Hóa Đơn",
                ",GGC",
                "Thành công"
            ),
            ecoWaybill
        )!!

        assertEquals("CN Công Ty CP Dược Phẩm ECO (Tp.Hà Nội)", result.shop)
        assertEquals("NGUYỄN THÀNH ĐẠT", result.customer)
        assertEquals("D18 Khu TĐC Lộc An Bình Sơn Ấp An Bình, X.Lộc An, H.Long Thành, Đồng Nai", result.address)
        assertEquals("0đ", result.cod)
    }

    @Test fun phonePickerUsesFirstDisplayedNumber() {
        assertEquals(
            "0868217663",
            VtmanFixedBlockParser.findPhone(
                listOf("Chọn số điện thoại để gọi", "0868217663", "0969859601")
            )
        )
    }

    @Test fun emptyResultDoesNotProduceAnOrder() {
        assertNull(VtmanFixedBlockParser.parse(listOf("Gạch phát offline", "Không có dữ liệu"), waybill))
    }

    @Test fun missingFieldsStayMissingInsteadOfInventingValues() {
        val result = VtmanFixedBlockParser.parse(listOf(waybill, "TT500"), waybill)!!
        assertEquals("", result.customer)
        assertEquals("", result.address)
        assertEquals("", result.cod)
    }
}
