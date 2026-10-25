package vn.hai.zalointentlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.json.JSONArray;
import org.json.JSONObject;

public final class BatchImportActivity extends Activity {
    private static final int PICK_FILE = 602;
    private static final String PREF = "zalo_batch_contacts";
    private static final String STORAGE_KEY = "contacts";
    private static final String CREATE_SHORTCUT_FILTER = "SHORTCUT_MINI_CHAT";
    private LinearLayout root;
    private LinearLayout entries;
    private TextView summary;
    private final int dark = Color.rgb(18,45,37);
    private final int green = Color.rgb(15,119,81);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(246,249,247));
        scroll.addView(root);
        setContentView(scroll);

        add(root, text("Danh sách chat Zalo", 24, true), 0);
        add(root, text(
            "Dùng aShell You (WiFi ADB) xuất shortcut một lần. App tự tìm tên cuộc trò chuyện và các mã intent_sig / target / owner, lưu riêng trên điện thoại và mở chat lại khi cần.",
            14,false),10);

        add(root, text("LỆNH XUẤT DÙNG TRONG aShell You", 13, true), 18);
        final String command = "dumpsys shortcut com.zing.zalo | grep -E 'ShortcutInfo \\{id=|shortLabel=|intents='";
        TextView commandText=text(command,13,false);
        commandText.setTextIsSelectable(true);
        commandText.setPadding(dp(12),dp(10),dp(12),dp(10));
        commandText.setBackgroundColor(Color.WHITE);
        add(root,commandText,8);
        Button copy=button("SAO CHÉP LỆNH aShell",false);
        add(root,copy,8);
        copy.setOnClickListener(v->{
            ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("ADB command",command));
            summary.setText("Đã sao chép lệnh. Dùng aShell You ở chế độ WiFi ADB để chạy, sau đó bấm Save tạo file .txt.");
        });
        Button file=button("NHẬP TỪ FILE TXT",true);
        add(root,file,12);
        file.setOnClickListener(v->pickFile());
        Button paste=button("NHẬP OUTPUT TỪ BỘ NHỚ TẠM",false);
        add(root,paste,8);
        paste.setOnClickListener(v->paste());
        summary=text("",14,false);
        add(root,summary,12);

        Button clear=button("XÓA DANH SÁCH THỬ NGHIỆM",false);
        add(root,clear,18);
        clear.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Xóa danh sách đã nhập?")
            .setMessage("Chỉ xóa dữ liệu trong Zalo Chat Lab; không xóa bất kỳ cuộc trò chuyện nào trên Zalo.")
            .setNegativeButton("Hủy",null)
            .setPositiveButton("Xóa",(d,w)->{
                getSharedPreferences(PREF,MODE_PRIVATE).edit().remove(STORAGE_KEY).apply();
                summary.setText("Đã xóa danh sách trong app thử nghiệm.");
                render();
            }).show());
        add(root,text("CUỘC TRÒ CHUYỆN ĐÃ LƯU",15,true),18);
        entries=new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        add(root,entries,10);
        render();
    }

    private void pickFile() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/*","application/octet-stream"});
        try {
            startActivityForResult(intent,PICK_FILE);
        } catch(Exception ex) {
            summary.setText("Không mở được trình chọn file: "+ex.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_FILE || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        try(InputStream stream=getContentResolver().openInputStream(data.getData());
            BufferedReader reader=new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb=new StringBuilder();
            char[] buffer=new char[8192];
            int count;
            while((count=reader.read(buffer))!=-1) {
                if(sb.length()+count>2_000_000) {
                    summary.setText("File vượt quá giới hạn 2 MB. Hãy dùng lệnh lọc ngắn gọn hiển thị trong app.");
                    return;
                }
                sb.append(buffer,0,count);
            }
            importData(sb.toString());
        } catch(Exception ex) {
            summary.setText("Không đọc được file: "+ex.getClass().getSimpleName()+" "+ex.getMessage());
        }
    }

    private void paste() {
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip=cm==null?null:cm.getPrimaryClip();
        if(clip==null || clip.getItemCount()==0) {
            summary.setText("Bộ nhớ tạm đang trống. Hãy chép Output aShell hoặc chọn file TXT.");
            return;
        }
        CharSequence text=clip.getItemAt(0).coerceToText(this);
        importData(text==null?"":text.toString());
    }

    private static class Entry {
        String id="";
        String label="";
        String sig="";
        String target="";
        String owner="";
        boolean isChat=false;
        boolean valid() {
            return isChat && !sig.isEmpty() && !target.isEmpty() && !owner.isEmpty();
        }
        JSONObject toJson() throws Exception {
            JSONObject o=new JSONObject();
            o.put("id",id);
            o.put("label",label);
            o.put("intent_sig",sig);
            o.put("target",target);
            o.put("owner",owner);
            return o;
        }
    }

    private String field(String str,String key) {
        Matcher m=Pattern.compile("\\b"+key+"=([^,}\\]\\s]+)").matcher(str);
        return m.find()?m.group(1):"";
    }

    private void importData(String text) {
        if(text.trim().isEmpty()) {
            summary.setText("Không có dữ liệu để nhập.");
            return;
        }
        JSONArray existing=load();
        int totalFound=0, added=0, changed=0;
        Entry current=null;
        try {
            for(String line:text.split("\\r?\\n")) {
                if(line.contains("ShortcutInfo {id=")) {
                    if(current!=null && current.valid()) {
                        totalFound++;
                        int action=upsert(existing,current);
                        if(action==1) added++;
                        else if(action==2) changed++;
                    }
                    current=new Entry();
                    current.id=field(line,"id");
                }
                if(current==null) continue;
                int ix=line.indexOf("shortLabel=");
                if(ix>=0) {
                    String value=line.substring(ix+"shortLabel=".length());
                    int end=value.indexOf(", resId=");
                    if(end>=0) value=value.substring(0,end);
                    current.label=value.trim();
                }
                if(line.contains("intents=") && line.contains(CREATE_SHORTCUT_FILTER)) {
                    current.isChat=true;
                    current.sig=field(line,"intent_sig");
                    current.target=field(line,"target");
                    current.owner=field(line,"owner");
                }
            }
            if(current!=null && current.valid()) {
                totalFound++;
                int action=upsert(existing,current);
                if(action==1) added++;
                else if(action==2) changed++;
            }
            getSharedPreferences(PREF,MODE_PRIVATE).edit().putString(STORAGE_KEY,existing.toString()).apply();
            summary.setText("Đã nhận diện "+totalFound+" shortcut chat. Thêm "+added+", cập nhật "+changed+
                ". Tổng lưu "+existing.length()+".\n"+
                (totalFound==0?"Không tìm thấy dòng ShortcutInfo cùng SHORTCUT_MINI_CHAT và đủ ba tham số. Hãy xuất lại bằng lệnh phía trên.":"Bấm tên bên dưới để kiểm tra mở chat."));
            render();
        } catch(Exception ex) {
            summary.setText("Không xử lý được nội dung: "+ex.getClass().getSimpleName()+" "+ex.getMessage());
        }
    }

    // Matches existing contacts by account owner and encrypted target. No private IDs leave this app.
    private int upsert(JSONArray all,Entry entry) throws Exception {
        for(int i=0;i<all.length();i++) {
            JSONObject old=all.getJSONObject(i);
            if(old.optString("target").equals(entry.target) &&
               old.optString("owner").equals(entry.owner)) {
                JSONObject next=entry.toJson();
                if(old.toString().equals(next.toString())) return 0;
                all.put(i,next);
                return 2;
            }
        }
        all.put(entry.toJson());
        return 1;
    }

    private JSONArray load() {
        try {
            return new JSONArray(getSharedPreferences(PREF,MODE_PRIVATE).getString(STORAGE_KEY,"[]"));
        } catch(Exception ignored) {
            return new JSONArray();
        }
    }

    private void render() {
        entries.removeAllViews();
        JSONArray all=load();
        if(all.length()==0) {
            add(entries,text("Chưa có chat nào được nhập.",14,false),8);
            return;
        }
        for(int i=0;i<all.length();i++) {
            JSONObject item=all.optJSONObject(i);
            if(item==null) continue;
            String label=item.optString("label","").trim();
            if(label.isEmpty()) label="Chat không có tên ("+(i+1)+")";
            final String display=label;
            Button b=button("MỞ ZALO: "+display,true);
            add(entries,b,9);
            b.setOnClickListener(v->openChat(item,display));
        }
    }

    private void openChat(JSONObject item,String display) {
        Intent intent=new Intent("com.zing.zalo.action.SHORTCUT_MINI_CHAT");
        intent.setComponent(new ComponentName("com.zing.zalo","com.zing.zalo.ui.IntentHandlerActivityAlias"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("intent_sig",item.optString("intent_sig"));
        intent.putExtra("target",item.optString("target"));
        intent.putExtra("owner",item.optString("owner"));
        try {
            startActivity(intent);
            summary.setText("Đã yêu cầu mở: "+display+". Hãy kiểm tra Zalo hiển thị đúng người.");
        } catch(SecurityException e) {
            summary.setText("Zalo chặn quyền mở chat: "+e.getMessage());
        } catch(Exception e) {
            summary.setText("Không mở được chat: "+e.getClass().getSimpleName()+" "+e.getMessage());
        }
    }

    private Button button(String title,boolean primary) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(14);
        b.setTextColor(primary?Color.WHITE:green);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            primary?green:Color.rgb(224,240,232)));
        return b;
    }
    private TextView text(String text,int size,boolean bold) {
        TextView t=new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(dark);
        if(bold)t.setTypeface(null,1);
        return t;
    }
    private int dp(int n) {
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
    private void add(LinearLayout parent,View child,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=dp(top);
        parent.addView(child,p);
    }
}
