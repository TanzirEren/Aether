package com.aether.chat.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("aether_prefs", Context.MODE_PRIVATE);
    }
    public static boolean light(Context c) { return sp(c).getBoolean("light", false); }
    public static void setLight(Context c, boolean v) { sp(c).edit().putBoolean("light", v).apply(); }
    public static boolean readReceipts(Context c) { return sp(c).getBoolean("readReceipts", true); }
    public static void setReadReceipts(Context c, boolean v) { sp(c).edit().putBoolean("readReceipts", v).apply(); }
    public static boolean lastSeenVisible(Context c) { return sp(c).getBoolean("lastSeenVisible", true); }
    public static void setLastSeenVisible(Context c, boolean v) { sp(c).edit().putBoolean("lastSeenVisible", v).apply(); }
    public static boolean sound(Context c) { return sp(c).getBoolean("sound", true); }
    public static void setSound(Context c, boolean v) { sp(c).edit().putBoolean("sound", v).apply(); }
    public static boolean remember(Context c) { return sp(c).getBoolean("remember", true); }
    public static void setRemember(Context c, boolean v) { sp(c).edit().putBoolean("remember", v).apply(); }
    public static String deviceId(Context c) {
        String id = sp(c).getString("deviceId", null);
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            sp(c).edit().putString("deviceId", id).apply();
        }
        return id;
    }
}
