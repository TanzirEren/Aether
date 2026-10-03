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
import com.aether.chat.model.CallRecord;
import com.aether.chat.model.User;
import com.aether.chat.util.TimeFmt;
import com.aether.chat.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.VH> {
    public interface Listener {
        void onOpenChat(CallRecord r);

        void onCallAgain(CallRecord r);
    }

    private final List<CallRecord> items = new ArrayList<>();
    private final UserWatcher watcher;
    private final Listener listener;

    public CallLogAdapter(UserWatcher w, Listener l) {
        watcher = w;
        listener = l;
    }

    public void submit(List<CallRecord> l) {
        items.clear();
        items.addAll(l);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_call, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        final CallRecord r = items.get(pos);
        User u = watcher.get(r.other);
        String name = u == null ? "..." : u.display();
        h.tvName.setText(name);
        Ui.avatar(h.ivAvatar, name, u == null ? null : u.profileImg);
        String dir;
        boolean missed = "missed".equals(r.result) && "in".equals(r.direction);
        if (missed) dir = "\u2715 Missed";
        else if ("declined".equals(r.result)) dir = "\u2715 Declined";
        else if ("cancelled".equals(r.result)) dir = "\u2197 Cancelled";
        else if ("missed".equals(r.result)) dir = "\u2197 No answer";
        else if ("in".equals(r.direction)) dir = "\u2199 Incoming";
        else dir = "\u2197 Outgoing";
        String type = "video".equals(r.type) ? "\uD83C\uDFA5" : "\uD83D\uDCDE";
        String dur = r.duration > 0 ? " \u00B7 " + TimeFmt.duration(r.duration) : "";
        h.tvSub.setText(dir + "  " + type + "  " + TimeFmt.listTime(r.at) + " " + TimeFmt.clock(r.at) + dur);
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onOpenChat(r); }
        });
        h.btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onCallAgain(r); }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final TextView tvName, tvSub;
        final View btn;

        VH(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivAvatar);
            tvName = v.findViewById(R.id.tvName);
            tvSub = v.findViewById(R.id.tvSub);
            btn = v.findViewById(R.id.btnCallAgain);
        }
    }
}
