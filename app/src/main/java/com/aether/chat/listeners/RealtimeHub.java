package com.aether.chat.listeners;

import android.content.Context;
import android.content.Intent;

import com.aether.chat.AetherApp;
import com.aether.chat.activities.CallActivity;
import com.aether.chat.model.FriendRequest;
import com.aether.chat.model.Notif;
import com.aether.chat.model.User;
import com.aether.chat.model.UserChat;
import com.aether.chat.notifications.NotificationHelper;
import com.aether.chat.repository.Repo;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ChildEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * App-wide realtime listeners (PRD 11): incoming calls, new-message / friend-request notifications,
 * delivery receipts and the Updates badge. Runs while the app process is alive.
 */
public final class RealtimeHub {
    private RealtimeHub() {}

    private static final class Reg {
        DatabaseReference ref;
        Object listener;
    }

    private static final List<Reg> REGS = new ArrayList<>();
    private static String started;
    private static String lastRingId;
    public static int updatesCount = 0;
    private static int pendingRequests = 0, unreadNotifs = 0;
    private static Runnable badgeCb;

    public static void setBadgeCallback(Runnable r) {
        badgeCb = r;
        if (r != null) r.run();
    }

    private static void add(DatabaseReference r, Object l) {
        Reg g = new Reg();
        g.ref = r;
        g.listener = l;
        REGS.add(g);
    }

    private static void recount() {
        updatesCount = pendingRequests + unreadNotifs;
        if (badgeCb != null) badgeCb.run();
    }

    public static synchronized void stop() {
        for (Reg g : REGS) {
            if (g.listener instanceof ValueEventListener) g.ref.removeEventListener((ValueEventListener) g.listener);
            else if (g.listener instanceof ChildEventListener) g.ref.removeEventListener((ChildEventListener) g.listener);
        }
        REGS.clear();
        started = null;
        updatesCount = 0;
        pendingRequests = 0;
        unreadNotifs = 0;
    }

    public static synchronized void start(final Context ctx, final String uid) {
        if (uid.equals(started)) return;
        stop();
        started = uid;
        final Context app = ctx.getApplicationContext();

        // incoming calls
        DatabaseReference ring = Repo.ref("calls/ringing/" + uid);
        ValueEventListener rl = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                if (!s.exists()) return;
                final String callId = s.child("callId").getValue(String.class);
                final String from = s.child("from").getValue(String.class);
                final String type = s.child("type").getValue(String.class);
                Long at = s.child("at").getValue(Long.class);
                if (callId == null || from == null) return;
                if (at != null && Math.abs(Repo.now() - at) > 45000) {
                    s.getRef().removeValue();
                    return;
                }
                if (CallActivity.active || callId.equals(lastRingId)) return;
                lastRingId = callId;
                final String t = type == null ? "audio" : type;
                if (AetherApp.foreground()) {
                    Intent i = CallActivity.incoming(app, callId, from, t);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    app.startActivity(i);
                } else {
                    Repo.loadUser(from, new Repo.Result<User>() {
                        @Override
                        public void got(User u) {
                            NotificationHelper.showIncomingCall(app, callId, from, t, u == null ? "Aether" : u.display());
                        }
                    });
                }
            }
        };
        ring.addValueEventListener(rl);
        add(ring, rl);

        // new messages
        final DatabaseReference uc = Repo.ref("userChats/" + uid);
        final boolean[] ready = {false};
        ChildEventListener cl = new Cel() {
            @Override
            public void onChildAdded(DataSnapshot s, String p) {
                handle(s);
            }

            @Override
            public void onChildChanged(DataSnapshot s, String p) {
                handle(s);
            }

            private void handle(DataSnapshot s) {
                final UserChat c = s.getValue(UserChat.class);
                if (c == null || c.otherUid == null) return;
                c.chatId = s.getKey();
                String me = Repo.me();
                if (me == null || me.equals(c.lastFrom) || c.unread <= 0) return;
                Repo.markDelivered(c.chatId, c.lastKey);
                if (!ready[0] || c.muted || c.hidden) return;
                if (c.chatId.equals(AetherApp.openChatId)) return;
                Repo.loadUser(c.otherUid, new Repo.Result<User>() {
                    @Override
                    public void got(User u) {
                        NotificationHelper.showMessage(app, c.chatId, c.otherUid, u == null ? "New message" : u.display(),
                                c.lastText == null ? "" : c.lastText);
                    }
                });
            }
        };
        uc.addChildEventListener(cl);
        add(uc, cl);
        uc.addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                ready[0] = true;
            }
        });

        // friend requests
        DatabaseReference fr = Repo.ref("friendRequests/" + uid);
        final boolean[] frReady = {false};
        ChildEventListener fl = new Cel() {
            @Override
            public void onChildAdded(DataSnapshot s, String p) {
                if (!frReady[0]) return;
                FriendRequest r = s.getValue(FriendRequest.class);
                if (r == null) return;
                NotificationHelper.showGeneric(app, ("fr" + s.getKey()).hashCode(), "Friend request",
                        "@" + (r.fromUsername == null ? "someone" : r.fromUsername) + " wants to connect");
            }
        };
        fr.addChildEventListener(fl);
        add(fr, fl);
        fr.addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                frReady[0] = true;
            }
        });
        ValueEventListener frCount = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                pendingRequests = (int) s.getChildrenCount();
                recount();
            }
        };
        fr.addValueEventListener(frCount);
        add(fr, frCount);

        // notifications feed (accepted/declined/missed call/security)
        DatabaseReference nf = Repo.ref("notifications/" + uid);
        final boolean[] nfReady = {false};
        ChildEventListener nl = new Cel() {
            @Override
            public void onChildAdded(DataSnapshot s, String p) {
                if (!nfReady[0]) return;
                Notif n = s.getValue(Notif.class);
                if (n == null) return;
                NotificationHelper.showGeneric(app, ("nf" + s.getKey()).hashCode(), "Aether", describe(n));
            }
        };
        nf.addChildEventListener(nl);
        add(nf, nl);
        nf.addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                nfReady[0] = true;
            }
        });
        ValueEventListener nfCount = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                int c = 0;
                for (DataSnapshot x : s.getChildren()) {
                    Boolean r = x.child("read").getValue(Boolean.class);
                    if (!Boolean.TRUE.equals(r)) c++;
                }
                unreadNotifs = c;
                recount();
            }
        };
        nf.addValueEventListener(nfCount);
        add(nf, nfCount);
    }

    public static String describe(Notif n) {
        String who = n.fromName == null || n.fromName.isEmpty() ? "Someone" : n.fromName;
        if ("request_accepted".equals(n.type)) return who + " accepted your friend request \uD83C\uDF89";
        if ("request_declined".equals(n.type)) return who + " declined your friend request";
        if ("missed_call".equals(n.type)) return "Missed " + (n.text == null ? "" : n.text + " ") + "call from " + who;
        if ("security".equals(n.type)) return n.text == null ? "Security alert" : n.text;
        return n.text == null ? "Update" : n.text;
    }
}
