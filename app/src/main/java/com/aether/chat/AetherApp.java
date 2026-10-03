package com.aether.chat;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import com.aether.chat.notifications.NotificationHelper;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Constants;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public class AetherApp extends Application {
    public static FirebaseDatabase db;
    public static FirebaseAuth auth;
    public static boolean ready;
    public static boolean sessionAuthed;
    public static String openChatId;
    private static int started;

    public static boolean foreground() {
        return started > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Repo.setContext(this);
        ready = initFirebase(this);
        NotificationHelper.createChannels(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) {
                started++;
                if (started == 1) Repo.setOnline(true);
            }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) {
                started--;
                if (started <= 0) {
                    started = 0;
                    Repo.setOnline(false);
                }
            }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    public static boolean initFirebase(Context c) {
        if (!Constants.isConfigured()) return false;
        try {
            FirebaseApp fb;
            if (FirebaseApp.getApps(c).isEmpty()) {
                FirebaseOptions o = new FirebaseOptions.Builder()
                        .setApiKey(Constants.FB_API_KEY)
                        .setApplicationId(Constants.FB_APP_ID)
                        .setProjectId(Constants.FB_PROJECT_ID)
                        .setDatabaseUrl(Constants.FB_DATABASE_URL)
                        .build();
                fb = FirebaseApp.initializeApp(c, o);
            } else {
                fb = FirebaseApp.getInstance();
            }
            db = FirebaseDatabase.getInstance(fb);
            try {
                db.setPersistenceEnabled(true);
            } catch (Exception ignored) {
            }
            auth = FirebaseAuth.getInstance(fb);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
