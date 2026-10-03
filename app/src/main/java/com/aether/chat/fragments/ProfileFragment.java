package com.aether.chat.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.aether.chat.R;
import com.aether.chat.activities.AuthActivity;
import com.aether.chat.activities.SettingsActivity;
import com.aether.chat.listeners.RealtimeHub;
import com.aether.chat.listeners.Vel;
import com.aether.chat.model.User;
import com.aether.chat.repository.CacheStore;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Constants;
import com.aether.chat.util.ImageTool;
import com.aether.chat.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {
    private DatabaseReference meRef, cRef;
    private ValueEventListener meL, cL;
    private User me;
    private String pendingTarget; // "avatar" or "cover"
    private ImageView ivCover, ivAvatar;
    private TextView tvName, tvUsername, tvBio, tvFriends, tvCache;

    private final ActivityResultLauncher<String> pick =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_profile, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        ivCover = v.findViewById(R.id.ivCover);
        ivAvatar = v.findViewById(R.id.ivAvatar);
        tvName = v.findViewById(R.id.tvName);
        tvUsername = v.findViewById(R.id.tvUsername);
        tvBio = v.findViewById(R.id.tvBio);
        tvFriends = v.findViewById(R.id.tvFriends);
        tvCache = v.findViewById(R.id.tvCache);
        tvCache.setText(ImageTool.formatSize(CacheStore.sizeBytes()));
        if (Repo.meUser != null) bind(Repo.meUser);

        v.findViewById(R.id.btnEdit).setOnClickListener(x -> edit());
        v.findViewById(R.id.btnSettings).setOnClickListener(x -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        v.findViewById(R.id.btnLogout).setOnClickListener(x -> new AlertDialog.Builder(requireContext())
                .setTitle("Log out?")
                .setPositiveButton("Log out", (d, w) -> {
                    RealtimeHub.stop();
                    Repo.logout();
                    Intent i = new Intent(requireContext(), AuthActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null).show());
        ivAvatar.setOnClickListener(x -> changeImage("avatar"));
        ivCover.setOnClickListener(x -> changeImage("cover"));

        String uid = Repo.me();
        meRef = Repo.ref("users/" + uid);
        meL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u == null) return;
                u.uid = Repo.me();
                bind(u);
            }
        };
        meRef.addValueEventListener(meL);
        cRef = Repo.ref("contacts/" + uid);
        cL = new Vel() {
            @Override
            public void onDataChange(DataSnapshot s) {
                tvFriends.setText(String.valueOf(s.getChildrenCount()));
            }
        };
        cRef.addValueEventListener(cL);
    }

    private void bind(User u) {
        me = u;
        tvName.setText(u.display());
        tvUsername.setText(u.username == null ? "" : "@" + u.username);
        tvBio.setText(u.bio == null || u.bio.isEmpty() ? "No bio yet" : u.bio);
        Ui.avatar(ivAvatar, u.display(), u.profileImg);
        Ui.cover(ivCover, u.coverImg);
    }

    private void changeImage(final String target) {
        String[] opts = target.equals("cover")
                ? new String[]{"Pick from gallery", "Aether cover 1", "Aether cover 2", "Aether cover 3", "Aether cover 4"}
                : new String[]{"Pick from gallery", "Remove photo"};
        new AlertDialog.Builder(requireContext()).setTitle(target.equals("cover") ? "Cover image" : "Profile photo")
                .setItems(opts, (d, w) -> {
                    Map<String, Object> f = new HashMap<>();
                    if (w == 0) {
                        pendingTarget = target;
                        pick.launch("image/*");
                        return;
                    }
                    if (target.equals("cover")) f.put("coverImg", Constants.COVERS[w - 1]);
                    else f.put("profileImg", null);
                    Repo.updateProfile(f, (ok, err) -> { if (!ok) Ui.toast(requireContext(), err); });
                }).show();
    }

    private void onPicked(final Uri uri) {
        if (uri == null || pendingTarget == null) return;
        final String target = pendingTarget;
        Ui.toast(requireContext(), "Updating\u2026");
        new Thread(() -> {
            try {
                final String data = target.equals("cover") ? ImageTool.coverImage(requireContext(), uri) : ImageTool.avatarImage(requireContext(), uri);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Map<String, Object> f = new HashMap<>();
                    f.put(target.equals("cover") ? "coverImg" : "profileImg", data);
                    Repo.updateProfile(f, (ok, err) -> { if (!ok && isAdded()) Ui.toast(requireContext(), err); });
                });
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> Ui.toast(requireContext(), "Could not read that image"));
            }
        }).start();
    }

    private void edit() {
        if (me == null) return;
        Context ctx = requireContext();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p / 2, p, 0);
        final EditText etName = new EditText(ctx);
        etName.setHint("Display name");
        etName.setText(me.name);
        final EditText etBio = new EditText(ctx);
        etBio.setHint("Bio (max 160)");
        etBio.setText(me.bio);
        etBio.setMaxLines(4);
        box.addView(etName);
        box.addView(etBio);
        new AlertDialog.Builder(ctx).setTitle("Edit profile").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String bio = etBio.getText().toString().trim();
                    if (n.isEmpty()) { Ui.toast(requireContext(), "Name can't be empty"); return; }
                    if (bio.length() > 160) bio = bio.substring(0, 160);
                    Map<String, Object> f = new HashMap<>();
                    f.put("name", n);
                    f.put("bio", bio);
                    Repo.updateProfile(f, (ok, err) -> Ui.toast(requireContext(), ok ? "Saved" : "Failed: " + err));
                })
                .setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (meRef != null && meL != null) meRef.removeEventListener(meL);
        if (cRef != null && cL != null) cRef.removeEventListener(cL);
    }
}
