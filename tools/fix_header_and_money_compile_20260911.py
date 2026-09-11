from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Global header is already supplied by the MAIN Scaffold. Remove nested headers in tab bodies.
s=s.replace('\n        TopHeader()\n', '\n')

# Fix denomination input lambda typing and remove unsupported contentPadding on OutlinedTextField.
s=s.replace('onValueChange = { raw -> counts[i] = raw.filter(Char::isDigit).trimStart(\'0\').toIntOrNull()?.coerceAtMost(9999) ?: 0 },',
            'onValueChange = { raw: String -> counts[i] = raw.filter(Char::isDigit).trimStart(\'0\').toIntOrNull()?.coerceAtMost(9999) ?: 0 },')
s=s.replace(',\n                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)', '')

start=s.index('@Composable\nprivate fun MoneyEntryRow(')
end=s.index('\n@Composable\nprivate fun SettingsSection', start)
replacement='''@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(label) { mutableStateOf(if (value == 0L) "0" else fmtMoney(value)) }
    LaunchedEffect(value) {
        val current = parseMoney(text)
        if (current != value) text = if (value == 0L) "0" else fmtMoney(value)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = text,
            onValueChange = { raw: String ->
                val digits0 = raw.filter(Char::isDigit)
                val digits = if (text == "0" && digits0.length > 1) {
                    when {
                        digits0.startsWith("0") -> digits0.drop(1)
                        digits0.endsWith("0") -> digits0.dropLast(1)
                        else -> digits0
                    }
                } else digits0
                val parsed = digits.ifEmpty { "0" }.toLongOrNull() ?: 0L
                text = if (digits.isEmpty()) "" else fmtMoney(parsed)
                onValueChange(parsed)
            },
            modifier = Modifier.weight(1f).height(56.dp),
            singleLine = true,
            suffix = { Text("đ", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        )
    }
}
'''
s=s[:start]+replacement+s[end:]
p.write_text(s)
