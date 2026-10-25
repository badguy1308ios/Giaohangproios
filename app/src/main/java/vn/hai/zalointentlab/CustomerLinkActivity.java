package vn.hai.zalointentlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Experimental GiaoHangPro-style customer/Zalo linking screen. Offline only.
 * The Zalo Intent remains opaque. Telephone number is NOT used to open the chat.
 */
public final class CustomerLinkActivity extends Activity {
    private static final String PREFERENCES = "zalo_link_lab_v5";
    private static final String DATA_KEY = "customers";
    private final int forest = Color.rgb(16, 55, 45);
    private final int green = Color.rgb(12, 122, 79);
    private EditText name, phone, config;
    private TextView message, count;
    private LinearLayout list;
    private String editingId = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(forest);
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(32));
        root.setBackgroundColor(Color.rgb(246, 249, 247));
        scroll.addView(root);
        setContentView(scroll);

        add(root, text("Liên kết Zalo khách hàng", 23, true), 0);
        add(root, text(
            "Mini app độc lập để thử quy trình GiaoHangPro. Tên khách và SĐT giao hàng là thông tin riêng; bấm Zalo sẽ dùng mã chat đã lưu, không tra theo SĐT.",
            14, false), 12);

        add(root, text("Tên khách hàng", 14, true), 18);
        name = field("Ví dụ: Nguyễn Mười", false);
        add(root, name, 6);

        add(root, text("SĐT giao hàng (không bắt buộc)", 14, true), 13);
        phone = field("Có thể bỏ trống hoặc khác số đăng ký Zalo", false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        add(root, phone, 6);

        add(root, text("Mã Zalo từ shortcut đã có", 14, true), 15);
        config = field("Dán 3 dòng intent_sig=..., target=..., owner=...\nHoặc nguyên dòng Intent từ aShell You", true);
        add(root, config, 6);

        Button paste = button("DÁN MÃ ZALO", false);
        add(root, paste, 8);
        paste.setOnClickListener(v -> pasteConfig());

        Button save = button("LƯU LIÊN KẾT CHO KHÁCH", true);
        add(root, save, 9);
        save.setOnClickListener(v -> saveCustomer());

        Button reset = button("TẠO KHÁCH MỚI / BỎ CHỈNH SỬA", false);
        add(root, reset, 8);
        reset.setOnClickListener(v -> clearForm());

        message = text(
            "Bước thử: điền tên khách → dán 3 tham số Intent → Lưu → bấm Mở Zalo trong danh sách. Dữ liệu chỉ nằm trong app thử trên máy.",
            13, false);
        add(root, message, 13);

        add(root, text("DANH SÁCH KHÁCH ĐÃ LIÊN KẾT", 15, true), 23);
        count = text("",13,false);
        add(root, count, 6);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        add(root, list, 8);
        render();
    }

    private void pasteConfig() {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if(clip == null || clip.getItemCount() == 0) {
            message.setText("Bộ nhớ tạm không có dữ liệu. Hãy copy 3 dòng intent_sig/target/owner trước.");
            return;
        }
        CharSequence value = clip.getItemAt(0).coerceToText(this);
        config.setText(value == null ? "" : value.toString());
        message.setText("Đã dán mã Zalo. Bấm LƯU LIÊN KẾT CHO KHÁCH.");
    }

    private String fieldFrom(String raw, String key) {
        Matcher matcher = Pattern.compile("\\b" + key + "=([^,}\\]\\s]+)").matcher(raw);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void saveCustomer() {
        String display = name.getText().toString().trim();
        String orderPhone = phone.getText().toString().trim();
        String raw = config.getText().toString();
        String signature = fieldFrom(raw,"intent_sig");
        String target = fieldFrom(raw,"target");
        String owner = fieldFrom(raw,"owner");

        if(display.isEmpty()) {
            message.setText("Hãy nhập tên khách hàng.");
            return;
        }
        if(signature.isEmpty() || target.isEmpty() || owner.isEmpty()) {
            message.setText("Thiếu một trong ba trường: intent_sig, target, owner. Không cần nhập lệnh dumpsys, chỉ cần mã Intent.");
            return;
        }

        try {
            JSONArray current = load();
            String id = editingId.isEmpty() ? UUID.randomUUID().toString() : editingId;
            JSONObject record = new JSONObject();
            record.put("id", id);
            record.put("name", display);
            record.put("phone", orderPhone);
            record.put("intent_sig", signature);
            record.put("target", target);
            record.put("owner", owner);

            // Reject accidental duplicate Zalo identity to avoid mislinking customers.
            for(int i=0;i<current.length();i++) {
                JSONObject c = current.optJSONObject(i);
                if(c == null || id.equals(c.optString("id"))) continue;
                if(target.equals(c.optString("target")) && owner.equals(c.optString("owner"))) {
                    message.setText("Chat Zalo này đã được liên kết với khách: " + c.optString("name") +
                        ". Hãy sửa liên kết cũ hoặc sử dụng chat khác.");
                    return;
                }
            }
            boolean replaced=false;
            for(int i=0;i<current.length();i++) {
                JSONObject c=current.optJSONObject(i);
                if(c != null && id.equals(c.optString("id"))) {
                    current.put(i,record);
                    replaced=true;
                    break;
                }
            }
            if(!replaced) current.put(record);
            getSharedPreferences(PREFERENCES,MODE_PRIVATE).edit()
                .putString(DATA_KEY,current.toString()).apply();
            clearForm();
            message.setText("Đã lưu liên kết cho " + display + ". Bấm MỞ CHAT ZALO trong danh sách để thử.");
            render();
        } catch(Exception e) {
            message.setText("Lưu thất bại: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private JSONArray load() {
        try {
            String raw = getSharedPreferences(PREFERENCES,MODE_PRIVATE)
                    .getString(DATA_KEY,"[]");
            return new JSONArray(raw);
        } catch(Exception e) {
            return new JSONArray();
        }
    }

    private void render() {
        list.removeAllViews();
        JSONArray rows=load();
        count.setText(rows.length() + " khách hàng đã liên kết (dữ liệu thử nghiệm, lưu nội bộ)");
        if(rows.length()==0) {
            add(list,text("Chưa có khách nào. Thêm một khách thử bên trên.",14,false),8);
            return;
        }
        for(int i=0;i<rows.length();i++) {
            JSONObject rec=rows.optJSONObject(i);
            if(rec==null) continue;
            String display=rec.optString("name","Khách hàng");
            String p=rec.optString("phone","");
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(12),dp(12),dp(14));
            card.setBackgroundColor(Color.WHITE);
            add(list,card,12);

            add(card,text(display,17,true),0);
            add(card,text(p.isEmpty() ?
                "SĐT giao hàng: chưa nhập; vẫn liên kết Zalo" :
                "SĐT giao hàng: "+p+" (không dùng để mở Zalo)",12,false),4);
            Button open=button("MỞ CHAT ZALO",true);
            add(card,open,9);
            open.setOnClickListener(v->openChat(rec));
            Button edit=button("SỬA LIÊN KẾT",false);
            add(card,edit,7);
            edit.setOnClickListener(v->{
                editingId=rec.optString("id");
                name.setText(rec.optString("name"));
                phone.setText(rec.optString("phone"));
                config.setText(
                    "intent_sig="+rec.optString("intent_sig")+"\n"+
                    "target="+rec.optString("target")+"\n"+
                    "owner="+rec.optString("owner"));
                message.setText("Đang sửa liên kết của "+display+". Sửa xong bấm LƯU. Cuộn lên đầu trang để xem biểu mẫu.");
            });
            Button delete=button("XÓA LIÊN KẾT",false);
            add(card,delete,7);
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Xóa liên kết?")
                .setMessage("Chỉ xóa dữ liệu thử nghiệm của "+display+
                    " trong Zalo Chat Lab. Không xóa khách trong GiaoHangPro hay cuộc trò chuyện Zalo.")
                .setNegativeButton("Hủy",null)
                .setPositiveButton("Xóa",(d,w)->deleteRecord(rec.optString("id"))).show());
        }
    }

    private void deleteRecord(String id) {
        try {
            JSONArray before=load(),after=new JSONArray();
            for(int i=0;i<before.length();i++) {
                JSONObject c=before.optJSONObject(i);
                if(c!=null && !id.equals(c.optString("id"))) after.put(c);
            }
            getSharedPreferences(PREFERENCES,MODE_PRIVATE).edit()
                .putString(DATA_KEY,after.toString()).apply();
            if(id.equals(editingId)) clearForm();
            message.setText("Đã xóa liên kết khỏi mini app. Zalo và GiaoHangPro không thay đổi.");
            render();
        } catch(Exception ex) {
            message.setText("Xóa thất bại: "+ex.getMessage());
        }
    }

    private void clearForm() {
        editingId="";
        name.setText("");
        phone.setText("");
        config.setText("");
    }

    private void openChat(JSONObject rec) {
        Intent intent=new Intent("com.zing.zalo.action.SHORTCUT_MINI_CHAT");
        intent.setComponent(new ComponentName("com.zing.zalo","com.zing.zalo.ui.IntentHandlerActivityAlias"));
        intent.setFlags(0x14000000);
        intent.putExtra("intent_sig",rec.optString("intent_sig"));
        intent.putExtra("target",rec.optString("target"));
        intent.putExtra("owner",rec.optString("owner"));
        try {
            startActivity(intent);
            message.setText("Đã gửi Intent. Kiểm tra Zalo có mở đúng chat của "+rec.optString("name")+" hay không.");
        } catch(SecurityException e) {
            message.setText("Zalo từ chối quyền mở chat: "+e.getMessage());
        } catch(ActivityNotFoundException e) {
            message.setText("Không tìm thấy ứng dụng/Activity Zalo: "+e.getMessage());
        } catch(Exception e) {
            message.setText("Không mở được chat: "+e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }

    private EditText field(String hint,boolean multiline) {
        EditText input=new EditText(this);
        input.setTextSize(14);
        input.setHint(hint);
        input.setPadding(dp(12),dp(10),dp(12),dp(10));
        input.setBackgroundColor(Color.WHITE);
        if(multiline) {
            input.setMinLines(4);
            input.setGravity(Gravity.TOP|Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else input.setSingleLine(true);
        return input;
    }

    private Button button(String caption, boolean primary) {
        Button b=new Button(this);
        b.setText(caption);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(primary?Color.WHITE:green);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            primary?green:Color.rgb(224,240,232)));
        return b;
    }

    private TextView text(String value,int size,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(forest);
        if(bold) t.setTypeface(null,1);
        return t;
    }

    private void add(LinearLayout container, View child, int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=dp(margin);
        container.addView(child,p);
    }

    private int dp(int px) {
        return Math.round(px*getResources().getDisplayMetrics().density);
    }
}
