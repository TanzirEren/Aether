package com.aether.chat.activities;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.aether.chat.R;
import com.aether.chat.model.User;
import com.aether.chat.notifications.NotificationHelper;
import com.aether.chat.repository.Repo;
import com.aether.chat.rtc.CallSignaling;
import com.aether.chat.rtc.WebRTCManager;
import com.aether.chat.util.TimeFmt;
import com.aether.chat.util.Ui;
import com.google.firebase.database.ServerValue;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallActivity extends AppCompatActivity implements WebRTCManager.Events, CallSignaling.Listener {
    public static boolean active;

    public static Intent outgoing(Context c, String other, String type) {
        return new Intent(c, CallActivity.class).putExtra("other", other).putExtra("type", type).putExtra("incoming", false);
    }

    public static Intent incoming(Context c, String callId, String other, String type) {
        return new Intent(c, CallActivity.class).putExtra("other", other).putExtra("type", type)
                .putExtra("incoming", true).putExtra("callId", callId);
    }

    private String me, other, type, callId;
    private boolean incoming, video, done, accepted, connected, offerHandled, owner;
    private long connectedAt;
    private final Handler h = new Handler(Looper.getMainLooper());
    private CallSignaling sig;
    private WebRTCManager rtc;
    private EglBase egl;
    private SurfaceViewRenderer remoteView, localView;
    private TextView tvStatus, tvName;
    private View infoGroup, controls, incomingGroup, glow;
    private ImageView ivAvatar;
    private ImageButton btnMute, btnCam;
    private ObjectAnimator glowAnim;
    private Ringtone ringtone;
    private AudioManager am;
    private int oldMode;
    private boolean oldSpeaker, muted, speaker, camOff;
    private String pendingOffer;
    private final List<IceCandidate> pendingCands = new ArrayList<>();
    private String otherName = "";

    private final ActivityResultLauncher<String[]> perms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), res -> {
                boolean ok = true;
                for (Boolean b : res.values()) if (!Boolean.TRUE.equals(b)) ok = false;
                if (ok) proceedAfterPermissions();
                else {
                    Ui.toast(this, "Microphone" + (video ? " and camera" : "") + " permission is required for calls");
                    endLocal(incoming ? "declined" : "cancelled");
                }
            });

    private final Runnable timeout = () -> {
        if (!connected && !incoming) endLocal("missed");
    };
    private final Runnable timer = new Runnable() {
        @Override
        public void run() {
            if (!connected || done) return;
            tvStatus.setText(TimeFmt.duration((SystemClock.elapsedRealtime() - connectedAt) / 1000));
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_call);
        me = Repo.me();
        other = getIntent().getStringExtra("other");
        type = getIntent().getStringExtra("type");
        incoming = getIntent().getBooleanExtra("incoming", false);
        callId = getIntent().getStringExtra("callId");
        if (me == null || other == null) { finish(); return; }
        if (active) { finish(); return; }
        active = true;
        owner = true;
        video = "video".equals(type);

        remoteView = findViewById(R.id.remoteView);
        localView = findViewById(R.id.localView);
        tvStatus = findViewById(R.id.tvStatus);
        tvName = findViewById(R.id.tvName);
        infoGroup = findViewById(R.id.infoGroup);
        controls = findViewById(R.id.controls);
        incomingGroup = findViewById(R.id.incomingGroup);
        glow = findViewById(R.id.glow);
        ivAvatar = findViewById(R.id.ivAvatar);
        btnMute = findViewById(R.id.btnMute);
        btnCam = findViewById(R.id.btnCam);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        glowAnim = ObjectAnimator.ofPropertyValuesHolder(glow,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.15f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.15f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.35f));
        glowAnim.setDuration(1600);
        glowAnim.setRepeatCount(ObjectAnimator.INFINITE);
        glowAnim.setRepeatMode(ObjectAnimator.REVERSE);
        glowAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        glowAnim.start();

        findViewById(R.id.btnFlip).setVisibility(video ? View.VISIBLE : View.GONE);
        btnCam.setVisibility(video ? View.VISIBLE : View.GONE);
        speaker = video;

        Repo.loadUser(other, u -> {
            if (u == null || done) return;
            otherName = u.display();
            tvName.setText(otherName);
            Ui.avatar(ivAvatar, otherName, u.profileImg);
        });

        findViewById(R.id.btnEnd).setOnClickListener(v -> endLocal(connected ? "ended" : (incoming ? "declined" : "cancelled")));
        findViewById(R.id.btnDecline).setOnClickListener(v -> endLocal("declined"));
        findViewById(R.id.btnAccept).setOnClickListener(v -> acceptIncoming());
        btnMute.setOnClickListener(v -> {
            muted = !muted;
            if (rtc != null) rtc.setMic(!muted);
            btnMute.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        });
        findViewById(R.id.btnSpeaker).setOnClickListener(v -> {
            speaker = !speaker;
            am.setSpeakerphoneOn(speaker);
            v.setAlpha(speaker ? 1f : 0.6f);
        });
        findViewById(R.id.btnFlip).setOnClickListener(v -> { if (rtc != null) rtc.switchCamera(); });
        btnCam.setOnClickListener(v -> {
            camOff = !camOff;
            if (rtc != null) rtc.setCamera(!camOff);
            btnCam.setAlpha(camOff ? 0.5f : 1f);
        });

        if (incoming) {
            if (callId == null) { finish(); return; }
            tvStatus.setText("Incoming " + (video ? "video" : "audio") + " call\u2026");
            incomingGroup.setVisibility(View.VISIBLE);
            NotificationHelper.cancel(this, 7001);
            try {
                ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                if (ringtone != null) ringtone.play();
            } catch (Exception ignored) { }
            sig = new CallSignaling(callId, false);
            sig.listen(this); // detects caller cancelling + receives the offer early
        } else {
            tvStatus.setText("Ringing\u2026");
            controls.setVisibility(View.VISIBLE);
            requestPermissionsThen();
        }
    }

    private String[] neededPerms() {
        return video ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}
                : new String[]{Manifest.permission.RECORD_AUDIO};
    }

    private boolean hasPerms() {
        for (String p : neededPerms()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestPermissionsThen() {
        if (hasPerms()) proceedAfterPermissions();
        else perms.launch(neededPerms());
    }

    private void proceedAfterPermissions() {
        if (incoming) startIncoming();
        else startOutgoing();
    }

    // ---------------- outgoing ----------------
    private void startOutgoing() {
        callId = Repo.ref("rtc").push().getKey();
        sig = new CallSignaling(callId, true);
        sig.listen(this);
        Map<String, Object> up = new HashMap<>();
        up.put("rtc/" + callId + "/status", "ringing");
        up.put("rtc/" + callId + "/from", me);
        up.put("rtc/" + callId + "/to", other);
        up.put("rtc/" + callId + "/type", type);
        Map<String, Object> ring = new HashMap<>();
        ring.put("callId", callId);
        ring.put("from", me);
        ring.put("type", type);
        ring.put("at", ServerValue.TIMESTAMP);
        up.put("calls/ringing/" + other, ring);
        com.aether.chat.AetherApp.db.getReference().updateChildren(up).addOnCompleteListener(t -> {
            if (!t.isSuccessful() && !done) {
                Ui.toast(this, "Could not start the call");
                endLocal("cancelled");
            }
        });
        initRtc();
        rtc.createOffer();
        h.postDelayed(timeout, 30000);
    }

    // ---------------- incoming ----------------
    private void acceptIncoming() {
        if (accepted) return;
        accepted = true;
        stopRing();
        incomingGroup.setVisibility(View.GONE);
        controls.setVisibility(View.VISIBLE);
        tvStatus.setText("Connecting\u2026");
        requestPermissionsThen();
    }

    private void startIncoming() {
        sig.setStatus("accepted");
        Repo.ref("calls/ringing/" + me).removeValue();
        initRtc();
        for (IceCandidate c : pendingCands) rtc.addRemoteCandidate(c);
        pendingCands.clear();
        if (pendingOffer != null && !offerHandled) {
            offerHandled = true;
            rtc.setRemoteOffer(pendingOffer);
        }
    }

    private void stopRing() {
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Exception ignored) { }
    }

    // ---------------- shared ----------------
    private void initRtc() {
        oldMode = am.getMode();
        oldSpeaker = am.isSpeakerphoneOn();
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        am.setSpeakerphoneOn(speaker);
        egl = EglBase.create();
        if (video) {
            remoteView.init(egl.getEglBaseContext(), null);
            localView.init(egl.getEglBaseContext(), null);
            localView.setMirror(true);
            localView.setZOrderMediaOverlay(true);
        }
        rtc = new WebRTCManager(this, egl, video, this);
        rtc.start();
        if (video && rtc.localVideo() != null) {
            localView.setVisibility(View.VISIBLE);
            rtc.localVideo().addSink(localView);
        }
    }

    // ---- CallSignaling.Listener (main thread: Firebase) ----
    @Override
    public void onOffer(String sdp) {
        if (rtc != null && accepted && !offerHandled) {
            offerHandled = true;
            rtc.setRemoteOffer(sdp);
        } else {
            pendingOffer = sdp;
        }
    }

    @Override
    public void onAnswer(String sdp) {
        if (rtc != null) rtc.setRemoteAnswer(sdp);
    }

    @Override
    public void onRemoteCandidate(IceCandidate c) {
        if (rtc != null) rtc.addRemoteCandidate(c);
        else pendingCands.add(c);
    }

    @Override
    public void onStatus(String status) {
        if (done) return;
        switch (status) {
            case "accepted":
                if (!incoming) tvStatus.setText("Connecting\u2026");
                h.removeCallbacks(timeout);
                break;
            case "ended":
            case "declined":
            case "missed":
            case "cancelled":
                endRemote(status);
                break;
            default:
                break;
        }
    }

    // ---- WebRTCManager.Events (WebRTC threads) ----
    @Override
    public void onLocalSdp(final SessionDescription sdp) {
        runOnUiThread(() -> {
            if (sig == null || done) return;
            if (incoming) sig.sendAnswer(sdp.description);
            else sig.sendOffer(sdp.description);
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate c) {
        runOnUiThread(() -> { if (sig != null && !done) sig.sendCandidate(c); });
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            if (connected || done) return;
            connected = true;
            connectedAt = SystemClock.elapsedRealtime();
            h.removeCallbacks(timeout);
            glowAnim.cancel();
            glow.setVisibility(View.INVISIBLE);
            h.post(timer);
        });
    }

    @Override
    public void onFailed() {
        runOnUiThread(() -> { if (!done) { Ui.toast(this, "Call connection failed"); endLocal("ended"); } });
    }

    @Override
    public void onRemoteVideo(final VideoTrack t) {
        runOnUiThread(() -> {
            if (done) return;
            remoteView.setVisibility(View.VISIBLE);
            t.addSink(remoteView);
            infoGroup.setVisibility(View.GONE);
        });
    }

    // ---------------- ending ----------------
    private void endLocal(String status) {
        if (done) return;
        done = true;
        if (sig != null) sig.setStatus(status);
        Repo.ref("calls/ringing/" + (incoming ? me : other)).removeValue();
        logMine(status, true);
        cleanup();
        finish();
    }

    private void endRemote(String status) {
        if (done) return;
        done = true;
        logMine(status, false);
        cleanup();
        finish();
    }

    private void logMine(String status, boolean local) {
        if (callId == null) return;
        long dur = connected ? (SystemClock.elapsedRealtime() - connectedAt) / 1000 : 0;
        String dir = incoming ? "in" : "out";
        String result;
        if (connected) result = "answered";
        else if ("declined".equals(status)) result = "declined";
        else if ("cancelled".equals(status)) result = incoming ? null : "cancelled";
        else if ("missed".equals(status)) result = incoming ? null : "missed";
        else result = incoming ? null : "cancelled";
        if (result != null) Repo.writeCallLog(me, callId, other, dir, type, result, dur);
        // caller writes the callee's missed entry + alert (callee may not be running the app)
        if (local && !incoming && !connected && ("missed".equals(status) || "cancelled".equals(status))) {
            Repo.writeCallLog(other, callId, me, "in", type, "missed", 0);
            User mu = Repo.meUser;
            Repo.pushNotification(other, "missed_call", me, mu == null ? "" : mu.display(), type);
        }
    }

    private void cleanup() {
        active = false;
        h.removeCallbacksAndMessages(null);
        stopRing();
        if (glowAnim != null) glowAnim.cancel();
        if (sig != null) sig.stop();
        if (rtc != null) rtc.close();
        try {
            if (video && egl != null) {
                remoteView.release();
                localView.release();
            }
            if (egl != null) egl.release();
        } catch (Exception ignored) { }
        egl = null;
        rtc = null;
        if (am != null) {
            try {
                am.setMode(AudioManager.MODE_NORMAL);
                am.setSpeakerphoneOn(false);
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void onBackPressed() {
        endLocal(connected ? "ended" : (incoming ? "declined" : "cancelled"));
    }

    @Override
    protected void onDestroy() {
        if (owner && !done) endLocal(connected ? "ended" : (incoming ? "declined" : "cancelled"));
        super.onDestroy();
    }
}
