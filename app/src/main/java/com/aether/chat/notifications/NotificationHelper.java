package com.aether.chat.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.aether.chat.R;
import com.aether.chat.activities.CallActivity;
import com.aether.chat.activities.ChatActivity;
import com.aether.chat.activities.MainActivity;
import com.aether.chat.util.Constants;
import com.aether.chat.util.Prefs;

public final class NotificationHelper {
    private NotificationHelper() {}

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel msg = new NotificationChannel(Constants.CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_DEFAULT);
        msg.setDescription("New messages, friend requests and updates");
        NotificationChannel calls = new NotificationChannel(Constants.CH_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Incoming calls");
        calls.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
        nm.createNotificationChannel(msg);
        nm.createNotificationChannel(calls);
    }

    private static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static void post(Context c, int id, android.app.Notification n) {
        if (!allowed(c)) return;
        try {
            NotificationManagerCompat.from(c).notify(id, n);
        } catch (SecurityException ignored) {
        }
    }

    public static void showMessage(Context c, String chatId, String otherUid, String title, String body) {
        Intent i = ChatActivity.intent(c, otherUid);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, chatId.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, Constants.CH_MESSAGES)
                .setSmallIcon(R.drawable.ic_chat).setContentTitle(title).setContentText(body)
                .setAutoCancel(true).setContentIntent(pi).setSilent(!Prefs.sound(c))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_DEFAULT);
        post(c, chatId.hashCode(), b.build());
    }

    public static void showGeneric(Context c, int id, String title, String body) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, Constants.CH_MESSAGES)
                .setSmallIcon(R.drawable.ic_notifications).setContentTitle(title).setContentText(body)
                .setAutoCancel(true).setContentIntent(pi).setSilent(!Prefs.sound(c));
        post(c, id, b.build());
    }

    public static void showIncomingCall(Context c, String callId, String fromUid, String type, String name) {
        Intent i = CallActivity.incoming(c, callId, fromUid, type);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 7001, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, Constants.CH_CALLS)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle("Incoming " + type + " call")
                .setContentText(name)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true).setAutoCancel(true)
                .setTimeoutAfter(35000)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi);
        post(c, 7001, b.build());
    }

    public static void cancel(Context c, int id) {
        try {
            NotificationManagerCompat.from(c).cancel(id);
        } catch (Exception ignored) {
        }
    }
}
