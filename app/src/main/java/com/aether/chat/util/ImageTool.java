package com.aether.chat.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageTool {
    private ImageTool() {}

    /** Compress an image to a JPEG data-URL (max dim + quality per PRD). */
    public static String imageToDataUrl(Context c, Uri uri, int maxDim, int quality, int targetBytes) throws IOException {
        ContentResolver cr = c.getContentResolver();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > maxDim * 2) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bm;
        try (InputStream in = cr.openInputStream(uri)) {
            bm = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bm == null) throw new IOException("Could not read image");
        int rotate = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
            }
        } catch (Exception ignored) {
        }
        float scale = Math.min(1f, (float) maxDim / Math.max(bm.getWidth(), bm.getHeight()));
        Matrix m = new Matrix();
        if (scale < 1f) m.postScale(scale, scale);
        if (rotate != 0) m.postRotate(rotate);
        if (scale < 1f || rotate != 0) {
            Bitmap nb = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            if (nb != bm) bm.recycle();
            bm = nb;
        }
        int q = quality;
        byte[] out;
        while (true) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, q, bos);
            out = bos.toByteArray();
            if (out.length <= targetBytes || q <= 40) break;
            q -= 8;
        }
        if (out.length > targetBytes * 2) {
            Bitmap nb = Bitmap.createScaledBitmap(bm, Math.max(1, bm.getWidth() * 3 / 4), Math.max(1, bm.getHeight() * 3 / 4), true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            nb.compress(Bitmap.CompressFormat.JPEG, 55, bos);
            out = bos.toByteArray();
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(out, Base64.NO_WRAP);
    }

    public static String chatImage(Context c, Uri uri) throws IOException {
        return imageToDataUrl(c, uri, Constants.MAX_IMAGE_DIM, Constants.IMAGE_QUALITY, Constants.MAX_IMAGE_BYTES);
    }

    public static String avatarImage(Context c, Uri uri) throws IOException {
        return imageToDataUrl(c, uri, 256, 70, 40 * 1024);
    }

    public static String coverImage(Context c, Uri uri) throws IOException {
        return imageToDataUrl(c, uri, 900, 65, 120 * 1024);
    }

    /** Reads up to limit+1 bytes; caller checks length > limit. */
    public static byte[] readBytes(Context c, Uri uri, long limit) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open file");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                total += n;
                if (total > limit) break;
            }
            return bos.toByteArray();
        }
    }

    public static String displayName(Context c, Uri uri) {
        String name = null;
        try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = cur.getString(i);
            }
        } catch (Exception ignored) {
        }
        if (name == null) {
            String p = uri.getLastPathSegment();
            name = p == null ? "file" : p;
        }
        return name;
    }

    public static byte[] dataUrlBytes(String dataUrl) {
        int i = dataUrl.indexOf(',');
        String raw = i >= 0 ? dataUrl.substring(i + 1) : dataUrl;
        return Base64.decode(raw, Base64.DEFAULT);
    }

    public static String toDataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static String safeName(String n) {
        return n == null ? "file" : n.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Writes decoded data-URL bytes into the cache dir (reused if present). */
    public static File writeCacheFile(Context c, String name, String dataUrl) throws IOException {
        File f = new File(c.getCacheDir(), safeName(name));
        if (!f.exists() || f.length() == 0) {
            try (FileOutputStream fo = new FileOutputStream(f)) {
                fo.write(dataUrlBytes(dataUrl));
            }
        }
        return f;
    }

    public static String formatSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }
}
