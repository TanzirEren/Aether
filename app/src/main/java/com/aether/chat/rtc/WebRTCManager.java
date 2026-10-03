package com.aether.chat.rtc;

import android.content.Context;

import com.aether.chat.util.Constants;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thin wrapper around PeerConnection. Callbacks arrive on WebRTC threads - the Activity posts to the UI thread. */
public class WebRTCManager {
    public interface Events {
        void onLocalSdp(SessionDescription sdp);

        void onIceCandidate(IceCandidate c);

        void onConnected();

        void onFailed();

        void onRemoteVideo(VideoTrack t);
    }

    private static boolean initialized;

    private final Context ctx;
    private final EglBase egl;
    private final boolean video;
    private final Events ev;
    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private CameraVideoCapturer capturer;
    private SurfaceTextureHelper sth;
    private boolean remoteSet;
    private final List<IceCandidate> pending = new ArrayList<>();

    public WebRTCManager(Context ctx, EglBase egl, boolean video, Events ev) {
        this.ctx = ctx.getApplicationContext();
        this.egl = egl;
        this.video = video;
        this.ev = ev;
    }

    public VideoTrack localVideo() {
        return videoTrack;
    }

    public void start() {
        if (!initialized) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions());
            initialized = true;
        }
        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .createPeerConnectionFactory();

        List<PeerConnection.IceServer> servers = new ArrayList<>();
        for (String s : Constants.STUN) servers.add(PeerConnection.IceServer.builder(s).createIceServer());
        PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(servers);
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        pc = factory.createPeerConnection(cfg, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState s) { }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
                if (s == PeerConnection.IceConnectionState.CONNECTED || s == PeerConnection.IceConnectionState.COMPLETED) ev.onConnected();
                else if (s == PeerConnection.IceConnectionState.FAILED) ev.onFailed();
            }

            @Override public void onIceConnectionReceivingChange(boolean b) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }
            @Override public void onIceCandidate(IceCandidate c) { ev.onIceCandidate(c); }
            @Override public void onIceCandidatesRemoved(IceCandidate[] c) { }
            @Override public void onAddStream(MediaStream s) { }
            @Override public void onRemoveStream(MediaStream s) { }
            @Override public void onDataChannel(DataChannel d) { }
            @Override public void onRenegotiationNeeded() { }
            public void onAddTrack(RtpReceiver r, MediaStream[] s) { }

            public void onTrack(RtpTransceiver t) {
                MediaStreamTrack tr = t.getReceiver().track();
                if (tr instanceof VideoTrack) ev.onRemoteVideo((VideoTrack) tr);
            }
        });
        if (pc == null) {
            ev.onFailed();
            return;
        }

        audioSource = factory.createAudioSource(new MediaConstraints());
        audioTrack = factory.createAudioTrack("aether_a0", audioSource);
        pc.addTrack(audioTrack, Collections.singletonList("aether_stream"));

        if (video) {
            CameraEnumerator en = Camera2Enumerator.isSupported(ctx) ? new Camera2Enumerator(ctx) : new Camera1Enumerator(false);
            CameraVideoCapturer cap = null;
            for (String n : en.getDeviceNames()) {
                if (en.isFrontFacing(n)) {
                    cap = en.createCapturer(n, null);
                    if (cap != null) break;
                }
            }
            if (cap == null) {
                for (String n : en.getDeviceNames()) {
                    cap = en.createCapturer(n, null);
                    if (cap != null) break;
                }
            }
            if (cap != null) {
                capturer = cap;
                sth = SurfaceTextureHelper.create("aether_capture", egl.getEglBaseContext());
                videoSource = factory.createVideoSource(false);
                capturer.initialize(sth, ctx, videoSource.getCapturerObserver());
                capturer.startCapture(640, 480, 24);
                videoTrack = factory.createVideoTrack("aether_v0", videoSource);
                pc.addTrack(videoTrack, Collections.singletonList("aether_stream"));
            }
        }
    }

    private MediaConstraints offerConstraints() {
        MediaConstraints mc = new MediaConstraints();
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", video ? "true" : "false"));
        return mc;
    }

    public void createOffer() {
        if (pc == null) return;
        pc.createOffer(new SdpAdapter() {
            @Override
            public void onCreateSuccess(final SessionDescription sdp) {
                pc.setLocalDescription(new SdpAdapter() {
                    @Override
                    public void onSetSuccess() {
                        ev.onLocalSdp(sdp);
                    }
                }, sdp);
            }
        }, offerConstraints());
    }

    public void setRemoteOffer(String sdp) {
        if (pc == null) return;
        pc.setRemoteDescription(new SdpAdapter() {
            @Override
            public void onSetSuccess() {
                remoteReady();
                createAnswer();
            }
        }, new SessionDescription(SessionDescription.Type.OFFER, sdp));
    }

    private void createAnswer() {
        pc.createAnswer(new SdpAdapter() {
            @Override
            public void onCreateSuccess(final SessionDescription sdp) {
                pc.setLocalDescription(new SdpAdapter() {
                    @Override
                    public void onSetSuccess() {
                        ev.onLocalSdp(sdp);
                    }
                }, sdp);
            }
        }, offerConstraints());
    }

    public void setRemoteAnswer(String sdp) {
        if (pc == null) return;
        pc.setRemoteDescription(new SdpAdapter() {
            @Override
            public void onSetSuccess() {
                remoteReady();
            }
        }, new SessionDescription(SessionDescription.Type.ANSWER, sdp));
    }

    private synchronized void remoteReady() {
        remoteSet = true;
        for (IceCandidate c : pending) pc.addIceCandidate(c);
        pending.clear();
    }

    public synchronized void addRemoteCandidate(IceCandidate c) {
        if (pc == null) return;
        if (remoteSet) pc.addIceCandidate(c);
        else pending.add(c);
    }

    public void setMic(boolean enabled) {
        if (audioTrack != null) audioTrack.setEnabled(enabled);
    }

    public void setCamera(boolean enabled) {
        if (videoTrack != null) videoTrack.setEnabled(enabled);
    }

    public void switchCamera() {
        if (capturer != null) capturer.switchCamera(null);
    }

    public void close() {
        try {
            if (capturer != null) {
                try {
                    capturer.stopCapture();
                } catch (InterruptedException ignored) {
                }
                capturer.dispose();
                capturer = null;
            }
            if (videoSource != null) { videoSource.dispose(); videoSource = null; }
            if (sth != null) { sth.dispose(); sth = null; }
            if (audioSource != null) { audioSource.dispose(); audioSource = null; }
            if (pc != null) { pc.close(); pc.dispose(); pc = null; }
            if (factory != null) { factory.dispose(); factory = null; }
        } catch (Exception ignored) {
        }
    }
}
