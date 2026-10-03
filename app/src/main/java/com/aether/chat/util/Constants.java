package com.aether.chat.util;

public final class Constants {
    private Constants() {}

    // ============ FIREBASE CONFIG - REPLACE THESE 4 VALUES ============
    // Firebase Console -> Project settings -> Your apps -> Android app
    public static final String FB_API_KEY = "AIzaSyCjivnazF2_nHKiYEzbh_ungY3pTcZ0jgg";
    public static final String FB_APP_ID = "1:325050196400:android:011af441d36772a7a77675";
    public static final String FB_PROJECT_ID = "aether-7dddc";
    public static final String FB_DATABASE_URL = "https://aether-7dddc-default-rtdb.firebaseio.com";
    // ==================================================================

    public static boolean isConfigured() {
        return !FB_API_KEY.startsWith("YOUR_") && !FB_APP_ID.startsWith("YOUR_")
                && !FB_PROJECT_ID.startsWith("YOUR_") && !FB_DATABASE_URL.contains("YOUR_");
    }

    // Brand logo: paste the Base64 of your Ae PNG here. Empty = built-in vector logo.
    public static final String AETHER_LOGO_PNG_BASE64 = "";

    public static final long MAX_FILE_BYTES = 1500000L;
    public static final int MAX_IMAGE_DIM = 1280;
    public static final int IMAGE_QUALITY = 72;
    public static final int MAX_IMAGE_BYTES = 250 * 1024;
    public static final int MAX_VOICE_SEC = 90;
    public static final int CACHE_MESSAGES = 500;
    public static final String TOO_BIG = "Aether keeps things light \u2014 files up to 1.5MB please \uD83E\uDEB6";

    public static final String T_TEXT = "text";
    public static final String T_IMAGE = "image";
    public static final String T_FILE = "file";
    public static final String T_VOICE = "voice";
    public static final String T_SYSTEM = "system";

    public static final String[] COVERS = {
            "https://images.unsplash.com/photo-1534796636912-3b95b3ab5986?auto=format&fit=crop&w=900&q=70",
            "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?auto=format&fit=crop&w=900&q=70",
            "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=900&q=70",
            "https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?auto=format&fit=crop&w=900&q=70"
    };

    // WebRTC: free public STUN. Add a TURN entry in WebRTCManager if calls fail on strict NATs.
    public static final String[] STUN = {
            "stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"
    };

    public static final String CH_MESSAGES = "aether_messages";
    public static final String CH_CALLS = "aether_calls";
}
