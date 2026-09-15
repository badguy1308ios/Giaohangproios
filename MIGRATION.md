# GiaoHangPro repository migration

Source: badguy1308/Giaohangpro
Destination: quanlylamviectructuyen-sketch/Giaohangpro2 (private)
Original main: 4800b653714095e0c3cf80cb5dc1bcc1ccd07cdf

All 464 original commits and 6 source branches were imported without rewriting Git objects. The original main is preserved at backup-before-migration-v1.0.276. Existing backup branches retain their exact commit IDs. The old repository is retained.

The app source, application ID (com.example.giaohangpro), and persistent signing keystore are unchanged. The keystore blob remains be5744cb202cc61664849f0e86e9dc7654fd06df.

version-code-offset.txt is the single source for the new repository's build number offset. Android workflow run 1 builds versionCode 277 / versionName 1.0.277. Both Gradle and APK naming read this file. Do not reset it or independently change the Gradle/APK version logic.

APK artifacts are retained for 7 days. Cleanup resolves this repository's Android workflow ID dynamically and defaults to preview only. Old GitHub Actions run logs and APK artifacts remain in the old repository; Git history migration does not transfer those resources. GitHub secrets and repository settings are not copied automatically. The current Android build uses its existing tracked signing key and requires no new signing secrets.

Phone customer/order backups remain on the device; installing a newer APK with the same package and signing key permits an upgrade without uninstalling. Export a customer backup in the app before changing devices or uninstalling.

The migration/import-20260915 branch holds the verified transfer bundle and import job record. It is separate from application main.
