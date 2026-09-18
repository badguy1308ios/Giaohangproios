from pathlib import Path

path = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
text = path.read_text(encoding='utf-8')

name_line = '    val names = remember(customer?.id) { mutableStateListOf<String>().apply { add(customer?.name.orEmpty()); addAll(customer?.aliases.orEmpty()) } }\n'
menu_line = '    var showFormPhotoMenu by remember { mutableStateOf(false) }\n'

while name_line + name_line in text:
    text = text.replace(name_line + name_line, name_line)
while menu_line + menu_line in text:
    text = text.replace(menu_line + menu_line, menu_line)

path.write_text(text, encoding='utf-8')
print('Cleaned duplicated customer form state declarations')
