package vn.hai.zalointentlab;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ContactsProbeActivity extends Activity {
    private static final int CHOOSE_CONTACT = 543;
    private static final int PERMISSION_CONTACTS = 544;
    private static final int COLOR = Color.rgb(18,45,37);
    private static final int GREEN = Color.rgb(15,119,81);
    private Uri selectedContact;
    private TextView report;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(COLOR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(22),dp(18),dp(20));
        root.setBackgroundColor(Color.rgb(246,249,247));
        scroll.addView(root);
        setContentView(scroll);

        add(root,text("Kiểm tra liên kết Zalo từ Danh bạ",22,true),0);
        add(root,text(
          "Chọn một liên hệ giống video. App chỉ đọc dữ liệu của liên hệ đã chọn để kiểm tra có loại dữ liệu Zalo riêng hay chỉ là số điện thoại. App không sửa danh bạ, không kết nối mạng.",
          14,false),14);
        Button choose = button("CHỌN LIÊN HỆ ĐỂ KIỂM TRA");
        add(root,choose,16);
        choose.setOnClickListener(v->{
            try {
                Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                startActivityForResult(pick,CHOOSE_CONTACT);
            } catch(Exception e) {
                report.setText("Không mở được Danh bạ: "+e.getClass().getSimpleName()+". "+e.getMessage());
            }
        });
        Button view = button("MỞ LẠI DANH BẠ ĐỂ THỬ NÚT ZALO");
        add(root,view,8);
        view.setOnClickListener(v->{
            if(selectedContact == null) {
                report.setText("Hãy chọn một liên hệ trước.");
                return;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,selectedContact));
            } catch(Exception e) {
                report.setText("Không mở được trang Danh bạ: "+e.getMessage());
            }
        });
        add(root,text("KẾT QUẢ ĐỌC DỮ LIỆU (CHỈ HIỂN THỊ TRÊN MÁY)",14,true),24);
        report=text("Chưa chọn liên hệ.",14,false);
        report.setTextIsSelectable(true);
        add(root,report,10);
        add(root,text("Giới hạn: Dù tìm thấy dữ liệu Zalo riêng, điều đó chưa chứng minh rằng GiaoHangPro có thể mở chat không cần số điện thoại. Nếu không tìm thấy dữ liệu riêng, Danh bạ có thể dùng số điện thoại để tạo Intent.",12,false),20);
    }

    @Override protected void onActivityResult(int code,int resultCode,Intent data) {
        super.onActivityResult(code,resultCode,data);
        if(code!=CHOOSE_CONTACT || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        selectedContact=data.getData();
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) {
            report.setText("Đang yêu cầu quyền đọc Danh bạ để chỉ kiểm tra liên hệ được chọn...");
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},PERMISSION_CONTACTS);
            return;
        }
        inspectContact();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request!=PERMISSION_CONTACTS) return;
        if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) inspectContact();
        else report.setText("Chưa có quyền đọc chi tiết liên hệ. App không đọc dữ liệu và không sửa gì.");
    }
    private void inspectContact() {
        if(selectedContact == null) return;
        try {
            ContentResolver resolver=getContentResolver();
            String contactId=null, name="Liên hệ được chọn";
            try(Cursor c=resolver.query(selectedContact,new String[]{
                ContactsContract.Contacts._ID,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},null,null,null)) {
                if(c!=null && c.moveToFirst()) {
                    contactId=c.getString(0);
                    name=c.getString(1);
                }
            }
            if(contactId==null){
                report.setText("Không đọc được mã liên hệ từ URI hệ thống.");
                return;
            }
            int phoneRows=0, zaloRows=0, otherCustom=0;
            Set<String> kinds=new HashSet<>();
            StringBuilder matches=new StringBuilder();
            String selection=ContactsContract.Data.CONTACT_ID+"=?";
            String[] projection={
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3
            };
            try(Cursor c=resolver.query(ContactsContract.Data.CONTENT_URI,projection,
                selection,new String[]{contactId},null)) {
                if(c!=null) while(c.moveToNext()) {
                    String mime=c.getString(0);
                    if(mime==null) continue;
                    String m=mime.toLowerCase(Locale.ROOT);
                    kinds.add(mime);
                    if(m.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) phoneRows++;
                    if(m.contains("zalo")||m.contains("zing")) {
                        zaloRows++;
                        matches.append("• Loại Zalo: ").append(mime).append("\n");
                        for(int i=1;i<=3;i++){
                            String value=c.getString(i);
                            if(value!=null && !value.isEmpty()) {
                                // Values can contain private IDs/phone numbers: show presence, not content.
                                matches.append("  DATA").append(i).append(": có dữ liệu (")
                                    .append(value.length()).append(" ký tự)\n");
                            }
                        }
                    } else if(!m.startsWith("vnd.android.cursor.item/")) otherCustom++;
                }
            }
            StringBuilder msg=new StringBuilder();
            msg.append("Liên hệ: ").append(name==null?"(không tên)":name).append("\n");
            msg.append("Số dòng điện thoại: ").append(phoneRows).append("\n");
            msg.append("Số loại dữ liệu: ").append(kinds.size()).append("\n");
            msg.append("Dòng có tên Zalo/Zing: ").append(zaloRows).append("\n\n");
            if(zaloRows>0) {
                msg.append("ĐÃ PHÁT HIỆN DỮ LIỆU LIÊN QUAN ZALO:\n").append(matches);
                msg.append("\nTiếp theo cần xem dữ liệu đó là số điện thoại hay định danh riêng.");
            } else {
                msg.append("KHÔNG THẤY DÒNG MIMETYPE ZALO/ZING.\n");
                msg.append("Trên thiết bị này, Danh bạ có thể dựng nút Zalo từ số điện thoại hoặc cách tích hợp khác; chưa chứng minh có ID chat độc lập.");
            }
            msg.append("\n\nApp không in số điện thoại hay ID riêng vào báo cáo này.");
            report.setText(msg.toString());
        } catch(SecurityException e) {
            report.setText("Thiếu quyền xem dữ liệu: "+e.getMessage());
        } catch(Exception e) {
            report.setText("Không kiểm tra được: "+e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }

    private TextView text(String value,int size,boolean bold) {
        TextView v=new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(COLOR);
        if(bold) v.setTypeface(null,1);
        return v;
    }
    private Button button(String value) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        return b;
    }
    private void add(LinearLayout root,View child,int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=dp(margin);
        root.addView(child,p);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
