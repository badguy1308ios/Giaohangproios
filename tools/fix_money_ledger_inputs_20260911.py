from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
old='''                            OutlinedTextField(value = if (counts[i] == 0) "" else counts[i].toString(), onValueChange = { counts[i] = it.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(9999) ?: 0 }, modifier = Modifier.width(62.dp).height(48.dp), singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(38.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Thêm", tint = Color.White, modifier = Modifier.size(21.dp)) } }'''
new='''                            OutlinedTextField(
                                value = counts[i].toString(),
                                onValueChange = { raw -> counts[i] = raw.filter(Char::isDigit).trimStart('0').toIntOrNull()?.coerceAtMost(9999) ?: 0 },
                                modifier = Modifier.width(62.dp).height(54.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            )
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Add, "Thêm", tint = Orange, modifier = Modifier.size(21.dp)) }'''
if old not in s: raise SystemExit('denomination block not found')
s=s.replace(old,new,1)
start=s.index('@Composable\nprivate fun MoneyEntryRow(')
end=s.index('\n@Composable\nprivate fun SettingsSection', start)
replacement='''@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(label) { mutableStateOf(if (value == 0L) "0" else fmtMoney(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused) text = if (value == 0L) "0" else fmtMoney(value)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).trimStart('0').ifEmpty { "0" }
                val parsed = digits.toLongOrNull() ?: 0L
                onValueChange(parsed)
                text = fmtMoney(parsed)
            },
            modifier = Modifier.weight(1f).height(56.dp).onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused && text == "0") text = ""
                if (!state.isFocused && text.isBlank()) { text = "0"; onValueChange(0L) }
            },
            singleLine = true,
            suffix = { Text("đ", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        )
    }
}
'''
s=s[:start]+replacement+s[end:]
p.write_text(s)
