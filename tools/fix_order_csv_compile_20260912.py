from pathlib import Path
p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()
old = '''        lines.drop(1).forEach{line->val c=cells(line);fun g(i:Int)=if(i>=0&&i<c.size)c[i]else"";val code=g(ic).trim();if(code.isNotBlank()){val o=Order(code,g(iname),g(ip),g(ia),g(ii),g(icod),g(isv).split(' ',';',',','|').map(String::trim).filter(String::isNotBlank),shop=g(ishop),status=g(ist).ifBlank{"Chưa giao"});val k=orderState.indexOfFirst{it.code.equals(code,true)};if(k>=0)orderState[k]=o else orderState.add(o);n++}}'''
new = '''        lines.drop(1).forEach { line ->
            val c = cells(line)
            fun g(i: Int): String = if (i >= 0 && i < c.size) c[i] else ""
            val code = g(ic).trim()
            if (code.isNotBlank()) {
                val serviceTags = g(isv)
                    .split(' ', ';', ',', '|')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                val order = Order(
                    code = code,
                    customer = g(iname),
                    phone = g(ip),
                    address = g(ia),
                    item = g(ii),
                    amount = g(icod),
                    tags = serviceTags,
                    shop = g(ishop),
                    status = g(ist).ifBlank { "Chưa giao" }
                )
                val k = orderState.indexOfFirst { it.code.equals(code, true) }
                if (k >= 0) orderState[k] = order else orderState.add(order)
                n++
            }
        }'''
if old not in s:
    raise SystemExit('target order csv line not found')
s = s.replace(old, new, 1)
p.write_text(s)
print('fixed order CSV Kotlin syntax')
