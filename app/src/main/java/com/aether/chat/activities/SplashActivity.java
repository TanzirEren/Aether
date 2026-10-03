package com.aether.chat.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.aether.chat.AetherApp;
import com.aether.chat.R;
import com.aether.chat.util.Constants;
import com.aether.chat.util.Prefs;
import com.aether.chat.util.ThemeUtil;
import com.aether.chat.util.Ui;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle b) {
        ThemeUtil.apply(this);
        super.onCreate(b);
        setContentView(R.layout.activity_splash);
        ImageView iv = findViewById(R.id.ivLogo);
        TextView brand = findViewById(R.id.tvBrand);
        TextView tag = findViewById(R.id.tvTagline);
        if (!Constants.AETHER_LOGO_PNG_BASE64.isEmpty()) {
            Bitmap bm = Ui.decodeDataUrl(Constants.AETHER_LOGO_PNG_BASE64, 0);
            if (bm != null) iv.setImageBitmap(bm);
        }
        iv.setAlpha(0f);
        iv.setScaleX(0.8f);
        iv.setScaleY(0.8f);
        brand.setAlpha(0f);
        tag.setAlpha(0f);
        iv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).start();
        brand.animate().alpha(1f).setStartDelay(300).setDuration(350).start();
        tag.animate().alpha(1f).setStartDelay(450).setDuration(350).start();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                route();
            }
        }, 1100);
    }

    private void route() {
        if (!AetherApp.ready) {
            new AlertDialog.Builder(this)
                    .setTitle("Firebase is not configured")
                    .setMessage("Open app/src/main/java/com/aether/chat/util/Constants.java and fill in FB_API_KEY, "
                            + "FB_APP_ID, FB_PROJECT_ID and FB_DATABASE_URL (or set them as GitHub secrets), then rebuild.")
                    .setCancelable(false)
                    .setPositiveButton("OK", (d, w) -> finish())
                    .show();
            return;
        }
        FirebaseUser u = AetherApp.auth.getCurrentUser();
        if (u != null && !Prefs.remember(this) && !AetherApp.sessionAuthed) {
            AetherApp.auth.signOut();
            u = null;
        }
        startActivity(new Intent(this, u == null ? AuthActivity.class : MainActivity.class));
        finish();
    }
}
