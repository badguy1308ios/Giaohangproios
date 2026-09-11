from pathlib import Path
p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()
start = s.index('@Composable\nprivate fun MoneyEntryRow(')
end = s.index('\n@Composable\nprivate fun SettingsSection', start)
new = '''private object MoneyCommaVisualTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val formatted = raw.reversed().chunked(3).joinToString(",").reversed()
        val mapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                if (raw.isEmpty()) return 0
                val commasBefore = if (o == 0) 0 else ((raw.length - 1) / 3 - (raw.length - o - 1).coerceAtLeast(0) / 3).coerceAtLeast(0)
                return (o + commasBefore).coerceAtMost(formatted.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceIn(0, formatted.length)
                return formatted.take(t).count { it != ',' }.coerceAtMost(raw.length)
            }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), mapping)
    }
}

@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(label) { mutableStateOf(if (value == 0L) "" else value.toString()) }
    var lastEmitted by remember(label) { mutableLongStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastEmitted) {
            text = if (value == 0L) "" else value.toString()
            lastEmitted = value
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = text,
            onValueChange = { raw: String ->
                val digits = raw.filter(Char::isDigit).trimStart('0')
                val parsed = digits.toLongOrNull() ?: 0L
                text = digits
                lastEmitted = parsed
                onValueChange(parsed)
            },
            modifier = Modifier.weight(1f).height(56.dp),
            singleLine = true,
            placeholder = { Text("0", fontSize = 14.sp) },
            suffix = { Text("đ", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = MoneyCommaVisualTransformation,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        )
    }
}
'''
s = s[:start] + new + s[end:]
p.write_text(s)
