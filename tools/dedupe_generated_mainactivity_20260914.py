from pathlib import Path

p = Path("app/src/main/java/com/example/giaohangpro/MainActivity.kt")
s = p.read_text(encoding="utf-8")
original = s

back_customer_backup = "            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS\n"
screen_vtman = "        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n"
screen_backup = "        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })\n"
click_line = "            .clickable { onClick() }\n"

# Collapse only byte-identical consecutive duplicates for lines that may occur
# elsewhere in legitimate code.
for line in (back_customer_backup, click_line):
    while line * 2 in s:
        s = s.replace(line * 2, line)

# These two exact screen-rendering branches belong to a single when(screen)
# block and must exist exactly once each. Historical patch scripts inserted
# extra copies, sometimes adjacent and sometimes separated.
def keep_first_exact_line(text: str, target: str) -> str:
    seen = False
    out = []
    for line in text.splitlines(keepends=True):
        if line == target:
            if seen:
                continue
            seen = True
        out.append(line)
    return "".join(out)

s = keep_first_exact_line(s, screen_vtman)
s = keep_first_exact_line(s, screen_backup)

if s != original:
    p.write_text(s, encoding="utf-8")
    print("Removed only exact generated duplicates from MainActivity.kt")
else:
    print("MainActivity.kt already deduplicated")
