# Zalo Chat Lab v5

Independent APK `vn.hai.zalointentlab`, built from `zalo-chat-lab` branch only. The main GiaoHangPro branch is unchanged.

## New manual linking test
1. Open **THỬ LIÊN KẾT ZALO VỚI KHÁCH HÀNG** in Zalo Chat Lab.
2. Enter a sample customer's name. Delivery phone number is optional.
3. Paste the three fields extracted from an existing Zalo chat shortcut (`intent_sig`, `target`, `owner`). NEVER commit these private fields to GitHub.
4. Tap **LƯU LIÊN KẾT CHO KHÁCH**. It stores only inside this APK (Android private preferences, backup disabled).
5. Tap **MỞ CHAT ZALO** for the customer. Editing and deleting do not affect Zalo or GiaoHangPro.

This tests **manually attaching an already acquired Zalo Intent to a customer record**, and reopening the right conversation by tapping one button. It does **not** autonomously discover Zalo conversations: Zalo has not exposed an API permitting an ordinary app to read those identifiers. When obtaining the three fields is necessary, the previously confirmed aShell / ADB route or bulk importer remains available.

The app uses no network permission, and the source contains no customer codes. No customer records or Zalo messages are read from GiaoHangPro. If it is eventually integrated, backups should store these fields and treat them as opaque sensitive credentials, subject to the Zalo account and version changing.
