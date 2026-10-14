# Customer photo storage repair

Confirmed code defects: image references were copied into portable backups even when image bytes could not be read; restore silently emptied missing image references. Legacy absolute paths did not resolve against restored app files. Camera references were stored without validating the captured image.

This change uses one sampled, EXIF-aware reader for display and backup; resolves exact legacy app-owned paths; stores new images under stable relative references after decoding and atomic rename; preserves the previous photo on capture/import failure. Camera pending URI survives activity recreation. Both backup formats validate photos before opening the destination; restore rejects missing/corrupt images before replacing customer records. Legacy export stages photos on disk to limit memory use.

Seven filesystem regression tests cover relative paths, restored roots, provider paths, empty/missing files, traversal, and import files. CI also runs existing unit tests and assembles the APK. Device acceptance: reopen an affected customer, capture/select a photo, restart the app, export/restore with photos, and check a corrupt/missing-photo archive does not replace customer records.

Limits: no affected device database or original photo was available. A screenshot alone cannot establish its exact failure cause. Exact-path recovery works only when the original bytes remain. Deleted files or inaccessible gallery-only references need a backup/source photo. No customer coordinate changes are included.
