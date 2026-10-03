package com.aether.chat.listeners;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

/** ChildEventListener with no-op defaults. */
public abstract class Cel implements ChildEventListener {
    @Override
    public void onChildAdded(DataSnapshot s, String prev) {
    }

    @Override
    public void onChildChanged(DataSnapshot s, String prev) {
    }

    @Override
    public void onChildRemoved(DataSnapshot s) {
    }

    @Override
    public void onChildMoved(DataSnapshot s, String prev) {
    }

    @Override
    public void onCancelled(DatabaseError error) {
    }
}
