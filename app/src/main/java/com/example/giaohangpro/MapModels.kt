package com.example.giaohangpro

data class MapPoint(
    val latitude: Double,
    val longitude: Double
)

data class MapOrderMarker(
    val order: Order,
    val point: MapPoint,
    val number: Int,
    val hasRealCoordinate: Boolean,
    val showNumber: Boolean = true
)

// Coordinates have already been resolved by groupRepresentative. Do not look up
// another customer from the representative's display name or address.
internal fun buildMapOrderMarkers(
    orders: List<Order>,
    pendingPoint: MapPoint,
    routeNumberingEnabled: Boolean,
    stableRouteNumbers: Map<String, Int>
): List<MapOrderMarker> = orders.mapIndexed { index, order ->
    val realPoint = pointFromStrings(order.latitude, order.longitude)
    MapOrderMarker(order, realPoint ?: pendingPoint,
        stableRouteNumbers[order.code] ?: (index + 1), realPoint != null,
        routeNumberingEnabled && realPoint != null)
}
