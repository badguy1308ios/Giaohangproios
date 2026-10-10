# Architecture step 2a: extract Orders

Baseline: build 39, commit ac5e0675636976eb740acccaefff24ef6dda07bb.

Moved unchanged from MainActivity.kt:
- OrderModels.kt: Order, status normalization and status styling helpers.
- OrderGrouping.kt: delivery groups, matched customer coordinates and totals.
- OrderListScreen.kt: order list, filters, add/edit/delete dialogs and UI callbacks.
- OrderComponents.kt: group/detail cards, waybill copy/QR, tags and action buttons.

MainActivity.kt: 4,302 -> 3,314 lines. Only cross-file private declarations became
internal; moved function bodies were compared against the baseline and are
unchanged. Existing public APIs and Kotlin package remain unchanged.

MainViewModel persistence, order import, STT and delivery/re-delivery mutation logic
remain in place for the later ViewModel stage. Customer screens, Map/GPS behavior,
VTMAN extraction, tools scripts and dependencies are not modified.

Validation: structural source comparison, cross-file private-reference audit,
git diff --check, then CI testDebugUnitTest assembleDebug. Existing coordinate
identity and grouping regression tests now exercise the extracted code.

Device acceptance before the next extraction:
1. Open order list; search/filter and open each order in a multi-order group.
2. Add/edit/delete an individual order; copy waybill and show QR.
3. Mark delivered, then Giao lại: verify STT and map/list visibility.
4. Open linked customer and return; verify selected order and map pin.
