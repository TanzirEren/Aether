package com.aether.chat.adapters;

import android.graphics.Bitmap;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.aether.chat.R;
import com.aether.chat.model.Message;
import com.aether.chat.util.ImageTool;
import com.aether.chat.util.TimeFmt;
import com.aether.chat.util.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
    public interface Listener {
        void onLong(Message m, View anchor);

        void onImage(Message m);

        void onFile(Message m);

        void onVoice(Message m);

        void onLink(String url);
    }

    private List<Message> items = new ArrayList<>();
    private final String me;
    private final Listener listener;
    private String playingId;
    private int playProgress;

    public MessageAdapter(String me, Listener l) {
        this.me = me;
        this.listener = l;
    }

    public List<Message> items() {
        return items;
    }

    public void submit(final List<Message> next) {
        final List<Message> old = items;
        DiffUtil.DiffResult r = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int a, int b) {
                return old.get(a).id != null && old.get(a).id.equals(next.get(b).id);
            }
            @Override public boolean areContentsTheSame(int a, int b) {
                Message x = old.get(a), y = next.get(b);
                return eq(x.status, y.status) && eq(x.text, y.text) && x.edited == y.edited
                        && x.deletedForAll == y.deletedForAll && x.createdAt == y.createdAt
                        && (x.mediaData == null) == (y.mediaData == null)
                        && String.valueOf(x.reactions).equals(String.valueOf(y.reactions));
            }
        });
        items = next;
        r.dispatchUpdatesTo(this);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public void setPlaying(String id) {
        String old = playingId;
        playingId = id;
        playProgress = 0;
        notifyById(old);
        notifyById(id);
    }

    public void setProgress(int p) {
        playProgress = p;
        notifyById(playingId);
    }

    private void notifyById(String id) {
        if (id == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).id)) {
                notifyItemChanged(i, "p");
                return;
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_message, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            Message m = items.get(pos);
            bindVoice(h, m);
            return;
        }
        super.onBindViewHolder(h, pos, payloads);
    }

    private void bindVoice(VH h, Message m) {
        boolean playing = m.id != null && m.id.equals(playingId);
        h.ivPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        h.pbVoice.setProgress(playing ? playProgress : 0);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH h, int pos) {
        final Message m = items.get(pos);
        boolean mine = me.equals(m.from);
        boolean system = "system".equals(m.type);
        boolean showDate = pos == 0 || !TimeFmt.sameDay(items.get(pos - 1).createdAt, m.createdAt);
        h.tvDate.setVisibility(showDate ? View.VISIBLE : View.GONE);
        if (showDate) h.tvDate.setText(TimeFmt.dayLabel(m.createdAt));

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) h.bubble.getLayoutParams();
        int pad = Ui.dp(h.row.getContext(), 56);
        if (system) {
            h.row.setGravity(Gravity.CENTER_HORIZONTAL);
            h.row.setPadding(0, 0, 0, 0);
            h.bubble.setBackgroundResource(R.drawable.bg_chip);
        } else if (mine) {
            h.row.setGravity(Gravity.END);
            h.row.setPadding(pad, 0, 0, 0);
            h.bubble.setBackgroundResource(R.drawable.bg_bubble_sent);
        } else {
            h.row.setGravity(Gravity.START);
            h.row.setPadding(0, 0, pad, 0);
            h.bubble.setBackgroundResource(R.drawable.bg_bubble_recv);
        }
        h.bubble.setLayoutParams(lp);

        h.tvForwarded.setVisibility(m.forwarded ? View.VISIBLE : View.GONE);
        if (m.replyText != null && !m.replyText.isEmpty()) {
            h.replyBox.setVisibility(View.VISIBLE);
            h.tvReply.setText(m.replyText);
        } else {
            h.replyBox.setVisibility(View.GONE);
        }
        h.ivMedia.setVisibility(View.GONE);
        h.fileRow.setVisibility(View.GONE);
        h.voiceRow.setVisibility(View.GONE);
        h.tvText.setVisibility(View.GONE);
        h.tvText.setOnClickListener(null);

        if (m.deletedForAll) {
            h.tvText.setVisibility(View.VISIBLE);
            h.tvText.setText("\uD83D\uDEAB This message was deleted");
            h.tvText.setAlpha(0.6f);
        } else {
            h.tvText.setAlpha(1f);
            String type = m.type == null ? "text" : m.type;
            if ("image".equals(type)) {
                h.ivMedia.setVisibility(View.VISIBLE);
                Bitmap b = Ui.decodeDataUrl(m.mediaData, 480);
                if (b != null) h.ivMedia.setImageBitmap(b);
                else h.ivMedia.setImageResource(R.drawable.ic_file);
                h.ivMedia.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { listener.onImage(m); }
                });
            } else if ("file".equals(type)) {
                h.fileRow.setVisibility(View.VISIBLE);
                h.tvFileName.setText(m.fileName == null ? "File" : m.fileName);
                h.tvFileSize.setText(ImageTool.formatSize(m.fileSize));
                h.fileRow.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { listener.onFile(m); }
                });
            } else if ("voice".equals(type)) {
                h.voiceRow.setVisibility(View.VISIBLE);
                h.tvVoiceDur.setText(TimeFmt.duration(m.duration));
                bindVoice(h, m);
                h.voiceRow.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { listener.onVoice(m); }
                });
            }
            if (m.text != null && !m.text.isEmpty()) {
                h.tvText.setVisibility(View.VISIBLE);
                linkify(h.tvText, m);
            }
        }

        h.tvEdited.setVisibility(m.edited && !m.deletedForAll ? View.VISIBLE : View.GONE);
        h.tvTime.setText(system ? "" : TimeFmt.clock(m.createdAt));
        if (mine && !system) {
            h.tvTick.setVisibility(View.VISIBLE);
            boolean read = "read".equals(m.status);
            boolean delivered = "delivered".equals(m.status);
            h.tvTick.setText(read || delivered ? "\u2713\u2713" : "\u2713");
            h.tvTick.setTextColor(read ? h.primary(h.itemView) : h.low(h.itemView));
        } else {
            h.tvTick.setVisibility(View.GONE);
        }

        if (m.reactions != null && !m.reactions.isEmpty()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String e : m.reactions.values()) {
                Integer c = counts.get(e);
                counts.put(e, c == null ? 1 : c + 1);
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                sb.append(e.getKey());
                if (e.getValue() > 1) sb.append(e.getValue());
                sb.append(' ');
            }
            h.tvReactions.setVisibility(View.VISIBLE);
            h.tvReactions.setText(sb.toString().trim());
        } else {
            h.tvReactions.setVisibility(View.GONE);
        }

        View.OnLongClickListener lc = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!"system".equals(m.type)) listener.onLong(m, h.bubble);
                return true;
            }
        };
        h.bubble.setOnLongClickListener(lc);
        h.tvText.setOnLongClickListener(lc);
        h.ivMedia.setOnLongClickListener(lc);
        h.fileRow.setOnLongClickListener(lc);
        h.voiceRow.setOnLongClickListener(lc);
    }

    private void linkify(TextView tv, Message m) {
        SpannableString ss = new SpannableString(m.text);
        Linkify.addLinks(ss, Linkify.WEB_URLS);
        for (URLSpan u : ss.getSpans(0, ss.length(), URLSpan.class)) {
            int s = ss.getSpanStart(u);
            int e = ss.getSpanEnd(u);
            final String url = u.getURL();
            ss.removeSpan(u);
            ss.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    listener.onLink(url);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(ds.linkColor);
                    ds.setUnderlineText(true);
                }
            }, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setText(ss);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvDate, tvForwarded, tvReply, tvFileName, tvFileSize, tvVoiceDur, tvText, tvEdited, tvTime, tvTick, tvReactions;
        final LinearLayout row, bubble, replyBox, fileRow, voiceRow;
        final ImageView ivMedia, ivPlay;
        final ProgressBar pbVoice;

        VH(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDate);
            tvForwarded = v.findViewById(R.id.tvForwarded);
            tvReply = v.findViewById(R.id.tvReply);
            tvFileName = v.findViewById(R.id.tvFileName);
            tvFileSize = v.findViewById(R.id.tvFileSize);
            tvVoiceDur = v.findViewById(R.id.tvVoiceDur);
            tvText = v.findViewById(R.id.tvText);
            tvEdited = v.findViewById(R.id.tvEdited);
            tvTime = v.findViewById(R.id.tvTime);
            tvTick = v.findViewById(R.id.tvTick);
            tvReactions = v.findViewById(R.id.tvReactions);
            row = v.findViewById(R.id.row);
            bubble = v.findViewById(R.id.bubble);
            replyBox = v.findViewById(R.id.replyBox);
            fileRow = v.findViewById(R.id.fileRow);
            voiceRow = v.findViewById(R.id.voiceRow);
            ivMedia = v.findViewById(R.id.ivMedia);
            ivPlay = v.findViewById(R.id.ivPlay);
            pbVoice = v.findViewById(R.id.pbVoice);
        }

        int primary(View v) { return resolve(v, R.attr.aPrimary); }

        int low(View v) { return resolve(v, R.attr.aTxtLow); }

        private int resolve(View v, int attr) {
            android.util.TypedValue tv = new android.util.TypedValue();
            v.getContext().getTheme().resolveAttribute(attr, tv, true);
            return tv.data;
        }
    }
}
