from pathlib import Path
import re
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Global header is already supplied by the MAIN Scaffold. Remove every nested no-arg header in tab bodies.
s=re.sub(r'(?m)^\s*TopHeader\(\)\s*(?://.*)?\n', '', s)

# Denomination fields: when value is zero, keep the editable text empty and show 0 as placeholder.
# This prevents Compose from reinserting 0 immediately after the user deletes the field, which caused 1 -> 10.
s=s.replace('value = counts[i].toString(),', 'value = if (counts[i] == 0) "" else counts[i].toString(),')
s=s.replace('onValueChange = { raw -> counts[i] = raw.filter(Char::isDigit).trimStart(\'0\').toIntOrNull()?.coerceAtMost(9999) ?: 0 },',
            'onValueChange = { raw: String -> counts[i] = raw.filter(Char::isDigit).trimStart(\'0\').toIntOrNull()?.coerceAtMost(9999) ?: 0 },')
s=s.replace('onValueChange = { raw: String -> counts[i] = raw.filter(Char::isDigit).trimStart(\'0\').toIntOrNull()?.coerceAtMost(9999) ?: 0 },',
            'onValueChange = { raw: String -> counts[i] = raw.filter(Char::isDigit).trimStart(\'0\').toIntOrNull()?.coerceAtMost(9999) ?: 0 },')
# Ensure denomination field has a visible zero without forcing it into the editable value.
needle='''                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),'''
if needle in s and 'placeholder = { Text("0") }' not in s[s.find(needle)-250:s.find(needle)+250]:
    s=s.replace(needle, '''                                singleLine = true,
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),''', 1)
s=s.replace(',\n                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)', '')

# Bank / Cước / COD: keep the user's edit buffer independent from the numeric model value.
# lastEmitted distinguishes the user's own changes from external changes such as Reset 0.
start=s.index('@Composable\nprivate fun MoneyEntryRow(')
end=s.index('\n@Composable\nprivate fun SettingsSection', start)
replacement='''@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(label) { mutableStateOf(if (value == 0L) "" else fmtMoney(value)) }
    var lastEmitted by remember(label) { mutableLongStateOf(value) }

    LaunchedEffect(value) {
        if (value != lastEmitted) {
            text = if (value == 0L) "" else fmtMoney(value)
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
                text = if (digits.isEmpty()) "" else fmtMoney(parsed)
                lastEmitted = parsed
                onValueChange(parsed)
            },
            modifier = Modifier.weight(1f).height(56.dp),
            singleLine = true,
            placeholder = { Text("0", fontSize = 14.sp) },
            suffix = { Text("đ", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        )
    }
}
'''
s=s[:start]+replacement+s[end:]
p.write_text(s)
