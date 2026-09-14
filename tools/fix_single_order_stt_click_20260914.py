from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# Single-order detail cards must use the same STT click callback as grouped cards.
old_call = '''        SingleOrderDetailCard(
            order = primary,
            routeStt = routeStt,
            hasRealCoordinate = deliveryGroupHasCoordinate(group),
            delivered = delivered,
            onCustomerClick = onCustomerClick,
            onDelivered = onDelivered,'''
new_call = '''        SingleOrderDetailCard(
            order = primary,
            routeStt = routeStt,
            hasRealCoordinate = deliveryGroupHasCoordinate(group),
            delivered = delivered,
            onNumberClick = onNumberClick,
            onCustomerClick = onCustomerClick,
            onDelivered = onDelivered,'''
if old_call in s:
    s = s.replace(old_call, new_call, 1)
elif 'onNumberClick = onNumberClick' not in s:
    raise SystemExit('SingleOrderDetailCard call anchor not found')

old_sig = '''private fun SingleOrderDetailCard(
    order: Order,
    routeStt: Int?,
    hasRealCoordinate: Boolean,
    delivered: Boolean,
    onCustomerClick: () -> Unit,'''
new_sig = '''private fun SingleOrderDetailCard(
    order: Order,
    routeStt: Int?,
    hasRealCoordinate: Boolean,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,'''
if old_sig in s:
    s = s.replace(old_sig, new_sig, 1)
elif 'private fun SingleOrderDetailCard(' in s and 'onNumberClick: () -> Unit' not in s[s.find('private fun SingleOrderDetailCard('):s.find('private fun SingleOrderDetailCard(')+500]:
    raise SystemExit('SingleOrderDetailCard signature anchor not found')

old_click = '''                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { },
                        contentAlignment = Alignment.Center
                    ) { RouteStateCircle(routeStt, hasRealCoordinate, false) }'''
new_click = '''                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() },
                        contentAlignment = Alignment.Center
                    ) { RouteStateCircle(routeStt, hasRealCoordinate, false) }'''
if old_click in s:
    s = s.replace(old_click, new_click, 1)
elif '.clickable { onNumberClick() }' not in s[s.find('private fun SingleOrderDetailCard('):s.find('private fun SingleOrderDetailCard(')+1200]:
    raise SystemExit('SingleOrderDetailCard STT click anchor not found')

p.write_text(s)
print('single-order STT circle now switches to map through onNumberClick')
