package vn.hai.zalointentlab;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private EditText input;
    private TextView status;
    private final int dark = Color.rgb(18, 45, 37);
    private final int green = Color.rgb(15, 119, 81);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 249, 247));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = text("Zalo Chat Lab", 25, dark, true);
        root.addView(heading);
        TextView subtitle = text("Thử mở thẳng cuộc trò chuyện Zalo bằng quyền ứng dụng Android thông thường — không cần ADB.", 14, Color.DKGRAY, false);
        add(root, subtitle, 10);

        TextView label = text("DỮ LIỆU SHORTCUT", 13, dark, true);
        add(root, label, 25);
        input = new EditText(this);
        input.setTextSize(13);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("Dán ba tham số hoặc nguyên dòng Intent từ aShell You:\nintent_sig=...\ntarget=...\nowner=...");
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackgroundColor(Color.WHITE);
        input.setText(getPreferences(MODE_PRIVATE).getString("lastConfig", ""));
        root.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));

        Button paste = button("DÁN TỪ BỘ NHỚ TẠM", false);
        add(root, paste, 12);
        paste.setOnClickListener(v -> pasteClipboard());

        Button contactsProbe = button("KIỂM TRA ZALO TỪ DANH BẠ ANDROID", false);
        add(root, contactsProbe, 12);
        contactsProbe.setOnClickListener(v -> startActivity(new Intent(this, ContactsProbeActivity.class)));

        Button batch = button("NHẬP NHIỀU CHAT ZALO TỪ FILE", false);
        add(root, batch, 12);
        batch.setOnClickListener(v -> startActivity(new Intent(this, BatchImportActivity.class)));

        Button probe = button("THỬ LIÊN KẾT ZALO KHÔNG ADB (MỚI)", false);
        add(root, probe, 16);
        probe.setOnClickListener(v -> startActivityForResult(
            new Intent(this, ShortcutProbeActivity.class), 415
        ));

        Button open = button("MỞ CHAT ZALO", true);
        add(root, open, 10);
        open.setOnClickListener(v -> openZalo());

        status = text("Chưa thử. App không cần quyền gỡ lỗi, không gửi dữ liệu lên mạng.", 14, dark, false);
        add(root, status, 18);

        TextView warning = text("Lưu ý: Nếu Android báo SecurityException, Zalo không cho phép app thông thường mở Activity này. APK không can thiệp hay sửa dữ liệu Zalo/GiaoHangPro.", 12, Color.DKGRAY, false);
        add(root, warning, 24);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 415 && resultCode == RESULT_OK && data != null) {
            String config = data.getStringExtra("zalo_config");
            if (config != null && !config.isEmpty()) {
                input.setText(config);
                status.setText("Đã nhận dữ liệu shortcut tự động! Bấm MỞ CHAT ZALO để kiểm chứng.");
            }
        }
    }

    private void pasteClipboard() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cb == null ? null : cb.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            status.setText("Bộ nhớ tạm đang trống.");
            return;
        }
        CharSequence txt = clip.getItemAt(0).coerceToText(this);
        input.setText(txt == null ? "" : txt.toString());
        status.setText("Đã dán. Bấm MỞ CHAT ZALO để kiểm chứng.");
    }

    private String extract(String raw, String key) {
        Pattern p = Pattern.compile("\\b" + key + "=([^,}\\]\\s]+)");
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1) : "";
    }

    private void openZalo() {
        String raw = input.getText().toString();
        String sig = extract(raw, "intent_sig");
        String target = extract(raw, "target");
        String owner = extract(raw, "owner");
        if (sig.isEmpty() || target.isEmpty() || owner.isEmpty()) {
            status.setText("Thiếu dữ liệu: cần đủ intent_sig, target, owner. Có thể dán nguyên dòng 'intents=[Intent ... /PersistableBundle[...]]' từ aShell You.");
            return;
        }

        Intent intent = new Intent("com.zing.zalo.action.SHORTCUT_MINI_CHAT");
        intent.setComponent(new ComponentName("com.zing.zalo", "com.zing.zalo.ui.IntentHandlerActivityAlias"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("intent_sig", sig);
        intent.putExtra("target", target);
        intent.putExtra("owner", owner);
        try {
            startActivity(intent);
            getPreferences(MODE_PRIVATE).edit().putString("lastConfig", raw).apply();
            status.setText("Đã gọi startActivity() không gặp ngoại lệ. Hãy kiểm tra Zalo có mở đúng khung chat hay không.");
        } catch (SecurityException ex) {
            status.setText("BỊ CHẶN QUYỀN (SecurityException): " + ex.getMessage());
        } catch (ActivityNotFoundException ex) {
            status.setText("KHÔNG TÌM THẤY ACTIVITY: " + ex.getMessage());
        } catch (Exception ex) {
            status.setText("LỖI " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private TextView text(String value, int sp, int color, boolean strong) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (strong) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : green);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? green : Color.rgb(225, 241, 232)));
        return b;
    }

    private void add(LinearLayout parent, View view, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
