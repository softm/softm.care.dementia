package net.softm.care.dementia;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.View;
import android.widget.*;
import softm.hannun.update.HannunInAppUpdateManager;

public class MainActivity extends Activity {
    private WebView web;
    private AlertDialog exitDialog;
    private GoogleAds googleAds;
    private ProgressBar progress;
    private LinearLayout error;
    private GeolocationPermissions.Callback locationCallback;
    private String locationOrigin;
    private boolean locationPermissionRequested;
    private HannunInAppUpdateManager appUpdater;
    private static final String HOME = "https://dementia.designboard.net/";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        appUpdater = new HannunInAppUpdateManager(this, null);
        locationPermissionRequested = state != null && state.getBoolean("locationPermissionRequested");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff6f8f7);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
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
        googleAds = new GoogleAds(this, root);
        setContentView(root);
        WebSettings settings = web.getSettings();
        settings.setUserAgentString(settings.getUserAgentString() + " DementiaAndroid/1 AdMobNative/1");
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
            @Override public void onPageFinished(WebView view, String url) {
                view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                progress.setVisibility(View.GONE);
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
                if (locationPermissionRequested) { callback.invoke(origin, false, false); return; }
                locationPermissionRequested = true;
                locationCallback = callback; locationOrigin = origin;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
            }
            @Override public void onGeolocationPermissionsHidePrompt() { }
        });
        googleAds.initialize();
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
    private void retry() {
        String url = web.getUrl();
        web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.loadUrl(url != null && trusted(Uri.parse(url)) ? url : HOME,
            java.util.Collections.singletonMap("Cache-Control", "no-cache"));
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 10 && locationCallback != null) {
            boolean allowed = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            GeolocationPermissions.Callback callback = locationCallback;
            locationCallback = null;
            callback.invoke(locationOrigin, allowed, false);
        }
    }
    @Override public void onBackPressed() {
        if (web.canGoBack()) { web.goBack(); return; }
        googleAds.showExitConfirmation();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("locationPermissionRequested", locationPermissionRequested); web.saveState(state); super.onSaveInstanceState(state); }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (appUpdater.onActivityResult(requestCode, resultCode)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override protected void onPause() { googleAds.onPause(); appUpdater.onPause(); web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); appUpdater.onResume(); if (web != null) web.onResume(); googleAds.onResume(); }
    @Override protected void onDestroy() {
        appUpdater.destroy();
        if (locationCallback != null) locationCallback.invoke(locationOrigin, false, false);
        if (exitDialog != null) { exitDialog.setOnDismissListener(null); exitDialog.dismiss(); }
        googleAds.destroy();
        web.destroy(); super.onDestroy();
    }
}
