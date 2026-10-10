package com.example.giaohangpro

private fun uiNormPhone(raw: String): String = CustomerPhoneLookup.normalize(raw)

internal data class DeliveryGroup(val key: String, val customer: Customer?, val orders: List<Order>)

internal fun deliveryGroupPoint(group: DeliveryGroup): MapPoint? {
    val c = group.customer
    c?.let { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    group.orders.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    if (c == null) return null
    c.extraAddresses.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    return null
}

internal fun deliveryGroupHasCoordinate(group: DeliveryGroup): Boolean = deliveryGroupPoint(group) != null

internal fun buildDeliveryGroups(orders: List<Order>, customers: List<Customer>): List<DeliveryGroup> {
    val customerByPhone = CustomerPhoneLookup(customers)
    val grouped = linkedMapOf<String, MutableList<Order>>()
    val customerForKey = mutableMapOf<String, Customer?>()
    orders.forEach { order ->
        val normalizedPhone = uiNormPhone(order.phone)
        val customer = customerByPhone.find(order.phone)
        val key = when {
            customer != null -> "C:${customer.id}"
            normalizedPhone.isNotBlank() -> "P:$normalizedPhone"
            else -> "O:${order.code}"
        }
        val displayKey = when {
            order.locallyDelivered -> "$key:LOCAL"
            isTerminalOrderStatus(order.status) -> "$key:TERMINAL"
            else -> key
        }
        grouped.getOrPut(displayKey) { mutableListOf() }.add(order)
        customerForKey[displayKey] = customer
    }
    return grouped.map { (key, list) -> DeliveryGroup(key, customerForKey[key], list) }
}

private fun orderMoneyValue(raw: String): Long = raw.filter(Char::isDigit).toLongOrNull() ?: 0L
internal fun groupMoneyText(group: DeliveryGroup): String = fmtMoney(group.orders.sumOf { orderMoneyValue(it.amount) }) + "đ"
internal fun groupRepresentative(group: DeliveryGroup): Order {
    val first = group.orders.first()
    val c = group.customer
    val coordinates = CustomerCoordinateSafety.preferredCoordinates(c, first.latitude, first.longitude)
    return first.copy(
        customer = c?.name ?: first.customer,
        phone = c?.phone ?: first.phone,
        address = c?.address?.takeIf(String::isNotBlank) ?: first.address,
        latitude = coordinates.first,
        longitude = coordinates.second,
        amount = groupMoneyText(group),
        item = if (group.orders.size > 1) "${group.orders.size} MVĐ" else first.item
    )
}

