package com.aether.chat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aether.chat.R;
import com.aether.chat.listeners.RealtimeHub;
import com.aether.chat.listeners.UserWatcher;
import com.aether.chat.model.FriendRequest;
import com.aether.chat.model.Notif;
import com.aether.chat.model.User;
import com.aether.chat.util.TimeFmt;
import com.aether.chat.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class UpdatesAdapter extends RecyclerView.Adapter<UpdatesAdapter.VH> {
    public interface Listener {
        void onAccept(FriendRequest r);

        void onReject(FriendRequest r);
    }

    /** items are FriendRequest or Notif */
    private final List<Object> items = new ArrayList<>();
    private final UserWatcher watcher;
    private final Listener listener;

    public UpdatesAdapter(UserWatcher w, Listener l) {
        watcher = w;
        listener = l;
    }

    public void submit(List<Object> l) {
        items.clear();
        items.addAll(l);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_update, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Object o = items.get(pos);
        if (o instanceof FriendRequest) {
            final FriendRequest r = (FriendRequest) o;
            User u = watcher.get(r.fromUid);
            String name = u != null ? u.display() : (r.fromName == null ? "Someone" : r.fromName);
            Ui.avatar(h.ivAvatar, name, u == null ? null : u.profileImg);
            h.tvTitle.setText(name + "  @" + (r.fromUsername == null ? "" : r.fromUsername));
            String bio = u != null && u.bio != null && !u.bio.isEmpty() ? u.bio : "wants to connect on Aether";
            h.tvSub.setText(bio);
            h.actions.setVisibility(View.VISIBLE);
            h.btnAccept.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { listener.onAccept(r); }
            });
            h.btnReject.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { listener.onReject(r); }
            });
        } else {
            Notif n = (Notif) o;
            User u = n.fromUid == null ? null : watcher.get(n.fromUid);
            String name = u != null ? u.display() : (n.fromName == null ? "Aether" : n.fromName);
            Ui.avatar(h.ivAvatar, name, u == null ? null : u.profileImg);
            h.tvTitle.setText(RealtimeHub.describe(n));
            h.tvSub.setText(TimeFmt.listTime(n.createdAt));
            h.actions.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final TextView tvTitle, tvSub;
        final View actions;
        final Button btnAccept, btnReject;

        VH(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivAvatar);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSub = v.findViewById(R.id.tvSub);
            actions = v.findViewById(R.id.actions);
            btnAccept = v.findViewById(R.id.btnAccept);
            btnReject = v.findViewById(R.id.btnReject);
        }
    }
}
