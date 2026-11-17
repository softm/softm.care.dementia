package net.softm.care.dementia;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private WebView web;
    private ProgressBar progress;
    private LinearLayout error;
    private GeolocationPermissions.Callback locationCallback;
    private String locationOrigin;
    private static final String HOME = "https://dementia.designboard.net/";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff6f8f7);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout toolbar = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText("치매안심"); title.setTextSize(20); title.setTextColor(0xff145b4f);
        title.setPadding(20, 16, 8, 16);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button home = new Button(this); home.setText("홈");
        home.setOnClickListener(v -> web.loadUrl(HOME)); toolbar.addView(home);
        Button refresh = new Button(this); refresh.setText("새로고침");
        refresh.setOnClickListener(v -> retry()); toolbar.addView(refresh);
        root.addView(toolbar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, new LinearLayout.LayoutParams(-1, 6));
        error = new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL);
        error.setPadding(24, 48, 24, 24);
        TextView message = new TextView(this);
        message.setText("페이지에 연결할 수 없습니다.\n인터넷 연결을 확인한 뒤 다시 시도해 주세요.");
        message.setTextSize(18); error.addView(message);
        Button retry = new Button(this); retry.setText("다시 시도");
        retry.setOnClickListener(v -> retry()); error.addView(retry);
        root.addView(error); error.setVisibility(View.GONE);
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return route(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return route(Uri.parse(url)); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                error.setVisibility(View.GONE); web.setVisibility(View.VISIBLE); progress.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError failure) {
                if (request.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showError();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value); if (value == 100) progress.setVisibility(View.GONE);
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!trusted(Uri.parse(origin))) { callback.invoke(origin, false, false); return; }
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false); return;
                }
                if (locationCallback != null) locationCallback.invoke(locationOrigin, false, false);
                locationCallback = callback; locationOrigin = origin;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
            }
            @Override public void onGeolocationPermissionsHidePrompt() { locationCallback = null; }
        });
        if (state == null || web.restoreState(state) == null) web.loadUrl(HOME);
    }
    private boolean trusted(Uri uri) {
        return "https".equals(uri.getScheme()) && "dementia.designboard.net".equals(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }
    private boolean route(Uri uri) {
        if (trusted(uri)) return false;
        String scheme = uri.getScheme();
        Intent intent;
        if ("tel".equals(scheme)) intent = new Intent(Intent.ACTION_DIAL, uri);
        else if ("https".equals(scheme) || "http".equals(scheme) || "mailto".equals(scheme) || "geo".equals(scheme)
                || "kakaomap".equals(scheme) || "nmap".equals(scheme) || "tmap".equals(scheme)) {
            intent = new Intent(Intent.ACTION_VIEW, uri); intent.addCategory(Intent.CATEGORY_BROWSABLE);
        } else { Toast.makeText(this, "지원하지 않는 링크입니다.", Toast.LENGTH_SHORT).show(); return true; }
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "연결할 앱이 설치되어 있지 않습니다.", Toast.LENGTH_LONG).show(); }
        return true;
    }
    private void showError() { error.setVisibility(View.VISIBLE); web.setVisibility(View.GONE); progress.setVisibility(View.GONE); }
    private void retry() { String url = web.getUrl(); web.loadUrl(url != null && trusted(Uri.parse(url)) ? url : HOME); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 10 && locationCallback != null) {
            boolean allowed = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            locationCallback.invoke(locationOrigin, allowed, false); locationCallback = null;
        }
    }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onSaveInstanceState(Bundle state) { web.saveState(state); super.onSaveInstanceState(state); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (locationCallback != null) locationCallback.invoke(locationOrigin, false, false);
        web.destroy(); super.onDestroy();
    }
}
