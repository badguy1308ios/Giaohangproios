from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

anchor = '''        FloatingActionButton(
            onClick = { showTools = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(44.dp),
            containerColor = Orange
        ) { Icon(Icons.Default.Edit, "Công cụ đơn", tint = Color.White) }
'''

replacement = '''        val orderListScope = rememberCoroutineScope()
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { orderListScope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.size(44.dp),
                containerColor = Color.White,
                contentColor = Orange
            ) { Icon(Icons.Default.KeyboardArrowUp, "Về đầu danh sách") }

            FloatingActionButton(
                onClick = { showTools = true },
                modifier = Modifier.size(44.dp),
                containerColor = Orange
            ) { Icon(Icons.Default.Edit, "Công cụ đơn", tint = Color.White) }
        }
'''

if anchor not in s:
    raise SystemExit('order tools FAB anchor not found')
s = s.replace(anchor, replacement, 1)
p.write_text(s)
print('order detail scroll-to-top button added above pencil')
