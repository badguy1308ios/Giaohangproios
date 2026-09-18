from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
s=s.replace('enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS }','enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER }')
s=s.replace('AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN })','AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER })\n        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })')
s=s.replace('private fun SettingsScreen(onBack: () -> Unit) {','private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit) {')
s=s.replace('SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN") { Toast.makeText(context,"Bảng kê tiền",Toast.LENGTH_SHORT).show() }','SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)')
p.write_text(s)
