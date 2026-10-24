# Gate photo audit — 2026-10-24

## Evidence and limits

The supplied screenshot shows an unreadable nonblank photo reference for customer Võ Anh Kiệt. It does not provide the installed version, stored URI, original bytes or provider permission state. No claim that this exact customer's root cause or recovery has been proven on device.

Confirmed legacy defect: before 17a3ea1, OpenDocument callbacks saved the external URI directly even when takePersistableUriPermission failed inside runCatching. 17a3ea1/6074d37 copied newly selected images, but never migrated existing external references. Old images continued depending on external provider availability and source lifetime.

Confirmed remaining backup defect: syncNow committed local JSON snapshots before portable backup. If portable backup failed, a later unchanged-data sync returned before retrying it. Fixed by retrying portable backup on unchanged data too.

## Paths inspected

- Camera and gallery: current new-photo copy/validate/rename; pending capture URI; gallery legacy permission handling. New copies now fsync before rename.
- Detail edit: updateCustomerPhoto updates only photo on current record. Full customer form uses original-record conflict protection. Address copies preserve photo per address.
- Persistence: load/save photoUri on main and extra addresses, order import reuses existing customers; street edits use copies. No routine deletion of stored gate files found in these paths.
- ZIP import/export: both portable and legacy formats validate nonblank photo sources, reject missing images; legacy import merges photos by address. Prior releases could create backups missing photo bytes; that damage cannot be undone without original bytes.
- Local rollback: stores references, not independent photo copies. Old URI restored by rollback must still resolve. New archive maps the exact entire source URI to its own stable file, without rewriting customer records.
- Cleanup: deletes sync JSON history and temporary ZIP directories; no blanket delete of customer_gate_photos found. Uninstall/clear-app-data and external provider removal remain outside this code's control.
- Rendering: sampled decode and EXIF orientation; previously all failures became a generic null. On-device audit now distinguishes permission exceptions, absent paths, zero bytes, unreadable image data and memory failures. FileNotFound from a provider is explicitly ambiguous rather than called proof of deletion.

## Implementation

At customer data load and before photo preview, readable legacy external references are copied to a deterministic SHA-256 URI-keyed file under files/customer_gate_photos. The source is streamed, fsynced, decoded and renamed before it becomes visible. Failed writes leave the source/reference unchanged; partial files are removed. Reads and backups resolve this preserved copy even if the provider later disappears or an old sync version restores the external URI. Different full URIs cannot accidentally select another customer's photo by basename. No guessing by name/phone and no coordinate edits.

Customer detail photo menu -> Kiểm tra ảnh đã lưu runs a read-only audit of the current customer and all referenced customer photos off the UI thread. Sao chép kết quả copies a report containing app version, customer IDs, source category and failure category; no phone, address, coordinates or full URI. It does not send anything automatically.

## Verification

JVM tests cover provider permission loss after preservation, interrupted copy/retry with no partial publication, empty/invalid source rejection, and different URIs sharing a basename. Existing resolver/sizing/customer-safety tests remain in CI. Android camera/provider/device behavior needs actual phone verification. The first required device evidence is the photo audit report for the affected customer; do not treat CI as proof of its recovered photo.

## Direct loader debug requested by Hải

Every null decode result and caught read exception/OOM is recorded at loadGatePhoto with the exact pipeline stage (source lookup, opening header, dimensions decode, opening pixels, pixel decode, orientation), exception class, version, time, source hash/type, local file existence/size when available and persisted URI grant flag. Logcat and bounded local preferences keep the last 40 distinct photo/stage/error combinations with repeat counts. The audit includes this history and maps hashes to customer IDs. A missing persisted grant is evidence only, not proof of missing temporary permission. No full URI or customer PII is logged.
