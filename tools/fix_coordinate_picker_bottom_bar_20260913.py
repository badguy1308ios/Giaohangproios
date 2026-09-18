from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

old_dialog = 'Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {'
new_dialog = 'Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {'
if old_dialog in s:
    s = s.replace(old_dialog, new_dialog, 1)
elif new_dialog not in s:
    raise SystemExit('coordinate picker Dialog anchor not found')

old_surface = '''            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),'''
new_surface = '''            modifier = Modifier.fillMaxSize(),'''
if old_surface in s:
    s = s.replace(old_surface, new_surface, 1)

old_row = '''                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {'''
old_row_previous = '''                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {'''
new_row = '''                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {'''
if old_row in s:
    s = s.replace(old_row, new_row, 1)
elif old_row_previous in s:
    s = s.replace(old_row_previous, new_row, 1)
elif new_row not in s:
    raise SystemExit('coordinate picker action row anchor not found')

p.write_text(s)
print('coordinate picker HUY/LUU moved 58dp above system navigation')
