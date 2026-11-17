package net.softm.care.dementia;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;

/** 돌봄한눈과 같은 하단 네이티브 및 지연 전면 광고 구성을 관리한다. */
final class GoogleAds {
    private static final long INTERSTITIAL_FIRST_DELAY_MS = 75_000L;
    private static final long INTERSTITIAL_COOLDOWN_MS = 180_000L;
    private static final int INTERSTITIAL_MAX_PER_SESSION = 3;

    private final Activity activity;
    private final FrameLayout nativeAdContainer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long appStartedAt = System.currentTimeMillis();
    private NativeAd nativeAd;
    private InterstitialAd interstitialAd;
    private long lastInterstitialShownAt;
    private int interstitialShownCount;
    private boolean interstitialLoading;
    private boolean interstitialShowScheduled;
    private boolean initialized;
    private boolean destroyed;

    GoogleAds(Activity activity, LinearLayout root) {
        this.activity = activity;
        nativeAdContainer = new FrameLayout(activity);
        nativeAdContainer.setVisibility(View.GONE);
        root.addView(nativeAdContainer, new LinearLayout.LayoutParams(-1, -2));
    }

    void initialize() {
        MobileAds.initialize(activity, status -> {
            if (destroyed) return;
            initialized = true;
            loadNativeAd();
            loadInterstitialAd();
            scheduleInterstitialOpportunity();
        });
    }

    private void loadNativeAd() {
        if (destroyed || activity.isFinishing()) return;
        AdLoader loader = new AdLoader.Builder(activity, activity.getString(R.string.admob_native_ad_unit_id))
            .forNativeAd(ad -> {
                if (destroyed || activity.isFinishing() || activity.isDestroyed()) {
                    ad.destroy();
                    return;
                }
                if (nativeAd != null) nativeAd.destroy();
                nativeAd = ad;
                nativeAdContainer.removeAllViews();
                nativeAdContainer.addView(createNativeAdView(ad));
                nativeAdContainer.setVisibility(View.VISIBLE);
            })
            .withAdListener(new AdListener() {
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    nativeAdContainer.setVisibility(View.GONE);
                }
            })
            .withNativeAdOptions(new NativeAdOptions.Builder().build())
            .build();
        loader.loadAd(new AdRequest.Builder().build());
    }

    private NativeAdView createNativeAdView(NativeAd ad) {
        NativeAdView adView = new NativeAdView(activity);
        adView.setBackgroundColor(Color.WHITE);
        adView.setPadding(20, 12, 20, 12);

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView badge = new TextView(activity);
        badge.setText("광고");
        badge.setTextColor(0xff0f766e);
        badge.setTextSize(11);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackgroundColor(0xffe6f4ef);
        row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(30)));

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(14, 0, 14, 0);

        TextView headline = new TextView(activity);
        headline.setText(ad.getHeadline());
        headline.setTextColor(0xff1f2937);
        headline.setTextSize(15);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setMaxLines(2);
        adView.setHeadlineView(headline);
        text.addView(headline);

        TextView body = new TextView(activity);
        body.setText(ad.getBody());
        body.setTextColor(0xff667085);
        body.setTextSize(12);
        body.setMaxLines(1);
        body.setVisibility(ad.getBody() == null ? View.GONE : View.VISIBLE);
        adView.setBodyView(body);
        text.addView(body);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        Button callToAction = new Button(activity);
        callToAction.setText(ad.getCallToAction());
        callToAction.setTextSize(12);
        callToAction.setAllCaps(false);
        callToAction.setVisibility(ad.getCallToAction() == null ? View.GONE : View.VISIBLE);
        adView.setCallToActionView(callToAction);
        row.addView(callToAction, new LinearLayout.LayoutParams(-2, dp(44)));

        adView.addView(row, new NativeAdView.LayoutParams(-1, -2));
        adView.setNativeAd(ad);
        return adView;
    }

    private void loadInterstitialAd() {
        if (destroyed || interstitialLoading || interstitialAd != null) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, activity.getString(R.string.admob_interstitial_ad_unit_id),
            new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(InterstitialAd ad) {
                    interstitialLoading = false;
                    interstitialAd = ad;
                    ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override public void onAdDismissedFullScreenContent() {
                            interstitialAd = null;
                            lastInterstitialShownAt = System.currentTimeMillis();
                            loadInterstitialAd();
                            scheduleInterstitialOpportunity();
                        }

                        @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                            interstitialAd = null;
                            loadInterstitialAd();
                        }
                    });
                    scheduleInterstitialOpportunity();
                }

                @Override public void onAdFailedToLoad(LoadAdError error) {
                    interstitialLoading = false;
                    interstitialAd = null;
                    handler.postDelayed(() -> {
                        if (!destroyed) loadInterstitialAd();
                    }, INTERSTITIAL_COOLDOWN_MS);
                }
            });
    }

    private void scheduleInterstitialOpportunity() {
        if (destroyed || interstitialShowScheduled || interstitialShownCount >= INTERSTITIAL_MAX_PER_SESSION) return;
        long now = System.currentTimeMillis();
        long firstWait = Math.max(0L, appStartedAt + INTERSTITIAL_FIRST_DELAY_MS - now);
        long cooldownWait = lastInterstitialShownAt == 0L
            ? 0L : Math.max(0L, lastInterstitialShownAt + INTERSTITIAL_COOLDOWN_MS - now);
        interstitialShowScheduled = true;
        handler.postDelayed(() -> {
            interstitialShowScheduled = false;
            showInterstitialIfReady();
        }, Math.max(firstWait, cooldownWait));
    }

    private void showInterstitialIfReady() {
        if (destroyed || activity.isDestroyed() || activity.isFinishing()
                || interstitialShownCount >= INTERSTITIAL_MAX_PER_SESSION) return;
        if (interstitialAd == null) {
            loadInterstitialAd();
            return;
        }
        interstitialShownCount++;
        interstitialAd.show(activity);
    }

    boolean canRequestExitAd() {
        return initialized && !destroyed;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        interstitialAd = null;
        nativeAdContainer.setVisibility(View.GONE);
        nativeAdContainer.removeAllViews();
        if (nativeAd != null) {
            nativeAd.destroy();
            nativeAd = null;
        }
    }
}

