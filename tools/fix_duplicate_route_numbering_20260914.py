from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

block = '''    // Numbering base = active + locally delivered route stops, in original route order.
    // Terminal VTMan statuses never consume a route STT.
    val numberedRouteGroups = allGroups.filterNot { g ->
        g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) }
    }
'''

# Collapse any consecutive duplicate copies of the exact OrderList numbering block.
while block + block in s:
    s = s.replace(block + block, block)

p.write_text(s)
print('duplicate numberedRouteGroups declarations removed')
