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
new_row = '''                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {'''
if old_row not in s:
    raise SystemExit('coordinate picker action row anchor not found')
s = s.replace(old_row, new_row, 1)

p.write_text(s)
print('coordinate picker bottom actions kept above navigation bar')
