from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
if 'import androidx.compose.foundation.gestures.draggable' not in s:
    s=s.replace('import androidx.compose.foundation.combinedClickable\n','import androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.gestures.draggable\nimport androidx.compose.foundation.gestures.rememberDraggableState\nimport androidx.compose.foundation.gestures.Orientation\n',1)
old='            Box(Modifier.fillMaxSize().padding(padding).background(Background)) {'
new='''            var swipeDx by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier.fillMaxSize().padding(padding).background(Background)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> swipeDx += delta },
                        onDragStarted = { swipeDx = 0f },
                        onDragStopped = {
                            val tabs = listOf(Tab.MAP, Tab.ORDERS, Tab.CUSTOMERS)
                            val i = tabs.indexOf(tab)
                            if (swipeDx < -120f && i < tabs.lastIndex) tab = tabs[i + 1]
                            if (swipeDx > 120f && i > 0) tab = tabs[i - 1]
                            swipeDx = 0f
                        }
                    )
            ) {'''
if old not in s: raise SystemExit('main Box not found')
s=s.replace(old,new,1)
p.write_text(s)
print('tab swipe applied')
