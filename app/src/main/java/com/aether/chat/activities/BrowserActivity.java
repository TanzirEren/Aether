package com.aether.chat.activities;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.aether.chat.R;
import com.aether.chat.util.ThemeUtil;
import com.aether.chat.util.Ui;

public class BrowserActivity extends AppCompatActivity {
    private WebView web;
    private String current;

    public static Intent intent(Context c, String url) {
        return new Intent(c, BrowserActivity.class).putExtra("url", url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        ThemeUtil.apply(this);
        super.onCreate(b);
        setContentView(R.layout.activity_browser);
        String url = getIntent().getStringExtra("url");
        // intent validation: only http(s) is ever loaded
        if (url == null) { finish(); return; }
        if (url.startsWith("http://")) url = "https://" + url.substring(7);
        Uri u = Uri.parse(url);
        if (u.getScheme() == null || !u.getScheme().equals("https")) { finish(); return; }
        current = url;

        web = findViewById(R.id.webView);
        final TextView tvUrl = findViewById(R.id.tvUrl);
        final ProgressBar pb = findViewById(R.id.progress);
        tvUrl.setText(url);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sch = r.getUrl().getScheme();
                return sch == null || !sch.equals("https");
            }

            @Override
            public void onPageStarted(WebView v, String u2, Bitmap f) {
                current = u2;
                tvUrl.setText(u2);
                pb.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String u2) {
                pb.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView v, int code, String d, String failing) {
                if (failing != null && failing.equals(current)) {
                    v.loadData("<html><body style='font-family:sans-serif;padding:24px'><h3>This page could not load inside Aether</h3>"
                            + "<p>Use the share button to open it in your browser.</p></body></html>", "text/html", "UTF-8");
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                pb.setProgress(p);
            }
        });
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("link", current));
            Ui.toast(this, "Link copied");
        });
        findViewById(R.id.btnExternal).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(current)));
            } catch (Exception e) {
                Ui.toast(this, "No browser found");
            }
        });
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
