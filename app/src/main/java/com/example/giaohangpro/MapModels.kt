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
