package com.aether.chat.listeners;

import com.aether.chat.model.User;
import com.aether.chat.repository.CacheStore;
import com.aether.chat.repository.Repo;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/** Keeps live User objects for a set of uids; call stop() when the screen goes away. */
public class UserWatcher {
    public interface CB {
        void onUser(User u);
    }

    private final Map<String, ValueEventListener> ls = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();

    public void watch(final String uid, final CB cb) {
        if (uid == null || ls.containsKey(uid)) return;
        User c = CacheStore.user(uid);
        if (c != null) {
            users.put(uid, c);
            cb.onUser(c);
        }
        ValueEventListener l = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u == null) return;
                u.uid = uid;
                users.put(uid, u);
                CacheStore.putUser(u);
                cb.onUser(u);
            }
        };
        ls.put(uid, l);
        Repo.ref("users/" + uid).addValueEventListener(l);
    }

    public User get(String uid) {
        User u = users.get(uid);
        return u != null ? u : CacheStore.user(uid);
    }

    public void stop() {
        for (Map.Entry<String, ValueEventListener> e : ls.entrySet()) {
            Repo.ref("users/" + e.getKey()).removeEventListener(e.getValue());
        }
        ls.clear();
    }
}
