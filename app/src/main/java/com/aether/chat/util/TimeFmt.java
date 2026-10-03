package com.aether.chat.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class TimeFmt {
    private TimeFmt() {}

    public static String clock(long t) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(t));
    }

    public static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance();
        Calendar y = Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    public static String listTime(long t) {
        if (t <= 0) return "";
        long now = System.currentTimeMillis();
        if (sameDay(t, now)) return clock(t);
        if (sameDay(t, now - 86400000L)) return "Yesterday";
        return new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date(t));
    }

    public static String dayLabel(long t) {
        long now = System.currentTimeMillis();
        if (sameDay(t, now)) return "Today";
        if (sameDay(t, now - 86400000L)) return "Yesterday";
        return new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(new Date(t));
    }

    public static String lastSeen(long t) {
        if (t <= 0) return "";
        long now = System.currentTimeMillis();
        if (sameDay(t, now)) return "last seen " + clock(t);
        if (sameDay(t, now - 86400000L)) return "last seen yesterday " + clock(t);
        return "last seen " + new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(t));
    }

    public static String duration(long sec) {
        if (sec < 0) sec = 0;
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }
}
