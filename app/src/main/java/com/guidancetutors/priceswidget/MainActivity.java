package com.guidancetutors.priceswidget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    TextView report;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(56, 120, 56, 56);
        box.setBackgroundColor(Color.parseColor("#0B1730"));
        box.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_RTL);

        TextView t = new TextView(this);
        t.setTextColor(Color.parseColor("#EEF3FF"));
        t.setTextSize(17);
        t.setLineSpacing(12, 1);
        t.setText("عشان تحط الويدجت: اضغط ضغطة طويلة على مكان فاضي في الشاشة الرئيسية ← الأدوات ← «الدولار والدهب».\n\nالأسعار بتتحدث لما تدوس «تحديث الأسعار» أو على أي رقم.");
        box.addView(t);

        Button btn = new Button(this);
        btn.setText("جرّب التحديث دلوقتي");
        btn.setOnClickListener(v -> test());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = 48;
        box.addView(btn, lp);

        report = new TextView(this);
        report.setTextColor(Color.parseColor("#9DAED1"));
        report.setTextSize(13);
        report.setGravity(Gravity.START);
        report.setPadding(0, 32, 0, 0);
        box.addView(report);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0B1730"));
        sv.addView(box);
        setContentView(sv);
        test();
    }

    void test() {
        report.setText("جاري التحميل…");
        new Thread(() -> {
            String r = Fetcher.run(this);
            PriceWidget.render(this);
            runOnUiThread(() -> report.setText(r.isEmpty() ? "تمام" : r));
        }).start();
    }
}
