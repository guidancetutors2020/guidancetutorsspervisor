package com.guidancetutors.priceswidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches prices, saves them, and returns a short report of what each source answered. */
class Fetcher {
    static final String UA = "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36";
    static final double OZ_GRAMS = 31.1034768;
    static final StringBuilder log = new StringBuilder();

    static synchronized String run(Context ctx) {
        log.setLength(0);
        SharedPreferences p = PriceWidget.prefs(ctx);
        SharedPreferences.Editor e = p.edit();
        boolean ok = false;

        String usdSrc = "سعر البنك";
        Double usd = num("Yahoo", get("https://query1.finance.yahoo.com/v8/finance/chart/EGP=X?interval=1m&range=1d"), "\"regularMarketPrice\":([0-9.]+)");
        if (usd == null) usd = num("Yahoo2", get("https://query2.finance.yahoo.com/v8/finance/chart/EGP=X?interval=1m&range=1d"), "\"regularMarketPrice\":([0-9.]+)");
        if (usd == null) usd = num("Google", get("https://www.google.com/finance/quote/USD-EGP"), "data-last-price=\"([0-9.]+)\"");
        if (usd == null) { usd = num("ER-API", get("https://open.er-api.com/v6/latest/USD"), "\"EGP\":([0-9.]+)"); usdSrc = "سعر اليوم"; }
        if (usd != null) { e.putFloat("usd", usd.floatValue()).putString("usd_src", usdSrc); ok = true; }

        String goldSrc = "الصاغة";
        String page = get("https://market.isagha.com/prices");
        Double g24 = num("iSagha 24", page, "data-unit=\"24\"\\s+data-metal=\"gold\"[\\s\\S]{0,800}?data-testid=\"price-figure\"[^>]*>\\s*([0-9.,]+)");
        Double g21 = num("iSagha 21", page, "data-unit=\"21\"\\s+data-metal=\"gold\"[\\s\\S]{0,800}?data-testid=\"price-figure\"[^>]*>\\s*([0-9.,]+)");
        if (g24 == null) {
            Double oz = num("Gold-API", get("https://api.gold-api.com/price/XAU"), "\"price\":([0-9.]+)");
            Double rate = usd != null ? usd : (p.contains("usd") ? (double) p.getFloat("usd", 0) : null);
            if (oz != null && rate != null && rate > 0) { g24 = oz * rate / OZ_GRAMS; goldSrc = "تقديري"; }
        }
        if (g24 != null && g21 == null) g21 = g24 * 21.0 / 24.0;
        if (g24 != null) { e.putFloat("g24", g24.floatValue()).putFloat("g21", g21.floatValue()).putString("gold_src", goldSrc); ok = true; }

        if (ok) e.putLong("time", System.currentTimeMillis()).remove("status");
        else e.putString("status", "تعذّر التحديث، اضغط تاني بعد شوية");
        e.apply();
        return log.toString();
    }

    static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8");
            int code = c.getResponseCode();
            if (code != 200) { log.append("HTTP ").append(code).append(" ← ").append(host(url)).append('\n'); return null; }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception ex) {
            log.append(ex.getClass().getSimpleName()).append(" ← ").append(host(url)).append('\n');
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String host(String url) { try { return new URL(url).getHost(); } catch (Exception e) { return url; } }

    static Double num(String name, String body, String regex) {
        if (body == null) return null;
        Matcher m = Pattern.compile(regex).matcher(body);
        if (!m.find()) { log.append(name).append(": لم يُعثر على السعر\n"); return null; }
        try {
            double d = Double.parseDouble(m.group(1).replace(",", ""));
            log.append(name).append(": ").append(d).append('\n');
            return d > 0 ? d : null;
        } catch (Exception ex) { return null; }
    }
}
