package com.aether.chat.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.aether.chat.R;
import com.aether.chat.model.User;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.ThemeUtil;
import com.aether.chat.util.Ui;

public class UserProfileActivity extends AppCompatActivity {
    public static Intent intent(Context c, String uid) {
        return new Intent(c, UserProfileActivity.class).putExtra("uid", uid);
    }

    @Override
    protected void onCreate(Bundle b) {
        ThemeUtil.apply(this);
        super.onCreate(b);
        setContentView(R.layout.activity_user_profile);
        final String uid = getIntent().getStringExtra("uid");
        if (uid == null) { finish(); return; }
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        final ImageView ivCover = findViewById(R.id.ivCover);
        final ImageView ivAvatar = findViewById(R.id.ivAvatar);
        final TextView tvName = findViewById(R.id.tvName);
        final TextView tvUser = findViewById(R.id.tvUsername);
        final TextView tvBio = findViewById(R.id.tvBio);
        Button btnMsg = findViewById(R.id.btnMessage);
        Button btnAudio = findViewById(R.id.btnAudio);
        Button btnVideo = findViewById(R.id.btnVideo);
        Button btnBlock = findViewById(R.id.btnBlock);
        boolean self = uid.equals(Repo.me());
        if (self) {
            btnMsg.setVisibility(android.view.View.GONE);
            btnAudio.setVisibility(android.view.View.GONE);
            btnVideo.setVisibility(android.view.View.GONE);
            btnBlock.setVisibility(android.view.View.GONE);
        }
        Repo.loadUser(uid, u -> {
            if (u == null) { Ui.toast(this, "User not found"); finish(); return; }
            tvName.setText(u.display());
            tvUser.setText(u.username == null ? "" : "@" + u.username);
            tvBio.setText(u.bio == null ? "" : u.bio);
            Ui.avatar(ivAvatar, u.display(), u.profileImg);
            Ui.cover(ivCover, u.coverImg);
        });
        btnMsg.setOnClickListener(v -> {
            startActivity(ChatActivity.intent(this, uid));
        });
        btnAudio.setOnClickListener(v -> startActivity(CallActivity.outgoing(this, uid, "audio")));
        btnVideo.setOnClickListener(v -> startActivity(CallActivity.outgoing(this, uid, "video")));
        btnBlock.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Block this user?")
                .setMessage("They won't be able to message or call you.")
                .setPositiveButton("Block", (d, w) -> Repo.setBlocked(uid, true, (ok, err) -> {
                    Ui.toast(this, ok ? "Blocked" : "Failed: " + err);
                    if (ok) finish();
                }))
                .setNegativeButton("Cancel", null).show());
    }
}
