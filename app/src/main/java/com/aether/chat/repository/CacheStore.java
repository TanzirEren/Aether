package com.aether.chat.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.aether.chat.model.Message;
import com.aether.chat.model.User;
import com.aether.chat.model.UserChat;
import com.aether.chat.util.Constants;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local cache (PRD 2.2 / 6.1). RTDB is the source of truth; this only makes the next open instant.
 * Stored as JSON in internal storage. Media payloads are NOT cached here (RTDB offline persistence handles those).
 */
public final class CacheStore {
    private CacheStore() {}

    public static class Data {
        public Map<String, List<Message>> chats = new HashMap<>();
        public Map<String, User> users = new HashMap<>();
        public List<UserChat> userChats = new ArrayList<>();
        public Map<String, String> drafts = new HashMap<>();
        public long updatedAt;
    }

    private static final Gson GSON = new Gson();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Data data = new Data();
    private static String owner;
    private static Context app;
    private static boolean scheduled;

    private static File file() {
        return new File(app.getFilesDir(), "aether_cache_" + owner + ".json");
    }

    public static synchronized void init(Context c, String uid) {
        if (uid == null) return;
        if (uid.equals(owner) && app != null) return;
        app = c.getApplicationContext();
        owner = uid;
        data = new Data();
        File f = file();
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                Data d = GSON.fromJson(r, Data.class);
                if (d != null) data = d;
                if (data.chats == null) data.chats = new HashMap<>();
                if (data.users == null) data.users = new HashMap<>();
                if (data.userChats == null) data.userChats = new ArrayList<>();
                if (data.drafts == null) data.drafts = new HashMap<>();
            } catch (Exception e) {
                data = new Data();
            }
        }
    }

    private static void scheduleSave() {
        if (scheduled || app == null) return;
        scheduled = true;
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                scheduled = false;
                final String json;
                synchronized (CacheStore.class) {
                    data.updatedAt = System.currentTimeMillis();
                    json = GSON.toJson(data);
                }
                final File f = file();
                EXEC.execute(new Runnable() {
                    @Override
                    public void run() {
                        try (FileWriter w = new FileWriter(f)) {
                            w.write(json);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        }, 1500);
    }

    public static synchronized void putMessage(String chatId, Message m) {
        if (m == null || m.id == null) return;
        List<Message> list = data.chats.get(chatId);
        if (list == null) {
            list = new ArrayList<>();
            data.chats.put(chatId, list);
        }
        Message lite = m.lite();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (m.id.equals(list.get(i).id)) {
                list.set(i, lite);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(lite);
        Collections.sort(list, new Comparator<Message>() {
            @Override
            public int compare(Message a, Message b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        while (list.size() > Constants.CACHE_MESSAGES) list.remove(0);
        scheduleSave();
    }

    public static synchronized void removeMessage(String chatId, String id) {
        List<Message> list = data.chats.get(chatId);
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).id)) {
                list.remove(i);
                break;
            }
        }
        scheduleSave();
    }

    public static synchronized List<Message> messages(String chatId) {
        List<Message> l = data.chats.get(chatId);
        return l == null ? new ArrayList<Message>() : new ArrayList<>(l);
    }

    public static synchronized void putUser(User u) {
        if (u == null || u.uid == null) return;
        User c = new User();
        c.uid = u.uid; c.name = u.name; c.username = u.username; c.bio = u.bio;
        c.coverImg = (u.coverImg != null && u.coverImg.startsWith("http")) ? u.coverImg : null;
        c.profileImg = (u.profileImg != null && u.profileImg.length() < 60000) ? u.profileImg : null;
        c.presence = u.presence;
        data.users.put(u.uid, c);
        scheduleSave();
    }

    public static synchronized User user(String uid) {
        return data.users.get(uid);
    }

    public static synchronized void setUserChats(List<UserChat> l) {
        data.userChats = new ArrayList<>(l);
        scheduleSave();
    }

    public static synchronized List<UserChat> userChats() {
        return new ArrayList<>(data.userChats);
    }

    public static synchronized String draft(String chatId) {
        String d = data.drafts.get(chatId);
        return d == null ? "" : d;
    }

    public static synchronized void setDraft(String chatId, String text) {
        if (text == null || text.isEmpty()) data.drafts.remove(chatId);
        else data.drafts.put(chatId, text);
        scheduleSave();
    }

    public static synchronized long sizeBytes() {
        return GSON.toJson(data).length();
    }

    public static synchronized Map<String, Long> chatSizes() {
        Map<String, Long> r = new HashMap<>();
        for (Map.Entry<String, List<Message>> e : data.chats.entrySet()) {
            r.put(e.getKey(), (long) GSON.toJson(e.getValue()).length());
        }
        return r;
    }

    public static synchronized void clearChat(String chatId) {
        data.chats.remove(chatId);
        scheduleSave();
    }

    /** Clears the CACHE only. RTDB truth is never touched. */
    public static synchronized void clearAll() {
        data = new Data();
        scheduleSave();
    }
}
