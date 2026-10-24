# Continue linked-file customer sync when photos cannot be read

Portable sync schema 3 writes every customer record and every readable photo. An unreadable image no longer aborts sync: it is marked explicitly in customers.json and listed by customer ID/name/address slot in manifest.missingPhotos and the saved UI status. The live app photo references are not cleared.

Fallback matches the same customer ID and SHA-256 source-reference identity in a previous backup, then decodes the previous image before reuse. It never substitutes an earlier replacement image merely because the customer or address matches. Pre-schema-3 archives have no source identity, so their images cannot be matched automatically with certainty.

The previous external file is copied before packing. Photos are staged on disk, one at a time, rather than keeping all compressed photos in RAM. The complete ZIP is staged before opening the external destination. A failed external write attempts to restore the completely copied previous bytes and reports whether that succeeded; provider IO/power failure cannot be promised atomic. Photo failures and external write failures are reported differently.

Import accepts explicitly marked missing-image entries in these partial backups, restores customer data and the original unresolved reference, and reports the count. Unmarked corrupt/missing archive entries still fail validation. Restore no longer immediately rewrites its source file. The UI distinguishes local history from the linked external file and reports missing photos after manual sync/file creation and on reopening the screen after scheduled sync.

Tests cover exact fallback identity, replacement-image rejection, unknown legacy identity and traversal paths. CI runs all unit tests and assembles the APK. Manual acceptance: one unreadable photo among readable ones must still produce a restorable ZIP with all customer records and a missing-photo warning. External provider read/write failures require device validation.
