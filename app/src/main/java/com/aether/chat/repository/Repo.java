package com.aether.chat.repository;

import android.content.Context;
import android.os.Build;

import com.aether.chat.AetherApp;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.FriendRequest;
import com.aether.chat.model.Message;
import com.aether.chat.model.User;
import com.aether.chat.util.Constants;
import com.aether.chat.util.Prefs;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All RTDB reads/writes live here (PRD 14: no RTDB calls in adapters). */
public final class Repo {
    private Repo() {}

    public interface Done {
        void done(boolean ok, String err);
    }

    public interface Result<T> {
        void got(T value);
    }

    public static User meUser;
    public static long serverOffset = 0;
    private static ValueEventListener connListener, offsetListener, meListener;
    private static String presenceUid;

    // ---------- basics ----------
    public static String me() {
        if (AetherApp.auth == null) return null;
        FirebaseUser u = AetherApp.auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public static DatabaseReference ref(String path) {
        return AetherApp.db.getReference(path);
    }

    public static String chatId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    public static long now() {
        return System.currentTimeMillis() + serverOffset;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static void finish(com.google.android.gms.tasks.Task<Void> t, Done d) {
        if (d == null) return;
        if (t.isSuccessful()) d.done(true, null);
        else d.done(false, t.getException() == null ? "Failed" : t.getException().getMessage());
    }

    private static void update(Map<String, Object> up, final Done d) {
        AetherApp.db.getReference().updateChildren(up).addOnCompleteListener(
                new com.google.android.gms.tasks.OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(com.google.android.gms.tasks.Task<Void> t) {
                        finish(t, d);
                    }
                });
    }

    public static String friendly(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException)
            return "Wrong email/username or password";
        if (e instanceof FirebaseTooManyRequestsException) return "Too many attempts. Please try again later.";
        if (e instanceof FirebaseNetworkException) return "No internet connection";
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) return "That email is already registered";
            if ("ERROR_WEAK_PASSWORD".equals(code)) return "Password is too weak";
            if ("ERROR_INVALID_EMAIL".equals(code)) return "That email looks invalid";
            if ("ERROR_REQUIRES_RECENT_LOGIN".equals(code)) return "Please log in again, then retry";
        }
        return e == null || e.getMessage() == null ? "Something went wrong" : e.getMessage();
    }

    // ---------- presence ----------
    public static void startPresence() {
        final String uid = me();
        if (uid == null || AetherApp.db == null) return;
        if (uid.equals(presenceUid)) return;
        stopPresence();
        presenceUid = uid;
        offsetListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                Double d = s.getValue(Double.class);
                if (d != null) serverOffset = d.longValue();
            }
        };
        ref(".info/serverTimeOffset").addValueEventListener(offsetListener);
        connListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                if (Boolean.TRUE.equals(s.getValue(Boolean.class))) {
                    refreshPresence(AetherApp.foreground());
                }
            }
        };
        ref(".info/connected").addValueEventListener(connListener);
    }

    private static Map<String, Object> presenceMap(Context ctxForPrefs, boolean online) {
        boolean showSeen = true;
        if (ctxForPrefs != null) showSeen = Prefs.lastSeenVisible(ctxForPrefs);
        return map("status", online ? "online" : "offline", "lastSeen", showSeen ? ServerValue.TIMESTAMP : 0);
    }

    private static Context appCtx;

    public static void setContext(Context c) {
        appCtx = c.getApplicationContext();
    }

    public static void refreshPresence(boolean online) {
        String uid = me();
        if (uid == null || AetherApp.db == null) return;
        DatabaseReference pr = ref("users/" + uid + "/presence");
        pr.onDisconnect().setValue(presenceMap(appCtx, false));
        pr.setValue(presenceMap(appCtx, online));
    }

    public static void setOnline(boolean online) {
        String uid = me();
        if (uid == null || AetherApp.db == null || presenceUid == null) return;
        ref("users/" + uid + "/presence").setValue(presenceMap(appCtx, online));
    }

    public static void stopPresence() {
        if (connListener != null) ref(".info/connected").removeEventListener(connListener);
        if (offsetListener != null) ref(".info/serverTimeOffset").removeEventListener(offsetListener);
        connListener = null;
        offsetListener = null;
        presenceUid = null;
    }

    public static void watchMe() {
        String uid = me();
        if (uid == null) return;
        if (meListener != null) return;
        meListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u != null) {
                    u.uid = me();
                    meUser = u;
                    CacheStore.putUser(u);
                }
            }
        };
        ref("users/" + uid).addValueEventListener(meListener);
    }

    // ---------- auth ----------
    public static void login(String id, final String pw, final Done d) {
        String input = id.trim();
        if (input.contains("@")) {
            signIn(input, pw, d);
            return;
        }
        final String u = input.toLowerCase(Locale.ROOT);
        if (!u.matches("[a-z0-9_]{3,20}")) {
            d.done(false, "Invalid username");
            return;
        }
        ref("usernames/" + u).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot s) {
                String uid = s.getValue(String.class);
                if (uid == null) {
                    d.done(false, "No account with that username");
                    return;
                }
                ref("users/" + uid + "/email").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot s2) {
                        String em = s2.getValue(String.class);
                        if (em == null) d.done(false, "Account has no email");
                        else signIn(em, pw, d);
                    }

                    @Override
                    public void onCancelled(DatabaseError e) {
                        d.done(false, e.getMessage());
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError e) {
                d.done(false, e.getMessage());
            }
        });
    }

    private static void signIn(String email, String pw, final Done d) {
        AetherApp.auth.signInWithEmailAndPassword(email, pw).addOnCompleteListener(
                new com.google.android.gms.tasks.OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                    @Override
                    public void onComplete(com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult> t) {
                        if (t.isSuccessful()) {
                            AetherApp.sessionAuthed = true;
                            d.done(true, null);
                        } else {
                            d.done(false, friendly(t.getException()));
                        }
                    }
                });
    }

    public static void register(final String email, final String name, String username, final String pw, final Done d) {
        final String u = username.toLowerCase(Locale.ROOT);
        if (!u.matches("[a-z0-9_]{3,20}")) {
            d.done(false, "Username: 3-20 characters, a-z 0-9 _");
            return;
        }
        ref("usernames/" + u).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot s) {
                if (s.exists()) {
                    d.done(false, "That username is taken");
                    return;
                }
                AetherApp.auth.createUserWithEmailAndPassword(email, pw).addOnCompleteListener(
                        new com.google.android.gms.tasks.OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                            @Override
                            public void onComplete(com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult> t) {
                                if (!t.isSuccessful()) {
                                    d.done(false, friendly(t.getException()));
                                    return;
                                }
                                final FirebaseUser fu = t.getResult().getUser();
                                final String uid = fu.getUid();
                                ref("usernames/" + u).runTransaction(new Transaction.Handler() {
                                    @Override
                                    public Transaction.Result doTransaction(MutableData md) {
                                        if (md.getValue() != null) return Transaction.abort();
                                        md.setValue(uid);
                                        return Transaction.success(md);
                                    }

                                    @Override
                                    public void onComplete(DatabaseError e, boolean committed, DataSnapshot snap) {
                                        if (!committed) {
                                            fu.delete();
                                            d.done(false, "That username is taken");
                                            return;
                                        }
                                        Map<String, Object> m = map("name", name, "username", u, "email", email,
                                                "bio", "", "coverImg", Constants.COVERS[0], "createdAt", ServerValue.TIMESTAMP);
                                        ref("users/" + uid).setValue(m).addOnCompleteListener(
                                                new com.google.android.gms.tasks.OnCompleteListener<Void>() {
                                                    @Override
                                                    public void onComplete(com.google.android.gms.tasks.Task<Void> w) {
                                                        fu.sendEmailVerification();
                                                        AetherApp.sessionAuthed = true;
                                                        d.done(w.isSuccessful(), w.isSuccessful() ? null : "Could not create profile");
                                                    }
                                                });
                                    }
                                });
                            }
                        });
            }

            @Override
            public void onCancelled(DatabaseError e) {
                d.done(false, e.getMessage());
            }
        });
    }

    public static void resetPassword(String email, final Done d) {
        AetherApp.auth.sendPasswordResetEmail(email).addOnCompleteListener(
                new com.google.android.gms.tasks.OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(com.google.android.gms.tasks.Task<Void> t) {
                        d.done(t.isSuccessful(), t.isSuccessful() ? null : friendly(t.getException()));
                    }
                });
    }

    public static void logout() {
        String uid = me();
        if (uid != null && AetherApp.db != null) {
            try {
                ref("users/" + uid + "/presence").setValue(presenceMap(appCtx, false));
            } catch (Exception ignored) {
            }
        }
        stopPresence();
        if (meListener != null && uid != null) ref("users/" + uid).removeEventListener(meListener);
        meListener = null;
        meUser = null;
        AetherApp.sessionAuthed = false;
        AetherApp.auth.signOut();
    }

    /** Records this device; notifies the user if it is a NEW device and other devices are already known. */
    public static void recordLogin(Context c) {
        final String uid = me();
        if (uid == null) return;
        final String dev = Prefs.deviceId(c);
        ref("security/" + uid + "/devices").addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                if (s.hasChild(dev)) return;
                boolean others = s.exists();
                ref("security/" + uid + "/devices/" + dev).setValue(map("name", Build.MODEL, "at", ServerValue.TIMESTAMP));
                if (others) {
                    pushNotification(uid, "security", null, null, "New login on " + Build.MODEL);
                }
            }
        });
    }

    // ---------- users ----------
    public static void lookupUser(String username, final Result<User> r) {
        final String u = username.trim().toLowerCase(Locale.ROOT).replace("@", "");
        if (!u.matches("[a-z0-9_]{3,20}")) {
            r.got(null);
            return;
        }
        ref("usernames/" + u).addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                final String uid = s.getValue(String.class);
                if (uid == null) {
                    r.got(null);
                    return;
                }
                loadUser(uid, r);
            }
        });
    }

    public static void loadUser(final String uid, final Result<User> r) {
        ref("users/" + uid).addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u != null) {
                    u.uid = uid;
                    CacheStore.putUser(u);
                }
                r.got(u);
            }
        });
    }

    public static void loadFriends(final Result<List<User>> r) {
        String me = me();
        ref("contacts/" + me).addListenerForSingleValueEvent(new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                final List<User> out = new ArrayList<>();
                final int total = (int) s.getChildrenCount();
                if (total == 0) {
                    r.got(out);
                    return;
                }
                final int[] left = {total};
                for (DataSnapshot c : s.getChildren()) {
                    loadUser(c.getKey(), new Result<User>() {
                        @Override
                        public void got(User u) {
                            if (u != null) out.add(u);
                            left[0]--;
                            if (left[0] == 0) r.got(out);
                        }
                    });
                }
            }
        });
    }

    public static void updateProfile(Map<String, Object> fields, Done d) {
        String me = me();
        Map<String, Object> up = new HashMap<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) up.put("users/" + me + "/" + e.getKey(), e.getValue());
        update(up, d);
    }

    public static void pushNotification(String toUid, String type, String fromUid, String fromName, String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        if (fromUid != null) m.put("fromUid", fromUid);
        if (fromName != null) m.put("fromName", fromName);
        if (text != null) m.put("text", text);
        m.put("read", false);
        m.put("createdAt", ServerValue.TIMESTAMP);
        ref("notifications/" + toUid).push().setValue(m);
    }

    // ---------- friends ----------
    public static void sendFriendRequest(User target, Done d) {
        String me = me();
        User mu = meUser;
        Map<String, Object> up = new HashMap<>();
        String b = "friendRequests/" + target.uid + "/" + me;
        up.put(b + "/fromUid", me);
        up.put(b + "/fromName", mu != null ? mu.display() : "");
        up.put(b + "/fromUsername", mu != null && mu.username != null ? mu.username : "");
        up.put(b + "/status", "pending");
        up.put(b + "/createdAt", ServerValue.TIMESTAMP);
        String s = "sentRequests/" + me + "/" + target.uid;
        up.put(s + "/toUid", target.uid);
        up.put(s + "/toName", target.display());
        up.put(s + "/toUsername", target.username == null ? "" : target.username);
        up.put(s + "/status", "pending");
        up.put(s + "/createdAt", ServerValue.TIMESTAMP);
        update(up, d);
    }

    public static void cancelRequest(String toUid, Done d) {
        String me = me();
        Map<String, Object> up = new HashMap<>();
        up.put("friendRequests/" + toUid + "/" + me, null);
        up.put("sentRequests/" + me + "/" + toUid, null);
        update(up, d);
    }

    public static void rejectRequest(FriendRequest r, Done d) {
        String me = me();
        Map<String, Object> up = new HashMap<>();
        up.put("friendRequests/" + me + "/" + r.fromUid, null);
        up.put("sentRequests/" + r.fromUid + "/" + me + "/status", "declined");
        update(up, d);
        pushNotification(r.fromUid, "request_declined", me, meUser != null ? meUser.display() : "", null);
    }

    public static void acceptRequest(final FriendRequest r, final Done d) {
        final String me = me();
        final String other = r.fromUid;
        final String chat = chatId(me, other);
        Map<String, Object> up = new HashMap<>();
        up.put("friendRequests/" + me + "/" + other, null);
        up.put("sentRequests/" + other + "/" + me + "/status", "accepted");
        up.put("contacts/" + me + "/" + other + "/since", ServerValue.TIMESTAMP);
        up.put("contacts/" + other + "/" + me + "/since", ServerValue.TIMESTAMP);
        up.put("chats/" + chat + "/meta/participants", map(me, true, other, true));
        update(up, new Done() {
            @Override
            public void done(boolean ok, String err) {
                if (!ok) {
                    d.done(false, err);
                    return;
                }
                // step 2 (needs participants to exist for the rules): seed chat with a system message
                String text = "\u2728 You're connected on Aether";
                String key = ref("chats/" + chat + "/messages").push().getKey();
                Map<String, Object> msg = map("type", "system", "from", me, "to", other, "text", text,
                        "status", "sent", "createdAt", ServerValue.TIMESTAMP);
                Map<String, Object> up2 = new HashMap<>();
                up2.put("chats/" + chat + "/messages/" + key, msg);
                up2.put("chats/" + chat + "/meta/updatedAt", ServerValue.TIMESTAMP);
                for (String[] p : new String[][]{{me, other}, {other, me}}) {
                    String b = "userChats/" + p[0] + "/" + chat;
                    up2.put(b + "/otherUid", p[1]);
                    up2.put(b + "/lastText", text);
                    up2.put(b + "/lastAt", ServerValue.TIMESTAMP);
                    up2.put(b + "/lastFrom", me);
                    up2.put(b + "/lastKey", key);
                    up2.put(b + "/hidden", false);
                }
                up2.put("userChats/" + other + "/" + chat + "/unread", ServerValue.increment(1));
                update(up2, null);
                pushNotification(other, "request_accepted", me, meUser != null ? meUser.display() : "", null);
                d.done(true, null);
            }
        });
    }

    public static void removeFriend(String uid, Done d) {
        String me = me();
        Map<String, Object> up = new HashMap<>();
        up.put("contacts/" + me + "/" + uid, null);
        up.put("contacts/" + uid + "/" + me, null);
        up.put("userChats/" + me + "/" + chatId(me, uid) + "/hidden", true);
        update(up, d);
    }

    public static void setBlocked(String uid, boolean blocked, Done d) {
        String me = me();
        Map<String, Object> up = new HashMap<>();
        up.put("userPrivate/" + me + "/blocked/" + uid, blocked ? Boolean.TRUE : null);
        update(up, d);
    }

    // ---------- messages ----------
    public static void sendMessage(String other, Message m, Done d) {
        String me = me();
        String chat = chatId(me, other);
        String key = ref("chats/" + chat + "/messages").push().getKey();
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", m.type);
        msg.put("from", me);
        msg.put("to", other);
        if (m.text != null) msg.put("text", m.text);
        if (m.mediaData != null) msg.put("mediaData", m.mediaData);
        if (m.fileName != null) msg.put("fileName", m.fileName);
        if (m.fileSize > 0) msg.put("fileSize", m.fileSize);
        if (m.mimeType != null) msg.put("mimeType", m.mimeType);
        if (m.duration > 0) msg.put("duration", m.duration);
        if (m.replyTo != null) msg.put("replyTo", m.replyTo);
        if (m.replyText != null) msg.put("replyText", m.replyText);
        if (m.forwarded) msg.put("forwarded", true);
        msg.put("status", "sent");
        msg.put("createdAt", ServerValue.TIMESTAMP);
        String preview = m.preview();
        Map<String, Object> up = new HashMap<>();
        up.put("chats/" + chat + "/messages/" + key, msg);
        up.put("chats/" + chat + "/meta/lastMsg", map("type", m.type, "preview", preview, "from", me, "at", ServerValue.TIMESTAMP));
        up.put("chats/" + chat + "/meta/updatedAt", ServerValue.TIMESTAMP);
        for (String[] p : new String[][]{{me, other}, {other, me}}) {
            String b = "userChats/" + p[0] + "/" + chat;
            up.put(b + "/otherUid", p[1]);
            up.put(b + "/lastText", preview);
            up.put(b + "/lastAt", ServerValue.TIMESTAMP);
            up.put(b + "/lastFrom", me);
            up.put(b + "/lastKey", key);
            up.put(b + "/lastStatus", "sent");
            up.put(b + "/hidden", false);
        }
        up.put("userChats/" + other + "/" + chat + "/unread", ServerValue.increment(1));
        update(up, d);
    }

    public static void editMessage(String chat, String id, String text, Done d) {
        Map<String, Object> up = new HashMap<>();
        String b = "chats/" + chat + "/messages/" + id;
        up.put(b + "/text", text);
        up.put(b + "/edited", true);
        up.put(b + "/editedAt", ServerValue.TIMESTAMP);
        update(up, d);
    }

    public static void deleteForMe(String chat, String id) {
        ref("chats/" + chat + "/messages/" + id + "/deletedFor/" + me()).setValue(true);
    }

    public static void deleteForAll(String chat, String id) {
        Map<String, Object> up = new HashMap<>();
        String b = "chats/" + chat + "/messages/" + id;
        up.put(b + "/deletedForAll", true);
        up.put(b + "/text", "");
        up.put(b + "/mediaData", null);
        up.put(b + "/fileName", null);
        update(up, null);
    }

    public static void react(String chat, String id, String emoji) {
        ref("chats/" + chat + "/messages/" + id + "/reactions/" + me()).setValue(emoji);
    }

    public static void markStatus(String chat, List<String> ids, String status) {
        if (ids.isEmpty()) return;
        Map<String, Object> up = new HashMap<>();
        for (String id : ids) up.put("chats/" + chat + "/messages/" + id + "/status", status);
        update(up, null);
    }

    public static void markDelivered(final String chat, String key) {
        if (key == null) return;
        ref("chats/" + chat + "/messages/" + key + "/status").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData d) {
                if ("sent".equals(d.getValue(String.class))) d.setValue("delivered");
                return Transaction.success(d);
            }

            @Override
            public void onComplete(DatabaseError e, boolean c, DataSnapshot s) {
            }
        });
    }

    public static void markRead(String chat, String other, boolean receipt) {
        String me = me();
        Map<String, Object> up = new HashMap<>();
        up.put("userChats/" + me + "/" + chat + "/unread", 0);
        if (receipt) up.put("userChats/" + other + "/" + chat + "/lastStatus", "read");
        update(up, null);
    }

    public static void setChatFlag(String chat, String field, Object value) {
        ref("userChats/" + me() + "/" + chat + "/" + field).setValue(value);
    }

    public static void deleteChat(String chat, String other) {
        String me = me();
        Map<String, Object> up = new HashMap<>();
        up.put("userChats/" + me + "/" + chat + "/clearedAt", ServerValue.TIMESTAMP);
        up.put("userChats/" + me + "/" + chat + "/hidden", true);
        up.put("userChats/" + me + "/" + chat + "/unread", 0);
        update(up, null);
        CacheStore.clearChat(chat);
    }

    public static void setTyping(String chat, boolean typing) {
        String me = me();
        DatabaseReference r = ref("chats/" + chat + "/meta/typing/" + me);
        if (typing) {
            r.onDisconnect().removeValue();
            r.setValue(true);
        } else {
            r.removeValue();
        }
    }

    // ---------- calls ----------
    public static void writeCallLog(String uid, String callId, String other, String direction, String type, String result, long duration) {
        Map<String, Object> m = new HashMap<>();
        m.put("other", other);
        m.put("direction", direction);
        m.put("type", type);
        m.put("result", result);
        m.put("at", ServerValue.TIMESTAMP);
        m.put("duration", duration);
        ref("callLog/" + uid + "/" + callId).setValue(m);
    }

    // ---------- account ----------
    public static void deleteAccount(final Done d) {
        final FirebaseUser fu = AetherApp.auth.getCurrentUser();
        if (fu == null) {
            d.done(false, "Not signed in");
            return;
        }
        final String uid = fu.getUid();
        final String uname = meUser != null ? meUser.username : null;
        Map<String, Object> up = new HashMap<>();
        if (uname != null) up.put("usernames/" + uname, null);
        for (String n : new String[]{"users", "userPrivate", "userChats", "contacts", "friendRequests", "sentRequests", "notifications", "callLog", "security"}) {
            up.put(n + "/" + uid, null);
        }
        AetherApp.db.getReference().updateChildren(up).addOnCompleteListener(
                new com.google.android.gms.tasks.OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(com.google.android.gms.tasks.Task<Void> t) {
                        if (!t.isSuccessful()) {
                            d.done(false, "Could not remove data: " + (t.getException() == null ? "" : t.getException().getMessage()));
                            return;
                        }
                        fu.delete().addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(com.google.android.gms.tasks.Task<Void> t2) {
                                if (t2.isSuccessful()) {
                                    stopPresence();
                                    meListener = null;
                                    meUser = null;
                                    CacheStore.clearAll();
                                    d.done(true, null);
                                } else {
                                    d.done(false, friendly(t2.getException()));
                                }
                            }
                        });
                    }
                });
    }
}
