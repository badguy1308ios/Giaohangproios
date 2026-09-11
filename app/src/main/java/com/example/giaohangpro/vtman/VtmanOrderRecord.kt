package com.example.giaohangpro.vtman

data class VtmanOrderRecord(
    val waybill: String,
    val shop: String = "",
    val phone: String = "",
    val customer: String = "",
    val goods: String = "",
    val status: String = "",
    val cod: String = "",
    val address: String = "",
    val service: String = "",
)
