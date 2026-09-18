from pathlib import Path
import re
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
# Collapse duplicated navigation cases.
case_settings='        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER })\n'
case_money='        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })\n'
while (case_settings+case_money)*2 in s:
    s=s.replace((case_settings+case_money)*2, case_settings+case_money)
# Keep only the first complete settings/ledger implementation before map constants.
start=s.find('\n@Composable\nprivate fun SettingsScreen(')
marker=s.find('\nprivate val DEFAULT_MAP_POINT =', start)
if start < 0 or marker < 0:
    raise SystemExit('settings/map marker not found')
region=s[start:marker]
# Locate repeated SettingsScreen starts inside region and trim at second one.
starts=[m.start() for m in re.finditer(r'\n@Composable\nprivate fun SettingsScreen\(', region)]
if len(starts)>1:
    region=region[:starts[1]]
s=s[:start]+region+'\n'+s[marker:]
p.write_text(s)
print('cleaned settings/money ledger duplicates')
