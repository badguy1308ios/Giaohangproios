# Architecture step 2b: extract Customers

Baseline: build 48, commit 15629e91ff0e1d6abdb6296861e7525d5b943759.

Moved from MainActivity.kt without changing function bodies:
- CustomerModels.kt: Customer, CustomerPhone, CustomerAddress,
  CustomerBackupResult and the shared initials helper.
- CustomerListScreen.kt: customer list/search/cards and their action components.
- CustomerDetailScreen.kt: details, gate photo actions and shared page header.
- CustomerFormScreen.kt: add/edit form, phone/address drafts, street dropdown,
  coordinate confirmation and photo components.
- CustomerBackupScreen.kt: existing backup/sync/restore UI.
- StreetNameManagementScreen.kt: existing street name management UI.

MainActivity.kt shrank from 3,314 to 1,995 lines. The Kotlin package and public
model signatures remain the same. Only CustomerBackupScreen,
StreetNameManagementScreen, createInitials and CardWhite change visibility from
private to internal to allow their existing callers across files.

Pre-build review requested by Hải:
- Exact moved-body comparison, allowing only those visibility changes.
- App navigation/callbacks and the complete MainViewModel compared byte-for-byte.
- Cross-file private declaration/reference audit: no matches.
- git diff --check.

Photo storage/decoding, coordinate safety, customer identity matching, database
and JSON persistence, backup format and ViewModel mutations are unchanged.
VTMAN, Map/GPS, Orders, dependencies and tools scripts are outside this step.
CI runs the existing testDebugUnitTest suite and assembleDebug after review.

Device acceptance:
1. Open/search customer list and inspect an existing customer's gate photo.
2. Edit name/note and save; verify the saved coordinates and photo remain.
3. Add/edit customer and select coordinates, exercising the existing confirmation.
4. Select/capture a photo, save, then reopen the customer.
5. Open street management and backup/sync screens; verify their usual controls.

MainViewModel extraction remains the next separate architecture step.
