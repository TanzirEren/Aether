package com.aether.chat.model;

public class UserChat {
    public String chatId, otherUid, lastText, lastFrom, lastKey, lastStatus;
    public long lastAt, unread, clearedAt;
    public boolean pinned, muted, archived, hidden;

    public UserChat() {}
}
