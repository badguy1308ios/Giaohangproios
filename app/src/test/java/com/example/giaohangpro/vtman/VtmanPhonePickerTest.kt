package com.example.giaohangpro.vtman

import org.junit.Assert.*
import org.junit.Test

class VtmanPhonePickerTest {
    private fun node(value: String, top: Int, bottom: Int = top + 40) = VtmanScreenText(value, 30, top, 650, bottom)

    @Test fun selectsTopRowRegardlessOfTraversalOrderAndIgnoresBackground() {
        val nodes = listOf(node("0969859601", 1340), node("0901111222", 500),
            node("Chọn số điện thoại để gọi", 1160), node("0868217663", 1240))
        assertEquals("0868217663", VtmanPhonePicker.firstPhone(nodes))
    }

    @Test fun combinedDescriptionDoesNotAppendDigitFromSecondNumber() {
        assertEquals("0868217663", VtmanPhonePicker.firstPhone(listOf(
            node("Chọn số điện thoại để gọi\n0868217663\n0969859601", 1160, 1440))))
    }

    @Test fun parentBoundsDoNotHideSeparateRows() {
        assertEquals("0868217663", VtmanPhonePicker.firstPhone(listOf(
            node("Chọn số điện thoại để gọi 0969859601 0868217663", 1100, 1450),
            node("Chọn số điện thoại để gọi", 1160), node("0868217663", 1240), node("0969859601", 1340))))
    }

    @Test fun acceptsFormattedRowsButRequiresPickerTitle() {
        assertEquals("0868217663", VtmanPhonePicker.firstPhone(listOf(
            node("Chọn số điện thoại\nđể gọi", 1160), node("+84 868 217 663", 1240))))
        assertNull(VtmanPhonePicker.firstPhone(listOf(node("0868217663", 1240))))
    }
}
