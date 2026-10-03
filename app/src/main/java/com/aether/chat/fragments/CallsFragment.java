package com.aether.chat.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aether.chat.R;
import com.aether.chat.activities.CallActivity;
import com.aether.chat.activities.ChatActivity;
import com.aether.chat.adapters.CallLogAdapter;
import com.aether.chat.listeners.UserWatcher;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.CallRecord;
import com.aether.chat.model.User;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallsFragment extends Fragment {
    private UserWatcher watcher;
    private CallLogAdapter adapter;
    private Query q;
    private ValueEventListener l;
    private View tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_calls, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        watcher = new UserWatcher();
        tvEmpty = v.findViewById(R.id.tvEmpty);
        RecyclerView rv = v.findViewById(R.id.rvCalls);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CallLogAdapter(watcher, new CallLogAdapter.Listener() {
            @Override
            public void onOpenChat(CallRecord r) {
                startActivity(ChatActivity.intent(requireContext(), r.other));
            }

            @Override
            public void onCallAgain(CallRecord r) {
                startActivity(CallActivity.outgoing(requireContext(), r.other, r.type == null ? "audio" : r.type));
            }
        });
        rv.setAdapter(adapter);
        v.findViewById(R.id.fabCall).setOnClickListener(x -> pickContact());
        q = Repo.ref("callLog/" + Repo.me()).orderByChild("at").limitToLast(100);
        l = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                List<CallRecord> list = new ArrayList<>();
                for (DataSnapshot c : s.getChildren()) {
                    CallRecord r = c.getValue(CallRecord.class);
                    if (r == null || r.other == null) continue;
                    r.id = c.getKey();
                    list.add(r);
                    watcher.watch(r.other, u -> adapter.notifyDataSetChanged());
                }
                Collections.reverse(list);
                adapter.submit(list);
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }
        };
        q.addValueEventListener(l);
    }

    private void pickContact() {
        Repo.loadFriends(friends -> {
            if (!isAdded()) return;
            if (friends.isEmpty()) { Ui.toast(requireContext(), "Add a friend first"); return; }
            final List<User> fl = friends;
            String[] names = new String[fl.size()];
            for (int i = 0; i < names.length; i++) names[i] = fl.get(i).display();
            new AlertDialog.Builder(requireContext()).setTitle("Call who?").setItems(names, (d, w) -> {
                final User u = fl.get(w);
                new AlertDialog.Builder(requireContext()).setTitle(u.display())
                        .setItems(new String[]{"Audio call", "Video call"}, (d2, w2) ->
                                startActivity(CallActivity.outgoing(requireContext(), u.uid, w2 == 0 ? "audio" : "video")))
                        .show();
            }).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (q != null && l != null) q.removeEventListener(l);
        if (watcher != null) watcher.stop();
    }
}
