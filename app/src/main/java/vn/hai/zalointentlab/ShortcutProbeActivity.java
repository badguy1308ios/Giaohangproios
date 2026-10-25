package vn.hai.zalointentlab;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public final class ShortcutProbeActivity extends Activity {
    private static final int REQUEST_CREATE_SHORTCUT = 17;
    private TextView result;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(18,45,37));
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setPadding(dp(20),dp(20),dp(20),dp(20));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,250,248));
        scroll.addView(root);
        setContentView(scroll);

        TextView heading=text("Thử lấy shortcut trực tiếp",23,true);
        root.addView(heading);
        TextView description=text("Android có API để ứng dụng hỏi app khác tạo shortcut. Bài thử này kiểm tra Zalo có cung cấp chức năng đó cho app ngoài hay không. Không cần ADB, không đổi ứng dụng mặc định, không can thiệp dữ liệu.",15,false);
        add(root,description,12);
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText("YÊU CẦU ZALO TẠO SHORTCUT");
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(15,119,81)));
        add(root,button,20);
        button.setOnClickListener(v->probe());
        result=text("Chưa kiểm tra.",14,false);
        add(root,result,18);
        TextView note=text("Nếu Zalo không hỗ trợ ACTION_CREATE_SHORTCUT, điều đó chỉ loại trừ hướng này; app vẫn có thể mở chat bằng các tham số đã lấy từ ADB.",13,false);
        add(root,note,16);
    }

    private void probe() {
        Intent request=new Intent(Intent.ACTION_CREATE_SHORTCUT);
        request.setPackage("com.zing.zalo");
        List<ResolveInfo> creators = getPackageManager().queryIntentActivities(request, PackageManager.MATCH_DEFAULT_ONLY);
        if(creators == null || creators.isEmpty()) {
            result.setText("KẾT QUẢ: Zalo không công bố Activity tạo shortcut theo ACTION_CREATE_SHORTCUT cho ứng dụng khác trên máy này. Chưa thể lấy Intent trực tiếp bằng cách này.");
            return;
        }
        result.setText("Tìm thấy " + creators.size() + " Activity hỗ trợ. Đang thử gọi Zalo...");
        try {
            startActivityForResult(request,REQUEST_CREATE_SHORTCUT);
        } catch (SecurityException e) {
            result.setText("Bị chặn quyền khi gọi Zalo: " + e.getMessage());
        } catch (Exception e) {
            result.setText("Không mở được trình chọn shortcut: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_CREATE_SHORTCUT) return;
        if(resultCode != RESULT_OK || data == null) {
            result.setText("Zalo không trả về shortcut (có thể thao tác đã bị hủy hoặc Zalo không hỗ trợ).");
            return;
        }
        Intent nested = null;
        try {
            nested = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        } catch(Exception ignored) {}
        String sig=getStringExtra(nested,"intent_sig");
        String target=getStringExtra(nested,"target");
        String owner=getStringExtra(nested,"owner");
        // Some apps return their navigation fields directly rather than in EXTRA_SHORTCUT_INTENT.
        if(sig.isEmpty()) sig=getStringExtra(data,"intent_sig");
        if(target.isEmpty()) target=getStringExtra(data,"target");
        if(owner.isEmpty()) owner=getStringExtra(data,"owner");
        if(!sig.isEmpty() && !target.isEmpty() && !owner.isEmpty()) {
            Intent outgoing=new Intent();
            outgoing.putExtra("zalo_config","intent_sig="+sig+"\ntarget="+target+"\nowner="+owner);
            setResult(RESULT_OK,outgoing);
            finish();
            return;
        }
        result.setText("Có phản hồi từ Zalo nhưng chưa nhận được đủ intent_sig, target, owner.\n"
            +"action="+data.getAction()+"\n"
            +"extra keys="+(data.getExtras()==null?"không có":data.getExtras().keySet().toString())+"\n"
            +"nested action="+(nested==null?"null":nested.getAction())+"\n"
            +"Hãy gửi ảnh kết quả này cho Mây (không chứa mã riêng).");
    }

    private String getStringExtra(Intent intent,String key) {
        if(intent==null) return "";
        Object obj = intent.hasExtra(key)?intent.getExtras().get(key):null;
        return obj instanceof String ? (String)obj : "";
    }
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String t,int size,boolean bold) {
        TextView tv=new TextView(this);
        tv.setText(t);
        tv.setTextSize(size);
        tv.setTextColor(Color.rgb(18,45,37));
        if(bold) tv.setTypeface(null,1);
        return tv;
    }
    private void add(LinearLayout root,android.view.View child,int mt) {
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin=dp(mt);
        root.addView(child,params);
    }
}
