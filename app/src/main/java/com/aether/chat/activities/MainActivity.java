package com.aether.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.aether.chat.AetherApp;
import com.aether.chat.R;
import com.aether.chat.fragments.CallsFragment;
import com.aether.chat.fragments.ContactsFragment;
import com.aether.chat.fragments.HomeFragment;
import com.aether.chat.fragments.ProfileFragment;
import com.aether.chat.fragments.UpdatesFragment;
import com.aether.chat.listeners.RealtimeHub;
import com.aether.chat.repository.CacheStore;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.ThemeUtil;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView nav;
    private final ActivityResultLauncher<String> notifPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> { });

    @Override
    protected void onCreate(Bundle b) {
        ThemeUtil.apply(this);
        super.onCreate(b);
        String uid = Repo.me();
        if (uid == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        CacheStore.init(this, uid);
        Repo.startPresence();
        Repo.watchMe();
        Repo.recordLogin(this);
        RealtimeHub.start(this, uid);

        nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if (id == R.id.nav_updates) f = new UpdatesFragment();
            else if (id == R.id.nav_calls) f = new CallsFragment();
            else if (id == R.id.nav_contacts) f = new ContactsFragment();
            else if (id == R.id.nav_profile) f = new ProfileFragment();
            else f = new HomeFragment();
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.container, f).commit();
            return true;
        });
        if (b == null) nav.setSelectedItemId(R.id.nav_home);

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        RealtimeHub.setBadgeCallback(this::updateBadge);
    }

    @Override
    protected void onStop() {
        super.onStop();
        RealtimeHub.setBadgeCallback(null);
    }

    private void updateBadge() {
        if (nav == null) return;
        BadgeDrawable bd = nav.getOrCreateBadge(R.id.nav_updates);
        int n = RealtimeHub.updatesCount;
        bd.setBackgroundColor(0xFFFFB648);
        bd.setBadgeTextColor(0xFF06141B);
        if (n > 0) {
            bd.setVisible(true);
            bd.setNumber(n);
        } else {
            bd.setVisible(false);
            bd.clearNumber();
        }
    }
}
