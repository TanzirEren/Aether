package com.aether.chat.rtc;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/** SdpObserver with no-op defaults. */
public class SdpAdapter implements SdpObserver {
    @Override
    public void onCreateSuccess(SessionDescription sdp) {
    }

    @Override
    public void onSetSuccess() {
    }

    @Override
    public void onCreateFailure(String error) {
    }

    @Override
    public void onSetFailure(String error) {
    }
}
