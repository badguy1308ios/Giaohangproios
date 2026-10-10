package com.example.giaohangpro

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DeliveryGroupCard(
    routeStt: Int?,
    group: DeliveryGroup,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit,
    onRedeliver: () -> Unit
) {
    val context = LocalContext.current
    val primary = group.orders.first()
    val customer = group.customer
    val phone = (customer?.phone ?: primary.phone).filter(Char::isDigit)

    fun openZalo() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/$phone")))
        }
    }
    fun openSms() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$phone")))
        }
    }
    fun openCall() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
        }
    }

    // Chỉ Customer ID có từ 2 MVĐ trở lên mới có phần đầu nhóm.
    if (group.orders.size > 1) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (delivered) Color(0xFF9DCEA8) else Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // Phần dùng chung: STT - số đơn - tổng tiền.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!delivered && group.orders.any { !isTerminalOrderStatus(it.status) }) {
                        Box(Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() }, contentAlignment = Alignment.Center) {
                            RouteStateCircle(routeStt, deliveryGroupHasCoordinate(group), false)
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${group.orders.size} đơn • Tổng COD", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(groupMoneyText(group), color = MoneyGreen, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Cụm nút thao tác dùng chung cho tất cả MVĐ trong Customer ID này.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        delivered || group.orders.all { normalizeOrderStatus(it.status) == "TT505" } ->
                            ActionButton("Giao lại", Icons.Default.RestartAlt, filled = true, buttonWeight = 1.28f, onClick = onRedeliver)
                        else ->
                            ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered)
                    }
                    ActionButton("Bank", Icons.Default.AccountBalance, onClick = {})
                    ActionButton("Zalo", Icons.Default.Chat, onClick = { openZalo() })
                    ActionButton("SMS", Icons.Default.Sms, onClick = { openSms() })
                    ActionButton("Gọi", Icons.Default.Call, onClick = { openCall() })
                }

                // Mỗi đơn vẫn giữ nguyên cấu trúc chi tiết riêng như giao diện cũ.
                group.orders.forEachIndexed { index, order ->
                    if (index > 0) HorizontalDivider(color = Border)
                    GroupedOrderDetail(
                        order = order,
                        delivered = delivered,
                        onCustomerClick = onCustomerClick
                    )
                }
            }
        }
    } else {
        // Customer ID chỉ có 1 MVĐ: hiển thị bình thường, không có phần đầu nhóm.
        SingleOrderDetailCard(
            order = primary,
            routeStt = routeStt,
            hasRealCoordinate = deliveryGroupHasCoordinate(group),
            delivered = delivered,
            onNumberClick = onNumberClick,
            onCustomerClick = onCustomerClick,
            onDelivered = onDelivered,
            onRedeliver = onRedeliver,
            onZalo = { openZalo() },
            onSms = { openSms() },
            onCall = { openCall() }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CopyableWaybillCode(
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Text(
        text = code,
        color = Navy,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MVĐ", code))
                Toast.makeText(context, "Đã copy MVĐ $code", Toast.LENGTH_SHORT).show()
            }
        )
    )
}

@Composable
internal fun GroupedOrderDetail(
    order: Order,
    delivered: Boolean,
    onCustomerClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrderHeading(order, delivered, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
        OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
        OrderInfoRow(Icons.Default.LocationOn, order.address)
        OrderInfoRow(Icons.Default.Inventory2, order.item)
        if (order.tags.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            OrderTags(order.tags)
        }
    }
}

@Composable
private fun SingleOrderDetailCard(
    order: Order,
    routeStt: Int?,
    hasRealCoordinate: Boolean,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit,
    onRedeliver: () -> Unit,
    onZalo: () -> Unit,
    onSms: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (delivered) Color(0xFF9DCEA8) else Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!delivered && !isTerminalOrderStatus(order.status)) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() },
                        contentAlignment = Alignment.Center
                    ) { RouteStateCircle(routeStt, hasRealCoordinate, false) }
                    Spacer(Modifier.width(7.dp))
                }
                OrderHeading(order, delivered, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
            OrderInfoRow(Icons.Default.LocationOn, order.address)
            OrderInfoRow(Icons.Default.Inventory2, order.item)
            if (order.tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                OrderTags(order.tags)
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    delivered || normalizeOrderStatus(order.status) == "TT505" ->
                        ActionButton("Giao lại", Icons.Default.RestartAlt, filled = true, buttonWeight = 1.28f, onClick = onRedeliver)
                    else ->
                        ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered)
                }
                ActionButton("Bank", Icons.Default.AccountBalance, onClick = {})
                ActionButton("Zalo", Icons.Default.Chat, onClick = onZalo)
                ActionButton("SMS", Icons.Default.Sms, onClick = onSms)
                ActionButton("Gọi", Icons.Default.Call, onClick = onCall)
            }
        }
    }
}

@Composable
fun OrderInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(vertical = 3.dp)
    Row(
        rowModifier,
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp).padding(top = 1.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = Navy,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            softWrap = true
        )
    }
}

@Composable
private fun WaybillQrButton(code: String) {
    var showQr by remember(code) { mutableStateOf(false) }
    val qrBitmap = remember(code) {
        runCatching {
            com.journeyapps.barcodescanner.BarcodeEncoder().encodeBitmap(
                code,
                com.google.zxing.BarcodeFormat.QR_CODE,
                720,
                720
            )
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(0.5.dp, Color(0xFFD8D8D8), RoundedCornerShape(6.dp))
            .clickable(enabled = qrBitmap != null) { showQr = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.QrCode2,
            contentDescription = "Tạo mã QR cho MVĐ $code",
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showQr && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Mã QR MVĐ", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Mã QR của MVĐ $code",
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(code, color = Navy, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQr = false }) {
                    Text("ĐÓNG", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OrderHeading(order: Order, delivered: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CopyableWaybillCode(order.code, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(5.dp))
            WaybillQrButton(order.code)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (!delivered) {
                Text(normalizeOrderStatus(order.status), color = orderStatusColor(order.status),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(order.amount, color = MoneyGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OrderTags(tags: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        tags.forEach { Tag(it) }
    }
}

@Composable
fun Tag(text: String) {
    val highlighted = text.trim().uppercase() in HighlightServiceCodes
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) Color(0xFFD32F2F) else Color(0xFFEDE9E4))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = if (highlighted) Color.White else Navy,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RowScope.ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean = false,
    buttonWeight: Float = 1f,
    onClick: () -> Unit = {}
) {
    Box(
        Modifier
            .height(36.dp)
            .weight(buttonWeight)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (filled) Orange else OrangeLight)
            .border(1.dp, Orange, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (filled) Color.White else OrangeDark,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                color = if (filled) Color.White else Navy,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

