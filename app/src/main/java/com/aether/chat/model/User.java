package com.aether.chat.model;

import com.google.firebase.database.Exclude;

public class User {
    public String uid, name, username, email, bio, profileImg, coverImg;
    public long createdAt;
    public Presence presence;

    public static class Presence {
        public String status;
        public long lastSeen;
        public Presence() {}
    }

    public User() {}

    @Exclude
    public boolean isOnline() {
        return presence != null && "online".equals(presence.status);
    }

    @Exclude
    public String display() {
        if (name != null && !name.isEmpty()) return name;
        return username != null ? username : "User";
    }
}
