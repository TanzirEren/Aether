package com.aether.chat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.aether.chat.AetherApp;
import com.aether.chat.R;
import com.aether.chat.listeners.RealtimeHub;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.User;
import com.aether.chat.repository.CacheStore;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.ImageTool;
import com.aether.chat.util.Prefs;
import com.aether.chat.util.ThemeUtil;
import com.aether.chat.util.Ui;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {
    private TextView tvCache;

    @Override
    protected void onCreate(Bundle b) {
        ThemeUtil.apply(this);
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        SwitchMaterial swTheme = findViewById(R.id.swTheme);
        SwitchMaterial swReceipts = findViewById(R.id.swReceipts);
        SwitchMaterial swLastSeen = findViewById(R.id.swLastSeen);
        SwitchMaterial swSound = findViewById(R.id.swSound);
        tvCache = findViewById(R.id.tvCacheSize);
        ((TextView) findViewById(R.id.tvAbout)).setText("Aether 1.0.0\n" + getString(R.string.about_free)
                + "\nBuilt with Firebase Auth + Realtime Database. Open-source libraries: Firebase, Glide, Gson, WebRTC, Material Components.");

        swTheme.setChecked(Prefs.light(this));
        swReceipts.setChecked(Prefs.readReceipts(this));
        swLastSeen.setChecked(Prefs.lastSeenVisible(this));
        swSound.setChecked(Prefs.sound(this));
        swTheme.setOnCheckedChangeListener((v, on) -> {
            Prefs.setLight(this, on);
            recreate();
        });
        swReceipts.setOnCheckedChangeListener((v, on) -> Prefs.setReadReceipts(this, on));
        swLastSeen.setOnCheckedChangeListener((v, on) -> {
            Prefs.setLastSeenVisible(this, on);
            Repo.refreshPresence(true);
        });
        swSound.setOnCheckedChangeListener((v, on) -> Prefs.setSound(this, on));
        refreshCache();
        findViewById(R.id.btnClearCache).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear cache?")
                .setMessage("This only clears the copy stored on this device. Your chats stay safe in Aether's cloud.")
                .setPositiveButton("Clear", (d, w) -> {
                    CacheStore.clearAll();
                    refreshCache();
                    Ui.toast(this, "Cache cleared");
                })
                .setNegativeButton("Cancel", null).show());
        findViewById(R.id.btnBlocked).setOnClickListener(v -> showBlocked());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
    }

    private void refreshCache() {
        StringBuilder sb = new StringBuilder("Total cached: ").append(ImageTool.formatSize(CacheStore.sizeBytes()));
        Map<String, Long> sizes = CacheStore.chatSizes();
        int n = 0;
        for (Map.Entry<String, Long> e : sizes.entrySet()) {
            if (n++ >= 6) break;
            sb.append("\n\u2022 chat ").append(e.getKey().substring(0, Math.min(6, e.getKey().length()))).append("\u2026  ")
                    .append(ImageTool.formatSize(e.getValue()));
        }
        tvCache.setText(sb.toString());
    }

    private void showBlocked() {
        Repo.ref("userPrivate/" + Repo.me() + "/blocked").addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                final List<String> uids = new ArrayList<>();
                for (DataSnapshot c : s.getChildren()) uids.add(c.getKey());
                if (uids.isEmpty()) {
                    Ui.toast(SettingsActivity.this, "You haven't blocked anyone");
                    return;
                }
                final String[] names = new String[uids.size()];
                final int[] left = {uids.size()};
                for (int i = 0; i < uids.size(); i++) {
                    final int idx = i;
                    Repo.loadUser(uids.get(i), new Repo.Result<User>() {
                        @Override
                        public void got(User u) {
                            names[idx] = u == null ? uids.get(idx) : u.display() + (u.username != null ? " (@" + u.username + ")" : "");
                            if (--left[0] == 0) {
                                new AlertDialog.Builder(SettingsActivity.this).setTitle("Tap to unblock")
                                        .setItems(names, (d, which) -> Repo.setBlocked(uids.get(which), false,
                                                (ok, err) -> Ui.toast(SettingsActivity.this, ok ? "Unblocked" : "Failed: " + err)))
                                        .setNegativeButton("Close", null).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete account?")
                .setMessage("This permanently removes your profile, contacts and username. Messages you sent remain in your friends' chats. This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> Repo.deleteAccount((ok, err) -> {
                    if (ok) {
                        RealtimeHub.stop();
                        Intent i = new Intent(this, AuthActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    } else {
                        Ui.toast(this, err);
                    }
                }))
                .setNegativeButton("Cancel", null).show();
    }
}
