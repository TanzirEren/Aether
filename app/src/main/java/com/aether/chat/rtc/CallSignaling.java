package com.aether.chat.rtc;

import com.aether.chat.listeners.Cel;
import com.aether.chat.listeners.Vel;
import com.aether.chat.repository.Repo;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.IceCandidate;

import java.util.HashMap;
import java.util.Map;

/** WebRTC signaling over Firebase RTDB: rtc/{callId}/offer|answer|callerCandidates|calleeCandidates|status */
public class CallSignaling {
    public interface Listener {
        void onOffer(String sdp);

        void onAnswer(String sdp);

        void onRemoteCandidate(IceCandidate c);

        void onStatus(String status);
    }

    private final String callId;
    private final boolean caller;
    private DatabaseReference statusRef, sdpRef, candRef;
    private ValueEventListener statusL, sdpL;
    private ChildEventListener candL;
    private boolean sdpHandled;

    public CallSignaling(String callId, boolean caller) {
        this.callId = callId;
        this.caller = caller;
    }

    private String base() {
        return "rtc/" + callId;
    }

    public void listen(final Listener l) {
        statusRef = Repo.ref(base() + "/status");
        statusL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                String st = s.getValue(String.class);
                if (st != null) l.onStatus(st);
            }
        };
        statusRef.addValueEventListener(statusL);

        sdpRef = Repo.ref(base() + (caller ? "/answer" : "/offer"));
        sdpL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                String sdp = s.child("sdp").getValue(String.class);
                if (sdp == null || sdpHandled) return;
                sdpHandled = true;
                if (caller) l.onAnswer(sdp);
                else l.onOffer(sdp);
            }
        };
        sdpRef.addValueEventListener(sdpL);

        candRef = Repo.ref(base() + (caller ? "/calleeCandidates" : "/callerCandidates"));
        candL = new Cel() {
            @Override
            public void onChildAdded(DataSnapshot s, String p) {
                String mid = s.child("sdpMid").getValue(String.class);
                Long idx = s.child("sdpMLineIndex").getValue(Long.class);
                String cand = s.child("candidate").getValue(String.class);
                if (cand == null) return;
                l.onRemoteCandidate(new IceCandidate(mid, idx == null ? 0 : idx.intValue(), cand));
            }
        };
        candRef.addChildEventListener(candL);
    }

    public void sendOffer(String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("sdp", sdp);
        m.put("type", "offer");
        Repo.ref(base() + "/offer").setValue(m);
    }

    public void sendAnswer(String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("sdp", sdp);
        m.put("type", "answer");
        Repo.ref(base() + "/answer").setValue(m);
    }

    public void sendCandidate(IceCandidate c) {
        Map<String, Object> m = new HashMap<>();
        m.put("sdpMid", c.sdpMid);
        m.put("sdpMLineIndex", c.sdpMLineIndex);
        m.put("candidate", c.sdp);
        Repo.ref(base() + (caller ? "/callerCandidates" : "/calleeCandidates")).push().setValue(m);
    }

    public void setStatus(String s) {
        Repo.ref(base() + "/status").setValue(s);
    }

    public void stop() {
        if (statusRef != null && statusL != null) statusRef.removeEventListener(statusL);
        if (sdpRef != null && sdpL != null) sdpRef.removeEventListener(sdpL);
        if (candRef != null && candL != null) candRef.removeEventListener(candL);
        statusRef = null;
        sdpRef = null;
        candRef = null;
    }
}
