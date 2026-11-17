package net.softm.care.dementia;

import android.app.Activity;
import android.widget.LinearLayout;
import softm.hannun.ads.HannunAdConfig;
import softm.hannun.ads.HannunAds;

/** 치매돌봄 화면을 한눈서비스 공통 AdMob 구현에 연결한다. */
final class GoogleAds {
    private final HannunAds delegate;
    GoogleAds(Activity activity, LinearLayout root) {
        delegate = new HannunAds(activity, root, new HannunAdConfig(
            activity.getString(R.string.admob_native_ad_unit_id), null,
            activity.getString(R.string.admob_interstitial_ad_unit_id),
            activity.getString(R.string.admob_exit_banner_ad_unit_id)));
    }
    void initialize() { delegate.start(); }
    void onResume() { delegate.onResume(); }
    void onPause() { delegate.onPause(); }
    void showExitConfirmation() { delegate.showExitConfirmation(); }
    void destroy() { delegate.destroy(); }
}
