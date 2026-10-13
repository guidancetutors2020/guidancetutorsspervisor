package com.guidancetutors.priceswidget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setBackgroundColor(Color.parseColor("#121822"));
        t.setTextColor(Color.parseColor("#F2EEE4"));
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setPadding(48, 48, 48, 48);
        t.setLineSpacing(10, 1);
        t.setText("تمام، التطبيق اتثبت ✓\n\nعشان تحط الويدجت:\nاضغط ضغطة طويلة على مكان فاضي في الشاشة الرئيسية ← الأدوات (Widgets) ← دوّر على «الدولار والدهب» واسحبه للشاشة.\n\nالأسعار بتتحدث لما تدوس على زرار «تحديث» أو على أي رقم. الساعات بتمشي لوحدها.");
        setContentView(t);
    }
}
