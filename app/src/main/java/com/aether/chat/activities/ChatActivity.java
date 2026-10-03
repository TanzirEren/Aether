package com.aether.chat.activities;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aether.chat.AetherApp;
import com.aether.chat.R;
import com.aether.chat.adapters.MessageAdapter;
import com.aether.chat.listeners.Cel;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.Message;
import com.aether.chat.model.User;
import com.aether.chat.notifications.NotificationHelper;
import com.aether.chat.repository.CacheStore;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Constants;
import com.aether.chat.util.ImageTool;
import com.aether.chat.util.Prefs;
import com.aether.chat.util.ThemeUtil;
import com.aether.chat.util.TimeFmt;
import com.aether.chat.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity implements MessageAdapter.Listener {
    public static Intent intent(Context c, String otherUid) {
        return new Intent(c, ChatActivity.class).putExtra("other", otherUid);
    }

    private String me, other, chatId;
    private User otherUser;
    private long clearedAt;
    private boolean otherTyping;
    private final Map<String, Message> all = new HashMap<>();
    private MessageAdapter adapter;
    private RecyclerView rv;
    private LinearLayoutManager lm;
    private EditText et;
    private ImageButton btnSend, btnMic;
    private TextView tvName, tvStatus, tvRec, tvReplyText;
    private ImageView ivAvatar;
    private View replyBar;
    private Message replyTo;
    private String editingId;
    private boolean visible;
    private boolean firstLoad = true;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Query msgQuery;
    private ChildEventListener msgListener;
    private ValueEventListener typingListener, otherListener, clearedListener;
    private DatabaseReference typingRef, otherRef, clearedRef;

    private MediaRecorder recorder;
    private File recFile;
    private long recStart;
    private boolean recording;
    private MediaPlayer player;
    private String playingId;
    private boolean typingSent;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) sendImage(uri); });
    private final ActivityResultLauncher<String> pickFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) sendFile(uri); });
    private final ActivityResultLauncher<String> permMic =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
                if (!ok) Ui.toast(this, "Microphone permission is needed for voice notes");
            });

    private final Runnable markRunnable = this::markReadNow;
    private final Runnable typingStop = () -> setTyping(false);
    private final Runnable recTick = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            long s = (SystemClock.elapsedRealtime() - recStart) / 1000;
            tvRec.setText("\u25CF Recording " + TimeFmt.duration(s) + "  \u2014 release to send, slide left to cancel");
            h.postDelayed(this, 400);
        }
    };
    private final Runnable playTick = new Runnable() {
        @Override
        public void run() {
            if (player != null && playingId != null) {
                try {
                    int d = Math.max(1, player.getDuration());
                    adapter.setProgress(player.getCurrentPosition() * 100 / d);
                } catch (Exception ignored) { }
                h.postDelayed(this, 250);
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        ThemeUtil.apply(this);
        super.onCreate(b);
        setContentView(R.layout.activity_chat);
        me = Repo.me();
        other = getIntent().getStringExtra("other");
        if (me == null || other == null) { finish(); return; }
        chatId = Repo.chatId(me, other);
        CacheStore.init(this, me);

        rv = findViewById(R.id.rvMessages);
        et = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnMic = findViewById(R.id.btnMic);
        tvName = findViewById(R.id.tvName);
        tvStatus = findViewById(R.id.tvStatus);
        tvRec = findViewById(R.id.tvRec);
        tvReplyText = findViewById(R.id.tvReplyText);
        ivAvatar = findViewById(R.id.ivAvatar);
        replyBar = findViewById(R.id.replyBar);

        adapter = new MessageAdapter(me, this);
        lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);
        rv.setItemAnimator(null);
        // keep latest message visible when the keyboard opens (adjustResize shrinks the list)
        rv.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (bt < ob) rv.postDelayed(this::scrollToEnd, 60);
        });

        for (Message m : CacheStore.messages(chatId)) all.put(m.id, m);
        refreshList();

        User cached = CacheStore.user(other);
        if (cached != null) bindOther(cached);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.headerInfo).setOnClickListener(v -> startActivity(UserProfileActivity.intent(this, other)));
        findViewById(R.id.btnAudio).setOnClickListener(v -> startActivity(CallActivity.outgoing(this, other, "audio")));
        findViewById(R.id.btnVideo).setOnClickListener(v -> startActivity(CallActivity.outgoing(this, other, "video")));
        findViewById(R.id.btnMenu).setOnClickListener(this::showMenu);
        findViewById(R.id.btnAttach).setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add(0, 1, 0, "Image");
            pm.getMenu().add(0, 2, 1, "Document");
            pm.setOnMenuItemClickListener(it -> {
                if (it.getItemId() == 1) pickImage.launch("image/*");
                else pickFile.launch("*/*");
                return true;
            });
            pm.show();
        });
        findViewById(R.id.btnReplyClose).setOnClickListener(v -> { clearReply(); cancelEdit(); });
        btnSend.setOnClickListener(v -> sendText());
        setupMic();

        et.setText(CacheStore.draft(chatId));
        toggleSendMic();
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) {
                toggleSendMic();
                if (s.length() > 0) {
                    if (!typingSent) setTyping(true);
                    h.removeCallbacks(typingStop);
                    h.postDelayed(typingStop, 3000);
                } else {
                    setTyping(false);
                }
            }
        });

        attachListeners();
    }

    private void toggleSendMic() {
        boolean has = et.getText().toString().trim().length() > 0;
        btnSend.setVisibility(has ? View.VISIBLE : View.GONE);
        btnMic.setVisibility(has ? View.GONE : View.VISIBLE);
    }

    private void setTyping(boolean on) {
        if (typingSent == on) return;
        typingSent = on;
        Repo.setTyping(chatId, on);
    }

    private void bindOther(User u) {
        otherUser = u;
        tvName.setText(u.display());
        Ui.avatar(ivAvatar, u.display(), u.profileImg);
        updateStatus();
    }

    private void updateStatus() {
        if (otherTyping) { tvStatus.setText("typing\u2026"); return; }
        if (otherUser == null || otherUser.presence == null) { tvStatus.setText(""); return; }
        if (otherUser.isOnline()) tvStatus.setText("online");
        else tvStatus.setText(TimeFmt.lastSeen(otherUser.presence.lastSeen));
    }

    private void attachListeners() {
        otherRef = Repo.ref("users/" + other);
        otherListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u == null) return;
                u.uid = other;
                CacheStore.putUser(u);
                bindOther(u);
            }
        };
        otherRef.addValueEventListener(otherListener);

        typingRef = Repo.ref("chats/" + chatId + "/meta/typing/" + other);
        typingListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                otherTyping = Boolean.TRUE.equals(s.getValue(Boolean.class));
                updateStatus();
            }
        };
        typingRef.addValueEventListener(typingListener);

        clearedRef = Repo.ref("userChats/" + me + "/" + chatId + "/clearedAt");
        clearedListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                Long v = s.getValue(Long.class);
                clearedAt = v == null ? 0 : v;
                refreshList();
            }
        };
        clearedRef.addValueEventListener(clearedListener);

        msgQuery = Repo.ref("chats/" + chatId + "/messages").orderByChild("createdAt").limitToLast(100);
        msgListener = new Cel() {
            @Override
            public void onChildAdded(DataSnapshot s, String p) { onMsg(s); }

            @Override
            public void onChildChanged(DataSnapshot s, String p) { onMsg(s); }

            @Override
            public void onChildRemoved(DataSnapshot s) {
                all.remove(s.getKey());
                CacheStore.removeMessage(chatId, s.getKey());
                refreshList();
            }
        };
        msgQuery.addChildEventListener(msgListener);
    }

    private void onMsg(DataSnapshot s) {
        Message m = s.getValue(Message.class);
        if (m == null) return;
        m.id = s.getKey();
        all.put(m.id, m);
        // receiver-side cache (PRD 2.2): persist as soon as it arrives
        CacheStore.putMessage(chatId, m);
        refreshList();
        if (me.equals(m.to) && !"read".equals(m.status) && !m.deletedForAll) scheduleMark();
    }

    private void scheduleMark() {
        h.removeCallbacks(markRunnable);
        h.postDelayed(markRunnable, 350);
    }

    private void markReadNow() {
        List<String> ids = new ArrayList<>();
        boolean receipts = Prefs.readReceipts(this);
        for (Message m : all.values()) {
            if (!me.equals(m.to)) continue;
            if (receipts && !"read".equals(m.status)) ids.add(m.id);
            else if (!receipts && "sent".equals(m.status)) ids.add(m.id);
        }
        if (!visible) {
            return;
        }
        Repo.markStatus(chatId, ids, receipts ? "read" : "delivered");
        Repo.markRead(chatId, other, receipts);
    }

    private void refreshList() {
        List<Message> list = new ArrayList<>();
        for (Message m : all.values()) {
            if (m.deletedFor != null && Boolean.TRUE.equals(m.deletedFor.get(me))) continue;
            if (m.createdAt > 0 && m.createdAt <= clearedAt) continue;
            list.add(m);
        }
        Collections.sort(list, new Comparator<Message>() {
            @Override
            public int compare(Message a, Message b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        boolean atBottom = firstLoad || lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 2;
        adapter.submit(list);
        if (atBottom) {
            rv.post(this::scrollToEnd);
        }
        if (!list.isEmpty()) firstLoad = false;
    }

    private void scrollToEnd() {
        int n = adapter.getItemCount();
        if (n > 0) rv.scrollToPosition(n - 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        AetherApp.openChatId = chatId;
        NotificationHelper.cancel(this, chatId.hashCode());
        scheduleMark();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        if (chatId != null && chatId.equals(AetherApp.openChatId)) AetherApp.openChatId = null;
        if (et != null) CacheStore.setDraft(chatId, editingId == null ? et.getText().toString() : "");
        setTyping(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        if (msgQuery != null && msgListener != null) msgQuery.removeEventListener(msgListener);
        if (typingRef != null && typingListener != null) typingRef.removeEventListener(typingListener);
        if (otherRef != null && otherListener != null) otherRef.removeEventListener(otherListener);
        if (clearedRef != null && clearedListener != null) clearedRef.removeEventListener(clearedListener);
        stopPlayer();
        if (recording) finishRecording(false);
    }

    // ---------------- sending ----------------
    private void send(Message m) {
        Repo.sendMessage(other, m, (ok, err) -> { if (!ok) Ui.toast(this, "Not sent: " + err); });
    }

    private void fillReply(Message m) {
        if (replyTo != null) {
            m.replyTo = replyTo.id;
            String who = me.equals(replyTo.from) ? "You" : (otherUser == null ? "" : otherUser.display());
            m.replyText = who + ": " + replyTo.preview();
        }
    }

    private void sendText() {
        String t = et.getText().toString().trim();
        if (t.isEmpty()) return;
        if (t.length() > 4000) t = t.substring(0, 4000);
        if (editingId != null) {
            Repo.editMessage(chatId, editingId, t, null);
            cancelEdit();
            et.setText("");
            return;
        }
        Message m = new Message();
        m.type = Constants.T_TEXT;
        m.text = t;
        fillReply(m);
        et.setText("");
        clearReply();
        send(m);
    }

    private void sendImage(final Uri uri) {
        Ui.toast(this, "Preparing photo\u2026");
        new Thread(() -> {
            try {
                final String data = ImageTool.chatImage(this, uri);
                runOnUiThread(() -> {
                    Message m = new Message();
                    m.type = Constants.T_IMAGE;
                    m.mediaData = data;
                    m.mimeType = "image/jpeg";
                    m.fileName = "photo.jpg";
                    fillReply(m);
                    clearReply();
                    send(m);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, "Could not read that image"));
            }
        }).start();
    }

    private void sendFile(final Uri uri) {
        new Thread(() -> {
            try {
                final byte[] bytes = ImageTool.readBytes(this, uri, Constants.MAX_FILE_BYTES);
                if (bytes.length > Constants.MAX_FILE_BYTES) {
                    runOnUiThread(() -> Ui.toast(this, Constants.TOO_BIG));
                    return;
                }
                String mime = getContentResolver().getType(uri);
                if (mime == null) mime = "application/octet-stream";
                final String name = ImageTool.displayName(this, uri);
                final String data = ImageTool.toDataUrl(mime, bytes);
                final String fm = mime;
                runOnUiThread(() -> {
                    Message m = new Message();
                    m.type = Constants.T_FILE;
                    m.mediaData = data;
                    m.fileName = name;
                    m.fileSize = bytes.length;
                    m.mimeType = fm;
                    fillReply(m);
                    clearReply();
                    send(m);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, "Could not read that file"));
            }
        }).start();
    }

    // ---------------- voice notes ----------------
    private void setupMic() {
        btnMic.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permMic.launch(Manifest.permission.RECORD_AUDIO);
                        return true;
                    }
                    startRecording();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (recording && e.getX() < -Ui.dp(this, 90)) finishRecording(false);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (recording) finishRecording(true);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (recording) finishRecording(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void startRecording() {
        try {
            recFile = new File(getCacheDir(), "rec_" + System.currentTimeMillis() + ".m4a");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(32000);
            recorder.setMaxDuration(Constants.MAX_VOICE_SEC * 1000);
            recorder.setOutputFile(recFile.getAbsolutePath());
            recorder.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finishRecording(true);
            });
            recorder.prepare();
            recorder.start();
            recording = true;
            recStart = SystemClock.elapsedRealtime();
            tvRec.setVisibility(View.VISIBLE);
            h.post(recTick);
        } catch (Exception ex) {
            recording = false;
            releaseRecorder();
            Ui.toast(this, "Could not start recording");
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    private void finishRecording(boolean send) {
        if (!recording) return;
        recording = false;
        tvRec.setVisibility(View.GONE);
        long sec = (SystemClock.elapsedRealtime() - recStart) / 1000;
        try { recorder.stop(); } catch (Exception ex) { send = false; }
        releaseRecorder();
        if (send && sec >= 1 && recFile != null && recFile.exists()) {
            try {
                byte[] bytes = ImageTool.readBytes(this, Uri.fromFile(recFile), Constants.MAX_FILE_BYTES);
                if (bytes.length > Constants.MAX_FILE_BYTES) {
                    Ui.toast(this, Constants.TOO_BIG);
                } else {
                    Message m = new Message();
                    m.type = Constants.T_VOICE;
                    m.mediaData = ImageTool.toDataUrl("audio/mp4", bytes);
                    m.mimeType = "audio/mp4";
                    m.duration = Math.min(sec, Constants.MAX_VOICE_SEC);
                    fillReply(m);
                    clearReply();
                    send(m);
                }
            } catch (Exception ex) {
                Ui.toast(this, "Could not send voice note");
            }
        } else if (send) {
            Ui.toast(this, "Hold the mic to record");
        }
        if (recFile != null) recFile.delete();
    }

    private void stopPlayer() {
        h.removeCallbacks(playTick);
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        playingId = null;
        if (adapter != null) adapter.setPlaying(null);
    }

    @Override
    public void onVoice(Message m) {
        if (m.id != null && m.id.equals(playingId)) { stopPlayer(); return; }
        if (m.mediaData == null) { Ui.toast(this, "Voice note is still loading"); return; }
        stopPlayer();
        try {
            File f = ImageTool.writeCacheFile(this, "voice_" + m.id + ".m4a", m.mediaData);
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.prepare();
            player.setOnCompletionListener(mp -> stopPlayer());
            player.start();
            playingId = m.id;
            adapter.setPlaying(m.id);
            h.post(playTick);
        } catch (Exception ex) {
            stopPlayer();
            Ui.toast(this, "Could not play voice note");
        }
    }

    // ---------------- message actions ----------------
    @Override
    public void onLong(final Message m, View anchor) {
        final List<String> opts = new ArrayList<>();
        final boolean mine = me.equals(m.from);
        if (!m.deletedForAll) {
            opts.add("React");
            opts.add("Reply");
            if (m.text != null && !m.text.isEmpty()) opts.add("Copy");
            opts.add("Forward");
            if (mine && Constants.T_TEXT.equals(m.type)) opts.add("Edit");
            if ("image".equals(m.type) || "file".equals(m.type)) opts.add("Share / Save");
        }
        opts.add("Delete for me");
        if (mine && !m.deletedForAll) opts.add("Delete for everyone");
        new AlertDialog.Builder(this).setItems(opts.toArray(new String[0]), (d, which) -> {
            String o = opts.get(which);
            switch (o) {
                case "React": pickReaction(m); break;
                case "Reply": setReply(m); break;
                case "Copy": copy(m.text); break;
                case "Forward": forward(m); break;
                case "Edit": startEdit(m); break;
                case "Share / Save": shareMedia(m); break;
                case "Delete for me": Repo.deleteForMe(chatId, m.id); break;
                case "Delete for everyone": confirmDeleteAll(m); break;
                default: break;
            }
        }).show();
    }

    private void pickReaction(final Message m) {
        final String[] em = {"\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F", "Remove"};
        new AlertDialog.Builder(this).setItems(em, (d, w) -> {
            if (w == em.length - 1) Repo.react(chatId, m.id, null);
            else Repo.react(chatId, m.id, em[w]);
        }).show();
    }

    private void copy(String t) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("message", t));
        Ui.toast(this, "Copied");
    }

    private void setReply(Message m) {
        cancelEdit();
        replyTo = m;
        replyBar.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvReplyTitle)).setText("Replying to " + (me.equals(m.from) ? "yourself" : (otherUser == null ? "" : otherUser.display())));
        tvReplyText.setText(m.preview());
        et.requestFocus();
    }

    private void clearReply() {
        replyTo = null;
        if (editingId == null) replyBar.setVisibility(View.GONE);
    }

    private void startEdit(Message m) {
        clearReply();
        editingId = m.id;
        replyBar.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvReplyTitle)).setText("Editing message");
        tvReplyText.setText(m.text);
        et.setText(m.text);
        et.setSelection(et.getText().length());
        et.requestFocus();
    }

    private void cancelEdit() {
        if (editingId != null) {
            editingId = null;
            et.setText("");
        }
        if (replyTo == null) replyBar.setVisibility(View.GONE);
    }

    private void confirmDeleteAll(final Message m) {
        new AlertDialog.Builder(this).setTitle("Delete for everyone?")
                .setMessage("This message will be removed for both of you.")
                .setPositiveButton("Delete", (d, w) -> Repo.deleteForAll(chatId, m.id))
                .setNegativeButton("Cancel", null).show();
    }

    private void forward(final Message m) {
        if (m.hasMedia && m.mediaData == null) { Ui.toast(this, "Media is still loading"); return; }
        Repo.loadFriends(friends -> {
            if (friends.isEmpty()) { Ui.toast(this, "No friends to forward to"); return; }
            final String[] names = new String[friends.size()];
            for (int i = 0; i < names.length; i++) names[i] = friends.get(i).display();
            final List<User> fl = friends;
            new AlertDialog.Builder(this).setTitle("Forward to").setItems(names, (d, w) -> {
                Message c = new Message();
                c.type = m.type; c.text = m.text; c.mediaData = m.mediaData; c.fileName = m.fileName;
                c.fileSize = m.fileSize; c.mimeType = m.mimeType; c.duration = m.duration; c.forwarded = true;
                Repo.sendMessage(fl.get(w).uid, c, (ok, err) -> Ui.toast(this, ok ? "Forwarded" : "Not sent: " + err));
            }).show();
        });
    }

    @Override
    public void onImage(Message m) {
        if (m.mediaData == null) { Ui.toast(this, "Image is still loading"); return; }
        try {
            File f = ImageTool.writeCacheFile(this, "img_" + m.id + ".jpg", m.mediaData);
            startActivity(new Intent(this, ImageViewerActivity.class).putExtra("path", f.getAbsolutePath()));
        } catch (Exception e) {
            Ui.toast(this, "Could not open image");
        }
    }

    @Override
    public void onFile(final Message m) {
        if (m.mediaData == null) { Ui.toast(this, "File is still loading"); return; }
        new AlertDialog.Builder(this).setTitle(m.fileName).setItems(new String[]{"Open", "Share / Save"}, (d, w) -> {
            if (w == 0) openMedia(m); else shareMedia(m);
        }).show();
    }

    private File mediaFile(Message m) throws Exception {
        String name = m.id + "_" + ImageTool.safeName(m.fileName == null ? "file" : m.fileName);
        return ImageTool.writeCacheFile(this, name, m.mediaData);
    }

    private void openMedia(Message m) {
        try {
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", mediaFile(m));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, m.mimeType == null ? "*/*" : m.mimeType);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "No app can open this file \u2014 try Share / Save");
        } catch (Exception e) {
            Ui.toast(this, "Could not open file");
        }
    }

    private void shareMedia(Message m) {
        if (m.mediaData == null) { Ui.toast(this, "Still loading"); return; }
        try {
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", mediaFile(m));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(m.mimeType == null ? "*/*" : m.mimeType);
            i.putExtra(Intent.EXTRA_STREAM, u);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share / Save"));
        } catch (Exception e) {
            Ui.toast(this, "Could not share file");
        }
    }

    @Override
    public void onLink(String url) {
        startActivity(BrowserActivity.intent(this, url));
    }

    // ---------------- header menu ----------------
    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "View profile");
        pm.getMenu().add(0, 2, 1, "Mute / unmute");
        pm.getMenu().add(0, 3, 2, "Clear local cache for this chat");
        pm.getMenu().add(0, 4, 3, "Block");
        pm.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1:
                    startActivity(UserProfileActivity.intent(this, other));
                    break;
                case 2:
                    Repo.ref("userChats/" + me + "/" + chatId + "/muted").addListenerForSingleValueEvent(new Vel() {
                        @Override
                        public void onDataChange(DataSnapshot s) {
                            boolean now = !Boolean.TRUE.equals(s.getValue(Boolean.class));
                            Repo.setChatFlag(chatId, "muted", now);
                            Ui.toast(ChatActivity.this, now ? "Muted" : "Unmuted");
                        }
                    });
                    break;
                case 3:
                    CacheStore.clearChat(chatId);
                    Ui.toast(this, "Local cache cleared for this chat");
                    break;
                case 4:
                    new AlertDialog.Builder(this).setTitle("Block this user?")
                            .setMessage("They won't be able to message or call you.")
                            .setPositiveButton("Block", (d, w) -> Repo.setBlocked(other, true, (ok, err) -> {
                                if (ok) finish(); else Ui.toast(this, err);
                            }))
                            .setNegativeButton("Cancel", null).show();
                    break;
                default:
                    break;
            }
            return true;
        });
        pm.show();
    }
}
