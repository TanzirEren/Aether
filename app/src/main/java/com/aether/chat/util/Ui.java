package com.aether.chat.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Ui {
    private Ui() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final Map<String, Bitmap> INITIALS = new HashMap<>();

    public static void toast(Context c, String s) {
        if (c != null) Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Decode a data-URL / raw base64 image, downsampled so its longest side is about maxPx (0 = full size). */
    public static Bitmap decodeDataUrl(String s, int maxPx) {
        if (s == null || s.isEmpty()) return null;
        String key = s.hashCode() + ":" + s.length() + ":" + maxPx;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        try {
            int i = s.indexOf(',');
            String raw = i >= 0 ? s.substring(i + 1) : s;
            byte[] bytes = Base64.decode(raw, Base64.DEFAULT);
            BitmapFactory.Options o = new BitmapFactory.Options();
            if (maxPx > 0) {
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                int sample = 1;
                int longest = Math.max(o.outWidth, o.outHeight);
                while (longest / sample > maxPx * 2) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
            }
            b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (b != null) CACHE.put(key, b);
        } catch (Exception ignored) {
        }
        return b;
    }

    public static int colorFor(String s) {
        int[] pal = {0xFF1F8A70, 0xFF2E86AB, 0xFFB5651D, 0xFF7B4B94, 0xFFC0392B, 0xFF3C8D40, 0xFF8E5B3A, 0xFF4F6D7A};
        return pal[Math.abs((s == null ? "" : s).hashCode()) % pal.length];
    }

    public static String initialsOf(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        String r = p[0].substring(0, 1);
        if (p.length > 1) r += p[1].substring(0, 1);
        return r.toUpperCase(Locale.ROOT);
    }

    public static Bitmap initials(String name, int size) {
        String key = name + "|" + size;
        Bitmap cached = INITIALS.get(key);
        if (cached != null) return cached;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colorFor(name));
        c.drawRect(0, 0, size, size, p);
        p.setColor(Color.WHITE);
        p.setTextSize(size * 0.42f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(initialsOf(name), size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        INITIALS.put(key, b);
        return b;
    }

    /** Avatar: data-URL image, http image, or generated initials. */
    public static void avatar(ImageView iv, String name, String img) {
        if (img != null && img.startsWith("http")) {
            Glide.with(iv).load(img).centerCrop().into(iv);
            return;
        }
        try {
            Glide.with(iv).clear(iv);
        } catch (Exception ignored) {
        }
        Bitmap b = null;
        if (img != null && img.startsWith("data:")) b = decodeDataUrl(img, 160);
        iv.setImageBitmap(b != null ? b : initials(name, 128));
    }

    /** Banner/cover image from url or data-url. */
    public static void cover(ImageView iv, String img) {
        String src = (img == null || img.isEmpty()) ? Constants.COVERS[0] : img;
        if (src.startsWith("http")) {
            Glide.with(iv).load(src).centerCrop().into(iv);
        } else {
            try {
                Glide.with(iv).clear(iv);
            } catch (Exception ignored) {
            }
            Bitmap b = decodeDataUrl(src, 900);
            if (b != null) iv.setImageBitmap(b);
        }
    }
}
