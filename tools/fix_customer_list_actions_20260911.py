from pathlib import Path
import re

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text(encoding='utf-8')

pattern = re.compile(r'''\s*CustomerQuickActions\(\s*// Hàng 4 nút thao tác phía dưới thẻ\.\s*\n\s*onZalo = \{.*?\n\s*onNavigate = \{.*?\n\s*\}\s*\n\s*\)''', re.S)

replacement = '''
            CustomerQuickActions(
                onZalo = {
                    val raw = if (customer.primaryCanZalo && customer.phone.isNotBlank()) customer.phone
                    else customer.extraPhones.firstOrNull { it.canZalo }?.number
                    raw?.let {
                        val number = it.filter(Char::isDigit)
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/$number")))
                        }
                    }
                },
                onSms = {
                    val raw = if (customer.primaryCanSms && customer.phone.isNotBlank()) customer.phone
                    else customer.extraPhones.firstOrNull { it.canSms }?.number
                    raw?.let {
                        val number = it.filter(Char::isDigit)
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("smsto:$number")))
                        }
                    }
                },
                onCall = {
                    val raw = if (customer.primaryCanCall && customer.phone.isNotBlank()) customer.phone
                    else customer.extraPhones.firstOrNull { it.canCall }?.number
                    raw?.let {
                        val number = it.filter(Char::isDigit)
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")))
                        }
                    }
                },
                onNavigate = {
                    pointFromStrings(customer.latitude, customer.longitude)?.let { openGoogleNavigation(context, it) }
                }
            )'''

s2, n = pattern.subn(replacement, s, count=1)
if n == 0:
    if 'Toast.makeText(context, "Mở Zalo:' in s:
        raise SystemExit('CustomerQuickActions demo block found but pattern did not match')
    print('Customer list quick actions already fixed; no changes.')
else:
    p.write_text(s2, encoding='utf-8')
    print('Updated customer list quick actions to match detail screen behavior.')
