package com.aether.chat.listeners;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

/** ValueEventListener with a no-op onCancelled. */
public abstract class Vel implements ValueEventListener {
    @Override
    public void onCancelled(DatabaseError error) {
    }
}
