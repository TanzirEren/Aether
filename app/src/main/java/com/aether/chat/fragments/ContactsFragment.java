package com.aether.chat.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aether.chat.R;
import com.aether.chat.activities.CallActivity;
import com.aether.chat.activities.ChatActivity;
import com.aether.chat.activities.UserProfileActivity;
import com.aether.chat.adapters.ContactAdapter;
import com.aether.chat.listeners.UserWatcher;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.User;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactsFragment extends Fragment {
    private UserWatcher watcher;
    private ContactAdapter adapter;
    private final List<ContactAdapter.Item> friends = new ArrayList<>();
    private final List<ContactAdapter.Item> sent = new ArrayList<>();
    private final Set<String> friendIds = new HashSet<>();
    private boolean showSent = false;
    private DatabaseReference cRef, sRef;
    private ValueEventListener cL, sL;
    private View tvEmpty, cardPreview;
    private TextView chipFriends, chipSent;
    private User found;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_contacts, c, false);
    }

    @Override
    public void onViewCreated(@NonNull final View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        watcher = new UserWatcher();
        tvEmpty = v.findViewById(R.id.tvEmpty);
        cardPreview = v.findViewById(R.id.cardPreview);
        chipFriends = v.findViewById(R.id.chipFriends);
        chipSent = v.findViewById(R.id.chipSent);
        RecyclerView rv = v.findViewById(R.id.rvContacts);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContactAdapter(watcher, new ContactAdapter.Listener() {
            @Override
            public void onChat(ContactAdapter.Item i) {
                startActivity(ChatActivity.intent(requireContext(), i.uid));
            }

            @Override
            public void onCall(final ContactAdapter.Item i) {
                new AlertDialog.Builder(requireContext()).setItems(new String[]{"Audio call", "Video call"}, (d, w) ->
                        startActivity(CallActivity.outgoing(requireContext(), i.uid, w == 0 ? "audio" : "video"))).show();
            }

            @Override
            public void onOpen(final ContactAdapter.Item i) {
                if (i.sent) { startActivity(UserProfileActivity.intent(requireContext(), i.uid)); return; }
                String[] opts = {"View profile", "Remove friend", "Block"};
                new AlertDialog.Builder(requireContext()).setItems(opts, (d, w) -> {
                    if (w == 0) startActivity(UserProfileActivity.intent(requireContext(), i.uid));
                    else if (w == 1) Repo.removeFriend(i.uid, (ok, err) -> Ui.toast(requireContext(), ok ? "Removed" : "Failed: " + err));
                    else Repo.setBlocked(i.uid, true, (ok, err) -> Ui.toast(requireContext(), ok ? "Blocked" : "Failed: " + err));
                }).show();
            }

            @Override
            public void onCancel(ContactAdapter.Item i) {
                Repo.cancelRequest(i.uid, (ok, err) -> Ui.toast(requireContext(), ok ? "Request cancelled" : "Failed: " + err));
            }
        });
        rv.setAdapter(adapter);

        final EditText et = v.findViewById(R.id.etUsername);
        v.findViewById(R.id.btnSearch).setOnClickListener(x -> search(et.getText().toString()));
        et.setOnEditorActionListener((tv, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { search(et.getText().toString()); return true; }
            return false;
        });
        chipFriends.setOnClickListener(x -> setSent(false));
        chipSent.setOnClickListener(x -> setSent(true));

        String me = Repo.me();
        cRef = Repo.ref("contacts/" + me);
        cL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                friends.clear();
                friendIds.clear();
                for (DataSnapshot c : s.getChildren()) {
                    friends.add(new ContactAdapter.Item(c.getKey(), null, null, false));
                    friendIds.add(c.getKey());
                    watcher.watch(c.getKey(), u -> adapter.notifyDataSetChanged());
                }
                refresh();
            }
        };
        cRef.addValueEventListener(cL);
        sRef = Repo.ref("sentRequests/" + me);
        sL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                sent.clear();
                for (DataSnapshot c : s.getChildren()) {
                    String st = c.child("status").getValue(String.class);
                    if (!"pending".equals(st)) continue;
                    sent.add(new ContactAdapter.Item(c.getKey(), c.child("toName").getValue(String.class),
                            c.child("toUsername").getValue(String.class), true));
                    watcher.watch(c.getKey(), u -> adapter.notifyDataSetChanged());
                }
                refresh();
            }
        };
        sRef.addValueEventListener(sL);
    }

    private void setSent(boolean s) {
        showSent = s;
        chipFriends.setBackgroundResource(s ? R.drawable.bg_chip : R.drawable.bg_chip_on);
        chipSent.setBackgroundResource(s ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        refresh();
    }

    private void refresh() {
        List<ContactAdapter.Item> l = showSent ? sent : friends;
        adapter.submit(l);
        tvEmpty.setVisibility(l.isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) tvEmpty).setText(showSent ? "No pending requests" : "No friends yet \u2014 search a @username above");
    }

    private void search(String q) {
        if (q.trim().isEmpty()) return;
        cardPreview.setVisibility(View.GONE);
        Repo.lookupUser(q, u -> {
            if (!isAdded() || getView() == null) return;
            if (u == null) { Ui.toast(requireContext(), "No user with that username"); return; }
            found = u;
            View v = getView();
            cardPreview.setVisibility(View.VISIBLE);
            ((TextView) v.findViewById(R.id.tvPreviewName)).setText(u.display());
            ((TextView) v.findViewById(R.id.tvPreviewSub)).setText("@" + u.username);
            ((TextView) v.findViewById(R.id.tvPreviewBio)).setText(u.bio == null ? "" : u.bio);
            Ui.avatar((ImageView) v.findViewById(R.id.ivPreview), u.display(), u.profileImg);
            Button btn = v.findViewById(R.id.btnPreviewAction);
            btn.setEnabled(true);
            if (u.uid.equals(Repo.me())) {
                btn.setText("That's you");
                btn.setEnabled(false);
            } else if (friendIds.contains(u.uid)) {
                btn.setText("Message");
                btn.setOnClickListener(x -> startActivity(ChatActivity.intent(requireContext(), u.uid)));
            } else {
                btn.setText("Send request");
                btn.setOnClickListener(x -> {
                    btn.setEnabled(false);
                    Repo.sendFriendRequest(u, (ok, err) -> {
                        Ui.toast(requireContext(), ok ? "Friend request sent" : "Failed: " + err);
                        if (ok) cardPreview.setVisibility(View.GONE);
                        else btn.setEnabled(true);
                    });
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cRef != null && cL != null) cRef.removeEventListener(cL);
        if (sRef != null && sL != null) sRef.removeEventListener(sL);
        if (watcher != null) watcher.stop();
    }
}
