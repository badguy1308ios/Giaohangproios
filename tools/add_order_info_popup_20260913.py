from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
start=s.index('@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\n@Composable\nfun OrderCard(')
end=s.index('@Composable\nfun OrderInfoRow(',start)
new=r'''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit, onNumberClick: () -> Unit = {}) {
    val context = LocalContext.current
    var infoPopup by remember(order.code) { mutableStateOf<Pair<String,String>?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).clickable { onNumberClick() }, contentAlignment = Alignment.Center) {
                    NumberCircle(index, selected = true)
                }
                Spacer(Modifier.width(8.dp))
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Text(order.code,color=Navy,fontSize=15.sp,fontWeight=FontWeight.ExtraBold,
                    modifier=Modifier.combinedClickable(onClick={},onLongClick={
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(order.code))
                        Toast.makeText(context,"Đã copy MVĐ ${order.code}",Toast.LENGTH_SHORT).show()
                    }))
                Spacer(Modifier.width(10.dp))
                Text(order.status.ifBlank{"—"},color=Navy,fontSize=15.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(order.amount,color=MoneyGreen,fontSize=13.sp,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            OrderInfoRow(Icons.Default.Store,order.shop.ifBlank{order.customer}){infoPopup="SHOP" to order.shop.ifBlank{order.customer}}
            OrderInfoRow(Icons.Default.Person,"${order.customer} - ${order.phone}",onClick={onCustomerClick(order)})
            OrderInfoRow(Icons.Default.LocationOn,order.address){infoPopup="ĐỊA CHỈ" to order.address}
            OrderInfoRow(Icons.Default.Inventory2,order.item){infoPopup="HÀNG HÓA" to order.item}
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){order.tags.forEach{Tag(it)}}
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                ActionButton("Đã giao",Icons.Default.CheckCircle,filled=true)
                ActionButton("Bank",Icons.Default.AccountBalance)
                ActionButton("Zalo",Icons.Default.Chat)
                ActionButton("SMS",Icons.Default.Sms)
                ActionButton("Gọi",Icons.Default.Call)
            }
        }
    }
    infoPopup?.let{(title,value)->
        AlertDialog(onDismissRequest={infoPopup=null},title={Text(title,fontWeight=FontWeight.Bold)},text={Text(value.ifBlank{"—"})},confirmButton={TextButton(onClick={infoPopup=null}){Text("ĐÓNG")}})
    }
}

'''
s=s[:start]+new+s[end:]
p.write_text(s)
print('order info popup applied')
