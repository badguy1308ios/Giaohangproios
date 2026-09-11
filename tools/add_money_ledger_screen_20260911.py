from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
marker='\n@Composable\nprivate fun SettingsSection('
ledger=r'''

private fun fmtMoney(v: Long): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v)
private fun parseMoney(v: String): Long = v.filter(Char::isDigit).toLongOrNull() ?: 0L

@Composable
private fun MoneyLedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("money_ledger_session", android.content.Context.MODE_PRIVATE) }
    val denoms = listOf(500000L, 200000L, 100000L, 50000L)
    val counts = remember { mutableStateListOf<Int>().apply { denoms.indices.forEach { add(prefs.getInt("count_$it", 0)) } } }
    var bank by remember { mutableStateOf(prefs.getLong("bank", 0L)) }
    var fee by remember { mutableStateOf(prefs.getLong("fee", 0L)) }
    var cod by remember { mutableStateOf(prefs.getLong("cod", 0L)) }
    val cash = denoms.indices.sumOf { denoms[it] * counts[it].toLong() }
    val result = cash + bank - fee - cod
    val resultBg = if (result == 0L) Color(0xFFE6F4EA) else if (result < 0L) Color(0xFFFDE8E7) else Color(0xFFFFECDD)
    val resultColor = if (result == 0L) Color(0xFF2E7D32) else if (result < 0L) Color(0xFFB3261E) else OrangeDark

    LaunchedEffect(counts.toList(), bank, fee, cod) {
        prefs.edit().apply {
            counts.forEachIndexed { i, v -> putInt("count_$i", v) }
            putLong("bank", bank); putLong("fee", fee); putLong("cod", cod)
        }.apply()
    }

    fun resetAll() {
        counts.indices.forEach { counts[it] = 0 }
        bank = 0L; fee = 0L; cod = 0L
        prefs.edit().clear().apply()
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("BẢNG KÊ TIỀN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(6.dp).height(42.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Đóng bảng kê", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = resultBg)) {
                Column(Modifier.padding(10.dp)) {
                    Text("KẾT QUẢ BẢNG TÍNH", color = resultColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text((if (result > 0) "+" else "") + fmtMoney(result) + "đ", color = resultColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("(Tiền mặt + Bank) - (Cước + COD)", color = TextGray, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ĐẾM TIỀN MẶT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { resetAll() }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(16.dp)) { Text("Reset 0", fontSize = 12.sp) }
            }

            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    denoms.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtMoney(d) + "đ", modifier = Modifier.width(84.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { if (counts[i] > 0) counts[i]-- }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Remove, "Bớt", tint = Orange, modifier = Modifier.size(19.dp)) }
                            OutlinedTextField(value = if (counts[i] == 0) "" else counts[i].toString(), onValueChange = { counts[i] = it.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(9999) ?: 0 }, modifier = Modifier.width(62.dp).height(48.dp), singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(38.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Thêm", tint = Color.White, modifier = Modifier.size(21.dp)) } }
                            Text(fmtMoney(d * counts[i].toLong()) + "đ", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TỔNG TIỀN MẶT", modifier = Modifier.weight(1f), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMoney(cash) + "đ", color = OrangeDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("ĐỐI SOÁT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyEntryRow("Bank", bank) { bank = it }
                    MoneyEntryRow("Cước", fee) { fee = it }
                    MoneyEntryRow("COD", cod) { cod = it }
                }
            }
        }
    }
}

@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = if (value == 0L) "" else fmtMoney(value), onValueChange = { onValueChange(parseMoney(it)) }, modifier = Modifier.weight(1f).height(50.dp), singleLine = true, placeholder = { Text("0", fontSize = 13.sp) }, suffix = { Text("đ", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
    }
}
'''
if 'private fun MoneyLedgerScreen' not in s:
    s=s.replace(marker,ledger+marker)
p.write_text(s)
