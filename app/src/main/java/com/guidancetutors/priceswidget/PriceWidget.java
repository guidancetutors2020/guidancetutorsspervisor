package com.guidancetutors.priceswidget;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class PriceWidget extends AppWidgetProvider {
    static final String PKG = "com.guidancetutors.priceswidget.";
    static final String A_REFRESH = PKG + "REFRESH", A_KEY = PKG + "KEY", A_MODE = PKG + "MODE", A_NOOP = PKG + "NOOP";

    static final ZoneId EGYPT = ZoneId.of("Africa/Cairo");
    static final String[] US_NAMES = {"الشرقي", "المركزي", "الجبلي", "الغربي"};
    static final String ST_E = "نيويورك، نيوجيرسي، ميشيغان، فلوريدا، جورجيا، نورث كارولينا، فيرجينيا، ماريلاند، بنسلفانيا، أوهايو، ماساتشوستس، كونيتيكت، إنديانا، واشنطن العاصمة";
    static final String ST_C = "تكساس، إلينوي، مينيسوتا، ويسكونسن، ميزوري، تينيسي، أوكلاهوما، لويزيانا، ألاباما، أيوا، كانساس";
    static final String ST_M = "كولورادو، يوتا، نيو مكسيكو، أيداهو، مونتانا، وايومنغ";
    static final String ST_P = "كاليفورنيا، ولاية واشنطن، أوريجون، نيفادا";
    static final ZoneId[] US_ZONES = {ZoneId.of("America/New_York"), ZoneId.of("America/Chicago"),
            ZoneId.of("America/Denver"), ZoneId.of("America/Los_Angeles")};
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx);
        startRefresh(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        SharedPreferences p = prefs(ctx);
        if (A_REFRESH.equals(a)) {
            startRefresh(ctx);
            return;
        } else if (A_NOOP.equals(a)) {
            return;
        } else if (A_KEY.equals(a)) {
            String k = intent.getStringExtra("k");
            String buf = p.getString("buf", "");
            SharedPreferences.Editor e = p.edit();
            if (k == null) return;
            if (k.length() == 1 && Character.isDigit(k.charAt(0))) {
                if (buf.length() < 4) e.putString("buf", buf + k);
            } else if (k.equals("del")) {
                if (!buf.isEmpty()) e.putString("buf", buf.substring(0, buf.length() - 1));
            } else if (k.equals("clr")) {
                e.putString("buf", "");
            } else if (k.equals("now")) {
                ZonedDateTime now = ZonedDateTime.now(p.getInt("conv_mode", 0) == 0 ? EGYPT : US_ZONES[0]);
                e.putString("buf", String.format(Locale.US, "%02d%02d", now.getHour(), now.getMinute()))
                        .putString("ampm", now.getHour() < 12 ? "AM" : "PM");
            } else if (k.equals("AM") || k.equals("PM")) {
                e.putString("ampm", k);
            }
            e.apply();
        } else if (A_MODE.equals(a)) {
            p.edit().putInt("conv_mode", 1 - p.getInt("conv_mode", 0)).putString("buf", "").apply();
        } else {
            super.onReceive(ctx, intent);
            return;
        }
        render(ctx);
    }

    /**
     * Turns typed digits into minutes of the day. 1-2 digits = hour ("7", "11", "19"),
     * 2 digits above 23 = hour + tens of minutes ("73" = 7:30), 3 digits = H:MM, 4 digits = HH:MM.
     * Hours 1-12 follow the AM/PM keys; 0 and 13-23 are read as 24-hour time. Returns -1 if invalid.
     */
    static int parse(String buf, String ampm) {
        if (buf.isEmpty()) return -1;
        int h, m;
        int n = Integer.parseInt(buf);
        if (buf.length() <= 2 && n <= 23) { h = n; m = 0; }
        else if (buf.length() == 2) { h = buf.charAt(0) - '0'; m = (buf.charAt(1) - '0') * 10; }
        else if (buf.length() == 3) { h = buf.charAt(0) - '0'; m = Integer.parseInt(buf.substring(1)); }
        else { h = Integer.parseInt(buf.substring(0, 2)); m = Integer.parseInt(buf.substring(2)); }
        if (h > 23 || m > 59) return -1;
        if (h >= 1 && h <= 12) {
            if (h == 12) h = 0;
            if ("PM".equals(ampm)) h += 12;
        }
        return h * 60 + m;
    }

    /** Network work runs as a job: Android blocks network for a bare broadcast in the background. */
    static void startRefresh(Context ctx) {
        prefs(ctx).edit().putString("status", "جاري تحديث الأسعار…").apply();
        render(ctx);
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        ComponentName cn = new ComponentName(ctx, PriceJob.class);
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                ok = js.schedule(new JobInfo.Builder(42, cn)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true).build()) == JobScheduler.RESULT_SUCCESS;
            } catch (Exception ignored) { }
        }
        if (!ok) {
            js.schedule(new JobInfo.Builder(42, cn)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(2000).build());
        }
    }

    static String dayTag(ZonedDateTime from, ZonedDateTime to) {
        long d = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
        if (d > 0) return "اليوم التالي";
        if (d < 0) return "اليوم السابق";
        return " ";
    }

    static void render(Context ctx) {
        SharedPreferences p = prefs(ctx);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);

        // Prices
        if (p.contains("usd")) v.setTextViewText(R.id.usd, String.format(Locale.US, "%.2f", p.getFloat("usd", 0)));
        if (p.contains("g24")) v.setTextViewText(R.id.g24, String.format(Locale.US, "%,.0f", p.getFloat("g24", 0)));
        if (p.contains("g21")) v.setTextViewText(R.id.g21, String.format(Locale.US, "%,.0f", p.getFloat("g21", 0)));
        v.setTextViewText(R.id.usd_sub, "جنيه · " + p.getString("usd_src", "سعر البنك"));
        v.setTextViewText(R.id.g24_sub, "جنيه/جرام · " + p.getString("gold_src", "الصاغة"));
        v.setTextViewText(R.id.g21_sub, "جنيه/جرام · " + p.getString("gold_src", "الصاغة"));

        String status = p.getString("status", null);
        if (status == null) {
            long t = p.getLong("time", 0);
            status = t == 0 ? "اضغط تحديث الأسعار"
                    : "آخر تحديث " + java.time.Instant.ofEpochMilli(t).atZone(EGYPT).format(TIME);
        }
        v.setTextViewText(R.id.updated, status);

        // States per zone. Arizona skips daylight saving, so it sits with Mountain in winter and Pacific in summer.
        java.time.Instant nowI = java.time.Instant.now();
        boolean azMountain = ZoneId.of("America/Phoenix").getRules().getOffset(nowI)
                .equals(US_ZONES[2].getRules().getOffset(nowI));
        v.setTextViewText(R.id.st_e, ST_E);
        v.setTextViewText(R.id.st_c, ST_C);
        v.setTextViewText(R.id.st_m, azMountain ? ST_M + "، أريزونا" : ST_M);
        v.setTextViewText(R.id.st_p, azMountain ? ST_P : ST_P + "، أريزونا");

        // Time converter
        int mode = p.getInt("conv_mode", 0);
        String buf = p.getString("buf", "");
        String ampm = p.getString("ampm", "PM");
        int mins = parse(buf, ampm);
        boolean typed = !buf.isEmpty();
        boolean bad = typed && mins < 0;
        boolean ampmApplies = !typed || bad || (parse(buf, "AM") != parse(buf, "PM"));

        v.setTextViewText(R.id.conv_mode, mode == 0 ? "مصر ← أمريكا  ⇄" : "أمريكا ← مصر  ⇄");
        v.setTextViewText(R.id.conv_hint, mode == 0 ? "اكتب الوقت بتوقيت مصر" : "اكتب الوقت بتوقيت أمريكا");
        v.setTextViewText(R.id.res_label, mode == 0
                ? "الوقت ده في أمريكا"
                : "الوقت ده في مصر، لو كان بتوقيت:");
        v.setInt(R.id.kam, "setBackgroundResource", ampmApplies && "AM".equals(ampm) ? R.drawable.key_on : R.drawable.key_soft);
        v.setInt(R.id.kpm, "setBackgroundResource", ampmApplies && "PM".equals(ampm) ? R.drawable.key_on : R.drawable.key_soft);

        int[][] slots = {
                {R.id.r1, R.id.r1_name, R.id.r1_time, R.id.r1_day},
                {R.id.r2, R.id.r2_name, R.id.r2_time, R.id.r2_day},
                {R.id.r3, R.id.r3_name, R.id.r3_time, R.id.r3_day},
                {R.id.r4, R.id.r4_name, R.id.r4_time, R.id.r4_day}};
        if (bad) {
            v.setTextViewText(R.id.conv_time, "وقت غير صحيح");
            v.setTextColor(R.id.conv_time, 0xFFF4CF70);
        } else {
            v.setTextColor(R.id.conv_time, 0xFFEEF3FF);
        }
        for (int i = 0; i < 4; i++) {
            v.setTextViewText(slots[i][1], US_NAMES[i]);
            if (bad) { v.setTextViewText(slots[i][2], "--"); v.setTextViewText(slots[i][3], " "); continue; }
            if (!typed) {
                if (i == 0) v.setTextViewText(R.id.conv_time, "--:--");
                v.setTextViewText(slots[i][2], "--");
                v.setTextViewText(slots[i][3], " ");
                continue;
            }
            if (mode == 0) {
                ZonedDateTime src = LocalDate.now(EGYPT).atTime(mins / 60, mins % 60).atZone(EGYPT);
                if (i == 0) v.setTextViewText(R.id.conv_time, src.format(TIME));
                ZonedDateTime t = src.withZoneSameInstant(US_ZONES[i]);
                v.setTextViewText(slots[i][2], t.format(TIME));
                v.setTextViewText(slots[i][3], dayTag(src, t));
            } else {
                if (!typed) {
                    if (i == 0) v.setTextViewText(R.id.conv_time, "--:--");
                    v.setTextViewText(slots[i][2], "--");
                    v.setTextViewText(slots[i][3], " ");
                    continue;
                }
                ZonedDateTime src = LocalDate.now(US_ZONES[i]).atTime(mins / 60, mins % 60).atZone(US_ZONES[i]);
                if (i == 0) v.setTextViewText(R.id.conv_time, src.format(TIME));
                ZonedDateTime t = src.withZoneSameInstant(EGYPT);
                v.setTextViewText(slots[i][2], t.format(TIME));
                v.setTextViewText(slots[i][3], dayTag(src, t));
            }
        }

        // Clicks
        PendingIntent refresh = pi(ctx, 1, new Intent(ctx, PriceWidget.class).setAction(A_REFRESH));
        v.setOnClickPendingIntent(R.id.refresh, refresh);
        v.setOnClickPendingIntent(R.id.usd, refresh);
        v.setOnClickPendingIntent(R.id.g24, refresh);
        v.setOnClickPendingIntent(R.id.g21, refresh);
        v.setOnClickPendingIntent(R.id.root, pi(ctx, 2, new Intent(ctx, PriceWidget.class).setAction(A_NOOP)));
        v.setOnClickPendingIntent(R.id.conv_mode, pi(ctx, 3, new Intent(ctx, PriceWidget.class).setAction(A_MODE)));
        int[] keyIds = {R.id.k0, R.id.k1, R.id.k2, R.id.k3, R.id.k4, R.id.k5, R.id.k6, R.id.k7, R.id.k8, R.id.k9};
        for (int d = 0; d < 10; d++) v.setOnClickPendingIntent(keyIds[d], key(ctx, 10 + d, String.valueOf(d)));
        v.setOnClickPendingIntent(R.id.kdel, key(ctx, 20, "del"));
        v.setOnClickPendingIntent(R.id.kclr, key(ctx, 21, "clr"));
        v.setOnClickPendingIntent(R.id.know, key(ctx, 22, "now"));
        v.setOnClickPendingIntent(R.id.kam, key(ctx, 23, "AM"));
        v.setOnClickPendingIntent(R.id.kpm, key(ctx, 24, "PM"));

        AppWidgetManager.getInstance(ctx).updateAppWidget(new ComponentName(ctx, PriceWidget.class), v);
    }

    static PendingIntent key(Context ctx, int code, String k) {
        return pi(ctx, code, new Intent(ctx, PriceWidget.class).setAction(A_KEY).putExtra("k", k));
    }

    static PendingIntent pi(Context ctx, int code, Intent i) {
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("prices", Context.MODE_PRIVATE);
    }
}
