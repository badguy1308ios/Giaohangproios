from pathlib import Path

p = Path("app/src/main/java/com/example/giaohangpro/MainActivity.kt")
s = p.read_text(encoding="utf-8")
original = s

# These are exact generated fragments that historical patch scripts inserted
# more than once. Collapse only consecutive byte-identical copies, keeping one.
exact_lines = [
    "            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS\n",
    "        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n",
    "        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n",
    "            .clickable { onClick() }\n",
]

for line in exact_lines:
    while line * 2 in s:
        s = s.replace(line * 2, line)

# Older runs also produced repeated VTMan/backup pairs. This is still an exact
# duplicate collapse and does not alter either branch's behavior.
screen_pair = (
    "        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n"
    "        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n"
)
while screen_pair * 2 in s:
    s = s.replace(screen_pair * 2, screen_pair)

if s != original:
    p.write_text(s, encoding="utf-8")
    print("Removed only exact generated duplicates from MainActivity.kt")
else:
    print("MainActivity.kt already deduplicated")
