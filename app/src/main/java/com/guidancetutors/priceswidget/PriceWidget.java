package com.guidancetutors.priceswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.guidancetutors.priceswidget.REFRESH";
    static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";
    static final double OZ_GRAMS = 31.1034768;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx, null);
        refresh(ctx, goAsync());
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            render(ctx, "جاري التحديث…");
            refresh(ctx, goAsync());
        } else {
            super.onReceive(ctx, intent);
        }
    }

    static void refresh(final Context ctx, final PendingResult pending) {
        new Thread(() -> {
            SharedPreferences p = prefs(ctx);
            SharedPreferences.Editor e = p.edit();
            boolean anyOk = false;

            // Dollar: bank (interbank) rate, live first, daily fallback.
            String usdSrc = "سعر لحظي";
            Double usd = num(get("https://query1.finance.yahoo.com/v8/finance/chart/EGP=X?interval=1m&range=1d"), "\"regularMarketPrice\":([0-9.]+)");
            if (usd == null) usd = num(get("https://query2.finance.yahoo.com/v8/finance/chart/EGP=X?interval=1m&range=1d"), "\"regularMarketPrice\":([0-9.]+)");
            if (usd == null) usd = num(get("https://www.google.com/finance/quote/USD-EGP"), "data-last-price=\"([0-9.]+)\"");
            if (usd == null) { usd = num(get("https://open.er-api.com/v6/latest/USD"), "\"EGP\":([0-9.]+)"); usdSrc = "سعر اليوم"; }
            if (usd != null) { e.putFloat("usd", usd.floatValue()); e.putString("usd_src", usdSrc); anyOk = true; }

            // Gold 24k: Egyptian jewellers' price from iSagha; estimate from world price if it fails.
            String goldSrc = "سعر الصاغة · آي صاغة";
            Double gold = num(get("https://market.isagha.com/prices"),
                    "data-unit=\"24\"\\s+data-metal=\"gold\"[\\s\\S]{0,800}?data-testid=\"price-figure\"[^>]*>\\s*([0-9.,]+)");
            if (gold == null) {
                Double oz = num(get("https://api.gold-api.com/price/XAU"), "\"price\":([0-9.]+)");
                Double rate = usd != null ? usd : (p.contains("usd") ? (double) p.getFloat("usd", 0) : null);
                if (oz != null && rate != null && rate > 0) { gold = oz * rate / OZ_GRAMS; goldSrc = "تقديري من السعر العالمي"; }
            }
            if (gold != null) { e.putFloat("gold", gold.floatValue()); e.putString("gold_src", goldSrc); anyOk = true; }

            if (anyOk) e.putLong("time", System.currentTimeMillis());
            e.apply();
            render(ctx, anyOk ? null : "فيه مشكلة في النت، اضغط تحديث تاني");
            pending.finish();
        }).start();
    }

    static void render(Context ctx, String status) {
        SharedPreferences p = prefs(ctx);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        if (p.contains("usd")) v.setTextViewText(R.id.usd, String.format(Locale.US, "%.2f", p.getFloat("usd", 0)));
        if (p.contains("gold")) v.setTextViewText(R.id.gold, String.format(Locale.US, "%,.0f", p.getFloat("gold", 0)));
        v.setTextViewText(R.id.usd_src, "جنيه · " + p.getString("usd_src", "—"));
        v.setTextViewText(R.id.gold_src, "جنيه · " + p.getString("gold_src", "—"));

        String line = status;
        if (line == null) {
            long t = p.getLong("time", 0);
            line = t == 0 ? "اضغط تحديث"
                    : "آخر تحديث " + new SimpleDateFormat("h:mm a", new Locale("ar", "EG")).format(new Date(t));
        }
        v.setTextViewText(R.id.updated, line);

        Intent i = new Intent(ctx, PriceWidget.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.refresh, pi);
        v.setOnClickPendingIntent(R.id.usd, pi);
        v.setOnClickPendingIntent(R.id.gold, pi);

        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        mgr.updateAppWidget(new ComponentName(ctx, PriceWidget.class), v);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("prices", Context.MODE_PRIVATE);
    }

    static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8");
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception ex) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static Double num(String body, String regex) {
        if (body == null) return null;
        Matcher m = Pattern.compile(regex).matcher(body);
        if (!m.find()) return null;
        try {
            double d = Double.parseDouble(m.group(1).replace(",", ""));
            return d > 0 ? d : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
