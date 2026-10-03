package com.aether.chat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.aether.chat.R;
import com.aether.chat.repository.Repo;
import com.aether.chat.util.Prefs;
import com.aether.chat.util.ThemeUtil;
import com.aether.chat.util.Ui;

public class AuthActivity extends AppCompatActivity {
    private boolean registerMode = false;
    private boolean showPass = false;
    private EditText etId, etPass, etName, etUsername;
    private TextView tvTitle, tvError, tvStrength, tvToggle, tvForgot;
    private View groupRegister;
    private Button btnSubmit;
    private ProgressBar progress;
    private CheckBox cbRemember;
    private ImageButton btnEye;

    @Override
    protected void onCreate(Bundle b) {
        ThemeUtil.apply(this);
        super.onCreate(b);
        setContentView(R.layout.activity_auth);
        etId = findViewById(R.id.etId);
        etPass = findViewById(R.id.etPass);
        etName = findViewById(R.id.etName);
        etUsername = findViewById(R.id.etUsername);
        tvTitle = findViewById(R.id.tvTitle);
        tvError = findViewById(R.id.tvError);
        tvStrength = findViewById(R.id.tvStrength);
        tvToggle = findViewById(R.id.tvToggle);
        tvForgot = findViewById(R.id.tvForgot);
        groupRegister = findViewById(R.id.groupRegister);
        btnSubmit = findViewById(R.id.btnSubmit);
        progress = findViewById(R.id.progress);
        cbRemember = findViewById(R.id.cbRemember);
        btnEye = findViewById(R.id.btnEye);

        btnEye.setOnClickListener(v -> {
            showPass = !showPass;
            etPass.setInputType(InputType.TYPE_CLASS_TEXT | (showPass ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            etPass.setSelection(etPass.getText().length());
            btnEye.setImageResource(showPass ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        });
        etPass.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) { updateStrength(s.toString()); }
        });
        tvToggle.setOnClickListener(v -> setMode(!registerMode));
        tvForgot.setOnClickListener(v -> forgot());
        btnSubmit.setOnClickListener(v -> submit());
        setMode(false);
    }

    private void setMode(boolean reg) {
        registerMode = reg;
        groupRegister.setVisibility(reg ? View.VISIBLE : View.GONE);
        tvTitle.setText(reg ? "Create your account" : "Welcome back");
        etId.setHint(reg ? "Email" : "Username or email");
        btnSubmit.setText(reg ? "Create account" : "Sign in");
        tvToggle.setText(reg ? "Already have an account? Sign in" : "New here? Create an account");
        tvForgot.setVisibility(reg ? View.GONE : View.VISIBLE);
        tvStrength.setVisibility(reg ? View.VISIBLE : View.GONE);
        showError(null);
        updateStrength(etPass.getText().toString());
    }

    private void updateStrength(String p) {
        if (!registerMode) return;
        int score = 0;
        if (p.length() >= 6) score++;
        if (p.length() >= 10) score++;
        if (p.matches(".*[0-9].*") && p.matches(".*[A-Za-z].*")) score++;
        if (p.matches(".*[^A-Za-z0-9].*")) score++;
        String[] labels = {"Too short", "Weak", "Okay", "Good", "Strong"};
        tvStrength.setText("Password strength: " + labels[Math.min(score, 4)]);
    }

    private void showError(String e) {
        tvError.setVisibility(e == null ? View.GONE : View.VISIBLE);
        if (e != null) tvError.setText(e);
    }

    private void busy(boolean on) {
        btnSubmit.setEnabled(!on);
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void forgot() {
        String id = etId.getText().toString().trim();
        if (!id.contains("@")) {
            showError("Enter your email address in the first field, then tap Forgot password.");
            return;
        }
        busy(true);
        Repo.resetPassword(id, (ok, err) -> {
            busy(false);
            if (ok) Ui.toast(this, "Reset link sent to " + id);
            else showError(err);
        });
    }

    private void submit() {
        showError(null);
        String id = etId.getText().toString().trim();
        String pw = etPass.getText().toString();
        if (id.isEmpty()) { showError(registerMode ? "Enter your email" : "Enter your username or email"); return; }
        if (pw.length() < 6) { showError("Password must be at least 6 characters"); return; }
        Prefs.setRemember(this, cbRemember.isChecked());
        busy(true);
        if (registerMode) {
            String name = etName.getText().toString().trim();
            String un = etUsername.getText().toString().trim();
            if (!id.contains("@")) { busy(false); showError("Enter a valid email"); return; }
            if (name.isEmpty()) { busy(false); showError("Enter a display name"); return; }
            if (!un.toLowerCase().matches("[a-z0-9_]{3,20}")) { busy(false); showError("Username: 3-20 characters, a-z 0-9 _"); return; }
            Repo.register(id, name, un, pw, (ok, err) -> {
                busy(false);
                if (ok) { Ui.toast(this, "Welcome to Aether! Check your email to verify."); goMain(); }
                else showError(err);
            });
        } else {
            Repo.login(id, pw, (ok, err) -> {
                busy(false);
                if (ok) goMain();
                else showError(err);
            });
        }
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
