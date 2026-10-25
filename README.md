# Zalo Chat Lab

This is an **independent Android test application**, application ID `vn.hai.zalointentlab`.
It is intentionally isolated from GiaoHangPro source. Do not merge this branch into `main`.

## Purpose
Determine whether a normal third-party Android app can open the Zalo
`com.zing.zalo.action.SHORTCUT_MINI_CHAT` Intent that was proven to work via ADB.
ADB success does **not** guarantee that an ordinary app is authorized.

## Usage
1. Build via the GitHub Actions `Zalo Chat Lab APK` workflow and download its APK artifact.
2. Install APK separately from GiaoHangPro.
3. Copy only the private `intents=[Intent .../PersistableBundle[...]]` line
   from aShell You shortcut output, containing `intent_sig`, `target`, `owner`.
4. In the app tap **DÁN TỪ BỘ NHỚ TẠM**, then **MỞ CHAT ZALO**.
5. Record whether it opens the right chat, opens Zalo only, or reports a security error.

**Privacy**: Never commit the extracted Zalo IDs to a public repository.
This test app is fully offline, has no network permission, and stores the last
successful test input only in its private on-device preferences; no Android backup.

**Isolation**: This branch replaces its own tree with just this small project.
The repository's `main` branch is untouched. Create a new private repository
later if a physically separate repository is desired.
