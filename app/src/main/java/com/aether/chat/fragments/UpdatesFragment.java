package com.aether.chat.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aether.chat.R;
import com.aether.chat.adapters.UpdatesAdapter;
import com.aether.chat.listeners.UserWatcher;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.FriendRequest;
import com.aether.chat.model.Notif;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdatesFragment extends Fragment {
    private UserWatcher watcher;
    private UpdatesAdapter adapter;
    private final List<FriendRequest> reqs = new ArrayList<>();
    private final List<Notif> notifs = new ArrayList<>();
    private DatabaseReference frRef, nfRef;
    private ValueEventListener frL, nfL;
    private View tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_updates, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        watcher = new UserWatcher();
        tvEmpty = v.findViewById(R.id.tvEmpty);
        RecyclerView rv = v.findViewById(R.id.rvUpdates);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UpdatesAdapter(watcher, new UpdatesAdapter.Listener() {
            @Override
            public void onAccept(FriendRequest r) {
                Repo.acceptRequest(r, (ok, err) -> Ui.toast(requireContext(), ok ? "You're now connected \uD83C\uDF89" : "Failed: " + err));
            }

            @Override
            public void onReject(FriendRequest r) {
                Repo.rejectRequest(r, (ok, err) -> { if (!ok) Ui.toast(requireContext(), "Failed: " + err); });
            }
        });
        rv.setAdapter(adapter);
        String me = Repo.me();
        frRef = Repo.ref("friendRequests/" + me);
        frL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                reqs.clear();
                for (DataSnapshot c : s.getChildren()) {
                    FriendRequest r = c.getValue(FriendRequest.class);
                    if (r == null) continue;
                    r.id = c.getKey();
                    if (r.fromUid == null) r.fromUid = c.getKey();
                    reqs.add(r);
                    watcher.watch(r.fromUid, u -> adapter.notifyDataSetChanged());
                }
                rebuild();
            }
        };
        frRef.addValueEventListener(frL);
        nfRef = Repo.ref("notifications/" + me);
        nfL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                notifs.clear();
                Map<String, Object> markRead = new HashMap<>();
                for (DataSnapshot c : s.getChildren()) {
                    Notif n = c.getValue(Notif.class);
                    if (n == null) continue;
                    n.id = c.getKey();
                    notifs.add(n);
                    if (n.fromUid != null) watcher.watch(n.fromUid, u -> adapter.notifyDataSetChanged());
                    if (!n.read) markRead.put(n.id + "/read", true);
                }
                rebuild();
                if (!markRead.isEmpty()) nfRef.updateChildren(markRead); // opening the tab clears the badge
            }
        };
        nfRef.addValueEventListener(nfL);
    }

    private void rebuild() {
        Collections.sort(notifs, new Comparator<Notif>() {
            @Override
            public int compare(Notif a, Notif b) {
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        List<Object> items = new ArrayList<>();
        items.addAll(reqs);
        items.addAll(notifs);
        adapter.submit(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (frRef != null && frL != null) frRef.removeEventListener(frL);
        if (nfRef != null && nfL != null) nfRef.removeEventListener(nfL);
        if (watcher != null) watcher.stop();
    }
}
