package com.aether.chat.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.aether.chat.R;
import com.aether.chat.util.Ui;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {
    private ImageView iv;
    private final Matrix matrix = new Matrix();
    private float scale = 1f, baseScale = 1f;
    private float lastX, lastY;
    private boolean dragging;
    private ScaleGestureDetector sgd;
    private float downY;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_image_viewer);
        final String path = getIntent().getStringExtra("path");
        iv = findViewById(R.id.imageView);
        if (path == null || !path.startsWith(getCacheDir().getAbsolutePath())) { finish(); return; }
        final Bitmap bm = BitmapFactory.decodeFile(path);
        if (bm == null) { Ui.toast(this, "Could not open image"); finish(); return; }
        iv.setImageBitmap(bm);
        iv.post(() -> {
            float sx = (float) iv.getWidth() / bm.getWidth();
            float sy = (float) iv.getHeight() / bm.getHeight();
            baseScale = Math.min(sx, sy);
            matrix.setScale(baseScale, baseScale);
            matrix.postTranslate((iv.getWidth() - bm.getWidth() * baseScale) / 2f, (iv.getHeight() - bm.getHeight() * baseScale) / 2f);
            iv.setImageMatrix(matrix);
            scale = 1f;
        });
        sgd = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float ns = Math.max(1f, Math.min(5f, scale * f));
                f = ns / scale;
                scale = ns;
                matrix.postScale(f, f, d.getFocusX(), d.getFocusY());
                iv.setImageMatrix(matrix);
                return true;
            }
        });
        iv.setOnTouchListener((v, e) -> {
            sgd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX(); lastY = e.getY(); downY = e.getY(); dragging = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && !sgd.isInProgress() && e.getPointerCount() == 1 && scale > 1.02f) {
                        matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
                        iv.setImageMatrix(matrix);
                    }
                    lastX = e.getX(); lastY = e.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    // swipe down to close (only when not zoomed)
                    if (scale <= 1.02f && e.getY() - downY > Ui.dp(this, 140)) finish();
                    dragging = false;
                    break;
                default:
                    break;
            }
            return true;
        });
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnShare).setOnClickListener(v -> {
            try {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("image/jpeg");
                s.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(path)));
                s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(s, "Share image"));
            } catch (Exception ex) {
                Ui.toast(this, "Could not share");
            }
        });
    }
}
