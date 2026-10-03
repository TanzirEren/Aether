package com.aether.chat.util;

import android.app.Activity;

import com.aether.chat.R;

public final class ThemeUtil {
    private ThemeUtil() {}

    /** Call BEFORE super.onCreate(). */
    public static void apply(Activity a) {
        a.setTheme(Prefs.light(a) ? R.style.Theme_Aether_Light : R.style.Theme_Aether);
    }
}
