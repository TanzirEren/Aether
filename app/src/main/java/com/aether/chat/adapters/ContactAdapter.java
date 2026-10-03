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
import com.aether.chat.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {
    public static class Item {
        public String uid, fallbackName, fallbackUsername;
        public boolean sent;

        public Item(String uid, String name, String username, boolean sent) {
            this.uid = uid;
            this.fallbackName = name;
            this.fallbackUsername = username;
            this.sent = sent;
        }
    }

    public interface Listener {
        void onChat(Item i);

        void onCall(Item i);

        void onOpen(Item i);

        void onCancel(Item i);
    }

    private final List<Item> items = new ArrayList<>();
    private final UserWatcher watcher;
    private final Listener listener;

    public ContactAdapter(UserWatcher w, Listener l) {
        watcher = w;
        listener = l;
    }

    public void submit(List<Item> l) {
        items.clear();
        items.addAll(l);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_contact, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        final Item it = items.get(pos);
        User u = watcher.get(it.uid);
        String name = u != null ? u.display() : (it.fallbackName == null ? "..." : it.fallbackName);
        String un = u != null && u.username != null ? u.username : it.fallbackUsername;
        h.tvName.setText(name);
        h.tvSub.setText(un == null ? "" : "@" + un);
        Ui.avatar(h.ivAvatar, name, u == null ? null : u.profileImg);
        h.dot.setVisibility(!it.sent && u != null && u.isOnline() ? View.VISIBLE : View.GONE);
        h.btnChat.setVisibility(it.sent ? View.GONE : View.VISIBLE);
        h.btnCall.setVisibility(it.sent ? View.GONE : View.VISIBLE);
        h.btnAction.setVisibility(it.sent ? View.VISIBLE : View.GONE);
        h.btnChat.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onChat(it); }
        });
        h.btnCall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onCall(it); }
        });
        h.btnAction.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onCancel(it); }
        });
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onOpen(it); }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final View dot, btnChat, btnCall;
        final TextView tvName, tvSub, btnAction;

        VH(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivAvatar);
            dot = v.findViewById(R.id.dotOnline);
            btnChat = v.findViewById(R.id.btnChat);
            btnCall = v.findViewById(R.id.btnCall);
            tvName = v.findViewById(R.id.tvName);
            tvSub = v.findViewById(R.id.tvSub);
            btnAction = v.findViewById(R.id.btnAction);
        }
    }
}
