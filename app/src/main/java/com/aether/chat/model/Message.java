package com.aether.chat.model;

import com.google.firebase.database.Exclude;

import java.util.Map;

public class Message {
    public String id, type, from, to, text, mediaData, fileName, mimeType, replyTo, replyText, status;
    public long fileSize, createdAt, editedAt, duration;
    public boolean forwarded, edited, deletedForAll, hasMedia;
    public Map<String, Boolean> deletedFor;
    public Map<String, String> reactions;

    public Message() {}

    /** Copy without the (large) media payload, for the local cache. */
    @Exclude
    public Message lite() {
        Message m = new Message();
        m.id = id; m.type = type; m.from = from; m.to = to; m.text = text;
        m.fileName = fileName; m.mimeType = mimeType; m.replyTo = replyTo; m.replyText = replyText;
        m.status = status; m.fileSize = fileSize; m.createdAt = createdAt; m.editedAt = editedAt;
        m.duration = duration; m.forwarded = forwarded; m.edited = edited; m.deletedForAll = deletedForAll;
        m.deletedFor = deletedFor; m.reactions = reactions;
        m.hasMedia = mediaData != null || hasMedia;
        return m;
    }

    @Exclude
    public String preview() {
        if (deletedForAll) return "\uD83D\uDEAB Message deleted";
        if ("image".equals(type)) return "\uD83D\uDCF7 Photo";
        if ("file".equals(type)) return "\uD83D\uDCCE " + (fileName == null ? "File" : fileName);
        if ("voice".equals(type)) return "\uD83C\uDFA4 Voice message";
        String t = text == null ? "" : text;
        return t.length() > 80 ? t.substring(0, 80) : t;
    }
}
