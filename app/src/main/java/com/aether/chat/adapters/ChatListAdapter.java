package com.aether.chat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aether.chat.R;
import com.aether.chat.listeners.UserWatcher;
import com.aether.chat.model.User;
import com.aether.chat.model.UserChat;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.TimeFmt;
import com.aether.chat.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.VH> {
    public interface Listener {
        void onOpen(UserChat c);

        void onLongPress(UserChat c);
    }

    private final List<UserChat> items = new ArrayList<>();
    private final UserWatcher watcher;
    private final Listener listener;

    public ChatListAdapter(UserWatcher w, Listener l) {
        watcher = w;
        listener = l;
    }

    public void submit(List<UserChat> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_chat, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        final UserChat c = items.get(pos);
        User u = watcher.get(c.otherUid);
        h.tvName.setText(u == null ? "..." : u.display());
        Ui.avatar(h.ivAvatar, u == null ? "?" : u.display(), u == null ? null : u.profileImg);
        h.dot.setVisibility(u != null && u.isOnline() ? View.VISIBLE : View.GONE);
        h.tvPreview.setText(c.lastText == null ? "" : c.lastText);
        h.tvTime.setText(TimeFmt.listTime(c.lastAt));
        boolean mine = Repo.me() != null && Repo.me().equals(c.lastFrom);
        if (mine) {
            h.tvTick.setVisibility(View.VISIBLE);
            boolean read = "read".equals(c.lastStatus);
            h.tvTick.setText(read ? "\u2713\u2713" : "\u2713");
        } else {
            h.tvTick.setVisibility(View.GONE);
        }
        if (c.unread > 0) {
            h.tvBadge.setVisibility(View.VISIBLE);
            h.tvBadge.setText(String.valueOf(c.unread));
        } else {
            h.tvBadge.setVisibility(View.GONE);
        }
        h.ivPin.setVisibility(c.pinned ? View.VISIBLE : View.GONE);
        h.ivMute.setVisibility(c.muted ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpen(c);
            }
        });
        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onLongPress(c);
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivAvatar, ivPin, ivMute;
        final View dot;
        final TextView tvName, tvTick, tvPreview, tvTime, tvBadge;

        VH(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivAvatar);
            ivPin = v.findViewById(R.id.ivPin);
            ivMute = v.findViewById(R.id.ivMute);
            dot = v.findViewById(R.id.dotOnline);
            tvName = v.findViewById(R.id.tvName);
            tvTick = v.findViewById(R.id.tvTick);
            tvPreview = v.findViewById(R.id.tvPreview);
            tvTime = v.findViewById(R.id.tvTime);
            tvBadge = v.findViewById(R.id.tvBadge);
        }
    }
}
