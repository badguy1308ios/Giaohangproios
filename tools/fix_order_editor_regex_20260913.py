from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# Fix only the Kotlin regex escaping produced by the order editor patch.
# Keep every existing order/map/customer feature unchanged.
s2 = s.replace('Regex("[\\s,;]+" )', 'Regex("[\\\\s,;]+" )')
if s2 == s:
    print('order editor regex already valid; no source changes required')
else:
    p.write_text(s2)
    print('fixed order editor Kotlin regex escape')
