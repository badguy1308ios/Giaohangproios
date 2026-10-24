# Delete replaced gate photos

Hải explicitly requested that replacing a gate photo permanently delete the previous app-stored photo without retaining it for other customers or sync history. The new policy does not run a global reference scan or preserve old image bytes.

Detail updates commit the new reference before deleting old app-owned bytes. Form edits delete removed/replaced photo references after a successful Save; Cancel and conflict/write failure preserve the saved customer's image. Intermediate draft replacements are also removed. Moving an address/photo within the same edited record is not a replacement. Camera captures now remove the initial files-root capture after the validated internal copy is created. Gallery originals outside app storage are not deleted.

Deletion is restricted to canonical app-owned gate-photo paths. External legacy URI archives are removed by exact URI hash. A zero-byte retirement marker prevents a concurrent/background legacy migration from recreating deliberately replaced image bytes; this marker contains no photo. Failures are recorded in GatePhotoDebug at DELETE_REPLACED_PHOTO. No historical orphan sweep is performed.

Local sync snapshots referencing the deleted image cannot restore its bytes. Already-created external ZIP backups are not rewritten by this operation. Photo loads/coordinates are otherwise unchanged. Tests cover path confinement, deleting the archived bytes and blocking later remigration; CI runs all unit tests and assembles the APK.
