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
    static final String A_REFRESH = PKG + "REFRESH", A_STEP = PKG + "STEP", A_NOW = PKG + "NOW",
            A_MODE = PKG + "MODE", A_ZONE = PKG + "ZONE";

    static final ZoneId EGYPT = ZoneId.of("Africa/Cairo");
    static final String[] US_NAMES = {"نيويورك", "تكساس", "كولورادو", "كاليفورنيا"};
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
        } else if (A_STEP.equals(a)) {
            int m = ((convMinutes(p) + intent.getIntExtra("delta", 0)) % 1440 + 1440) % 1440;
            p.edit().putInt("conv_min", m).apply();
        } else if (A_NOW.equals(a)) {
            p.edit().remove("conv_min").apply();
        } else if (A_MODE.equals(a)) {
            p.edit().putInt("conv_mode", 1 - p.getInt("conv_mode", 0)).remove("conv_min").apply();
        } else if (A_ZONE.equals(a)) {
            if (p.getInt("conv_mode", 0) == 1) {
                p.edit().putInt("conv_zone", (p.getInt("conv_zone", 0) + 1) % 4).remove("conv_min").apply();
            }
        } else {
            super.onReceive(ctx, intent);
            return;
        }
        render(ctx);
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

    static int convMinutes(SharedPreferences p) {
        if (p.contains("conv_min")) return p.getInt("conv_min", 0);
        ZoneId src = p.getInt("conv_mode", 0) == 0 ? EGYPT : US_ZONES[p.getInt("conv_zone", 0)];
        ZonedDateTime now = ZonedDateTime.now(src);
        int m = now.getHour() * 60 + now.getMinute();
        return (m / 30) * 30; // round down to the half hour, the way classes are booked
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

        // Time converter
        int mode = p.getInt("conv_mode", 0);
        int zone = p.getInt("conv_zone", 0);
        int mins = convMinutes(p);
        ZoneId srcZone = mode == 0 ? EGYPT : US_ZONES[zone];
        ZonedDateTime src = LocalDate.now(srcZone).atTime(mins / 60, mins % 60).atZone(srcZone);
        v.setTextViewText(R.id.conv_time, src.format(TIME));
        v.setTextViewText(R.id.conv_mode, mode == 0 ? "مصر ← أمريكا  ⇄" : "أمريكا ← مصر  ⇄");
        v.setTextViewText(R.id.conv_src, mode == 0 ? "بتوقيت مصر" : "بتوقيت " + US_NAMES[zone] + "  ▾ غيّر الولاية");

        int[][] slots = {
                {R.id.r1, R.id.r1_name, R.id.r1_time, R.id.r1_day},
                {R.id.r2, R.id.r2_name, R.id.r2_time, R.id.r2_day},
                {R.id.r3, R.id.r3_name, R.id.r3_time, R.id.r3_day},
                {R.id.r4, R.id.r4_name, R.id.r4_time, R.id.r4_day}};
        if (mode == 0) {
            for (int i = 0; i < 4; i++) {
                ZonedDateTime t = src.withZoneSameInstant(US_ZONES[i]);
                v.setViewVisibility(slots[i][0], View.VISIBLE);
                v.setTextViewText(slots[i][1], US_NAMES[i]);
                v.setTextViewText(slots[i][2], t.format(TIME));
                v.setTextViewText(slots[i][3], dayTag(src, t));
            }
        } else {
            ZonedDateTime t = src.withZoneSameInstant(EGYPT);
            v.setViewVisibility(slots[0][0], View.VISIBLE);
            v.setTextViewText(slots[0][1], "في مصر");
            v.setTextViewText(slots[0][2], t.format(TIME));
            v.setTextViewText(slots[0][3], dayTag(src, t));
            for (int i = 1; i < 4; i++) v.setViewVisibility(slots[i][0], View.GONE);
        }

        // Clicks
        PendingIntent refresh = pi(ctx, 1, new Intent(ctx, PriceWidget.class).setAction(A_REFRESH));
        v.setOnClickPendingIntent(R.id.refresh, refresh);
        v.setOnClickPendingIntent(R.id.usd, refresh);
        v.setOnClickPendingIntent(R.id.g24, refresh);
        v.setOnClickPendingIntent(R.id.g21, refresh);
        v.setOnClickPendingIntent(R.id.m60, step(ctx, 2, -60));
        v.setOnClickPendingIntent(R.id.m30, step(ctx, 3, -30));
        v.setOnClickPendingIntent(R.id.p30, step(ctx, 4, 30));
        v.setOnClickPendingIntent(R.id.p60, step(ctx, 5, 60));
        v.setOnClickPendingIntent(R.id.conv_now, pi(ctx, 6, new Intent(ctx, PriceWidget.class).setAction(A_NOW)));
        v.setOnClickPendingIntent(R.id.conv_mode, pi(ctx, 7, new Intent(ctx, PriceWidget.class).setAction(A_MODE)));
        v.setOnClickPendingIntent(R.id.conv_src_box, pi(ctx, 8, new Intent(ctx, PriceWidget.class).setAction(A_ZONE)));

        AppWidgetManager.getInstance(ctx).updateAppWidget(new ComponentName(ctx, PriceWidget.class), v);
    }

    static PendingIntent step(Context ctx, int code, int delta) {
        return pi(ctx, code, new Intent(ctx, PriceWidget.class).setAction(A_STEP).putExtra("delta", delta));
    }

    static PendingIntent pi(Context ctx, int code, Intent i) {
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("prices", Context.MODE_PRIVATE);
    }
}
