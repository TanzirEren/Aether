package com.aether.chat.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.aether.chat.R;
import com.aether.chat.activities.ChatActivity;
import com.aether.chat.activities.MainActivity;
import com.aether.chat.activities.SettingsActivity;
import com.aether.chat.activities.UserProfileActivity;
import com.aether.chat.adapters.ChatListAdapter;
import com.aether.chat.listeners.UserWatcher;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.User;
import com.aether.chat.model.UserChat;
import com.aether.chat.repository.CacheStore;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {
    private final List<UserChat> all = new ArrayList<>();
    private final Set<String> blocked = new HashSet<>();
    private UserWatcher watcher;
    private ChatListAdapter adapter;
    private int mode = 0; // 0 all, 1 unread, 2 archived
    private String query = "";
    private View emptyView, cardResult;
    private TextView chipAll, chipUnread, chipArchived;
    private DatabaseReference ucRef, blockRef;
    private ValueEventListener ucListener, blockListener;
    private User searchHit;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_home, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        watcher = new UserWatcher();
        RecyclerView rv = v.findViewById(R.id.rvChats);
        emptyView = v.findViewById(R.id.emptyView);
        cardResult = v.findViewById(R.id.cardResult);
        chipAll = v.findViewById(R.id.chipAll);
        chipUnread = v.findViewById(R.id.chipUnread);
        chipArchived = v.findViewById(R.id.chipArchived);
        adapter = new ChatListAdapter(watcher, new ChatListAdapter.Listener() {
            @Override
            public void onOpen(UserChat c) {
                startActivity(ChatActivity.intent(requireContext(), c.otherUid));
            }

            @Override
            public void onLongPress(UserChat c) {
                menu(c);
            }
        });
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        v.findViewById(R.id.btnSettings).setOnClickListener(x -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        View.OnClickListener goContacts = x -> {
            if (getActivity() instanceof MainActivity) {
                ((com.google.android.material.bottomnavigation.BottomNavigationView) getActivity().findViewById(R.id.bottomNav))
                        .setSelectedItemId(R.id.nav_contacts);
            }
        };
        v.findViewById(R.id.btnEmptyAdd).setOnClickListener(goContacts);
        ((FloatingActionButton) v.findViewById(R.id.fabAdd)).setOnClickListener(goContacts);
        chipAll.setOnClickListener(x -> setMode(0));
        chipUnread.setOnClickListener(x -> setMode(1));
        chipArchived.setOnClickListener(x -> setMode(2));
        cardResult.setOnClickListener(x -> {
            if (searchHit != null) startActivity(UserProfileActivity.intent(requireContext(), searchHit.uid));
        });
        ((EditText) v.findViewById(R.id.etSearch)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                lookupIfHandle();
                render();
            }
        });

        // instant render from local cache, then RTDB catches up
        all.addAll(CacheStore.userChats());
        for (UserChat c : all) watch(c.otherUid);
        render();

        String me = Repo.me();
        ucRef = Repo.ref("userChats/" + me);
        ucListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                all.clear();
                for (DataSnapshot c : s.getChildren()) {
                    UserChat uc = c.getValue(UserChat.class);
                    if (uc == null || uc.otherUid == null) continue;
                    uc.chatId = c.getKey();
                    all.add(uc);
                    watch(uc.otherUid);
                }
                CacheStore.setUserChats(all);
                render();
            }
        };
        ucRef.addValueEventListener(ucListener);
        blockRef = Repo.ref("userPrivate/" + me + "/blocked");
        blockListener = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                blocked.clear();
                for (DataSnapshot c : s.getChildren()) blocked.add(c.getKey());
                render();
            }
        };
        blockRef.addValueEventListener(blockListener);
    }

    private void watch(String uid) {
        watcher.watch(uid, u -> {
            if (adapter != null) adapter.notifyDataSetChanged();
        });
    }

    private void setMode(int m) {
        mode = m;
        chipAll.setBackgroundResource(m == 0 ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        chipUnread.setBackgroundResource(m == 1 ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        chipArchived.setBackgroundResource(m == 2 ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        render();
    }

    private void lookupIfHandle() {
        searchHit = null;
        cardResult.setVisibility(View.GONE);
        if (query.startsWith("@") && query.length() >= 4) {
            final String q = query;
            Repo.lookupUser(q, u -> {
                if (u == null || !q.equals(query) || getView() == null) return;
                if (u.uid.equals(Repo.me())) return;
                searchHit = u;
                cardResult.setVisibility(View.VISIBLE);
                ((TextView) getView().findViewById(R.id.tvResultName)).setText(u.display() + "  @" + u.username);
                Ui.avatar((ImageView) getView().findViewById(R.id.ivResult), u.display(), u.profileImg);
            });
        }
    }

    private void render() {
        List<UserChat> out = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (UserChat c : all) {
            if (c.hidden || blocked.contains(c.otherUid)) continue;
            if (mode == 2 ? !c.archived : c.archived) continue;
            if (mode == 1 && c.unread <= 0) continue;
            if (!q.isEmpty() && !q.startsWith("@")) {
                User u = watcher.get(c.otherUid);
                String name = u == null ? "" : u.display().toLowerCase(Locale.ROOT);
                String last = c.lastText == null ? "" : c.lastText.toLowerCase(Locale.ROOT);
                if (!name.contains(q) && !last.contains(q)) continue;
            }
            out.add(c);
        }
        Collections.sort(out, new Comparator<UserChat>() {
            @Override
            public int compare(UserChat a, UserChat b) {
                if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                return Long.compare(b.lastAt, a.lastAt);
            }
        });
        adapter.submit(out);
        emptyView.setVisibility(out.isEmpty() && query.isEmpty() && mode == 0 ? View.VISIBLE : View.GONE);
    }

    private void menu(final UserChat c) {
        String[] opts = {
                c.pinned ? "Unpin" : "Pin",
                c.muted ? "Unmute" : "Mute",
                "Mark as read",
                c.archived ? "Unarchive" : "Archive",
                "Clear local cache for this chat",
                "Delete chat"
        };
        new AlertDialog.Builder(requireContext()).setItems(opts, (d, w) -> {
            switch (w) {
                case 0: Repo.setChatFlag(c.chatId, "pinned", !c.pinned); break;
                case 1: Repo.setChatFlag(c.chatId, "muted", !c.muted); break;
                case 2: Repo.setChatFlag(c.chatId, "unread", 0); break;
                case 3: Repo.setChatFlag(c.chatId, "archived", !c.archived); break;
                case 4:
                    CacheStore.clearChat(c.chatId);
                    Ui.toast(requireContext(), "Local cache cleared");
                    break;
                default:
                    new AlertDialog.Builder(requireContext()).setTitle("Delete chat?")
                            .setMessage("The conversation disappears from your list. Your friend keeps their copy.")
                            .setPositiveButton("Delete", (dd, ww) -> Repo.deleteChat(c.chatId, c.otherUid))
                            .setNegativeButton("Cancel", null).show();
                    break;
            }
        }).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ucRef != null && ucListener != null) ucRef.removeEventListener(ucListener);
        if (blockRef != null && blockListener != null) blockRef.removeEventListener(blockListener);
        if (watcher != null) watcher.stop();
    }
}
