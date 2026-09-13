from pathlib import Path

p = Path("app/src/main/java/com/example/giaohangpro/MainActivity.kt")
s = p.read_text(encoding="utf-8")
original = s

# These are exact duplicate fragments previously inserted repeatedly by the
# old final-patch workflow. Keep one canonical copy of each fragment only.
back_customer_backup = "            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS\n"
while back_customer_backup * 2 in s:
    s = s.replace(back_customer_backup * 2, back_customer_backup)

screen_pair = (
    "        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n"
    "        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n"
)
while screen_pair * 2 in s:
    s = s.replace(screen_pair * 2, screen_pair)

click_line = "            .clickable { onClick() }\n"
while click_line * 2 in s:
    s = s.replace(click_line * 2, click_line)

if s != original:
    p.write_text(s, encoding="utf-8")
    print("Removed only exact generated duplicates from MainActivity.kt")
else:
    print("MainActivity.kt already deduplicated")
