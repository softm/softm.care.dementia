package net.softm.care.dementia;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.google.android.gms.ads.*;

/** Exit controls remain available whether the optional ad loads or fails. */
final class ExitConfirmation {
    private static final long EXIT_AD_TIMEOUT_MS = 5_000L;

    static AlertDialog create(Activity activity, boolean adsAllowed) {
        float density = activity.getResources().getDisplayMetrics().density;
        int available = (int) (activity.getResources().getDisplayMetrics().widthPixels / density);
        int width = Math.min(380, available - 24);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(activity, 16), dp(activity, 24), dp(activity, 16), dp(activity, 20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE); background.setCornerRadius(dp(activity, 24));
        content.setBackground(background);
        TextView title = text(activity, "앱을 종료하시겠습니까?", 22, 0xff202936);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView message = text(activity, "치매안심을 종료하고 홈 화면으로 돌아갑니다.", 15, 0xff707b8f);
        message.setPadding(0, dp(activity, 12), 0, dp(activity, 18));
        content.addView(message, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout slot = new FrameLayout(activity);
        slot.setVisibility(View.GONE);
        content.addView(slot, new LinearLayout.LayoutParams(-1, dp(activity, 250)));
        LinearLayout loading = new LinearLayout(activity);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        ProgressBar indicator = new ProgressBar(activity);
        loading.addView(indicator, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));
        TextView loadingText = text(activity, "광고를 불러오고 있습니다.", 14, 0xff707b8f);
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(-2, -2);
        loadingTextParams.topMargin = dp(activity, 12);
        loading.addView(loadingText, loadingTextParams);
        slot.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        View divider = new View(activity); divider.setBackgroundColor(0xffd9e0e3);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        line.topMargin = dp(activity, 18); line.bottomMargin = dp(activity, 18);
        content.addView(divider, line);
        LinearLayout buttons = new LinearLayout(activity);
        Button stay = new Button(activity); stay.setText("계속 이용"); stay.setTextSize(17);
        stay.setTextColor(0xff303438); stay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe5e7e7));
        Button exit = new Button(activity); exit.setText("종료"); exit.setTextSize(17);
        exit.setTextColor(Color.WHITE); exit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff117d77));
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(0, dp(activity, 56), 1);
        button.setMarginEnd(dp(activity, 12)); buttons.addView(stay, button);
        buttons.addView(exit, new LinearLayout.LayoutParams(0, dp(activity, 56), 1));
        content.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true); scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity).create();
        dialog.setView(scroll, 0, 0, 0, 0);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable enableExit = () -> {
            if (dialog.isShowing()) exit.setEnabled(true);
        };
        stay.setOnClickListener(v -> dialog.dismiss());
        exit.setOnClickListener(v -> { activity.finish(); dialog.dismiss(); });
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int maxHeight = activity.getResources().getDisplayMetrics().heightPixels - dp(activity, 96);
            dialog.getWindow().setLayout(dp(activity, width), WindowManager.LayoutParams.WRAP_CONTENT);
            content.post(() -> {
                if (dialog.isShowing() && content.getHeight() > maxHeight)
                    dialog.getWindow().setLayout(dp(activity, width), maxHeight);
            });
            if (!adsAllowed || width - 32 < 300) {
                exit.setEnabled(true);
                return;
            }
            slot.setVisibility(View.VISIBLE);
            exit.setEnabled(false);
            handler.postDelayed(enableExit, EXIT_AD_TIMEOUT_MS);
            AdView ad = new AdView(activity);
            ad.setAdSize(AdSize.MEDIUM_RECTANGLE);
            ad.setAdUnitId(activity.getString(R.string.admob_exit_banner_ad_unit_id));
            slot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { }
                public void onViewDetachedFromWindow(View v) { ad.destroy(); }
            });
            ad.setAdListener(new AdListener() {
                @Override public void onAdLoaded() {
                    if (!dialog.isShowing()) { ad.destroy(); return; }
                    handler.removeCallbacks(enableExit);
                    exit.setEnabled(true);
                    slot.removeAllViews();
                    slot.addView(ad, new FrameLayout.LayoutParams(dp(activity, 300), dp(activity, 250), Gravity.CENTER));
                }

                @Override public void onAdFailedToLoad(LoadAdError error) {
                    handler.removeCallbacks(enableExit);
                    if (!dialog.isShowing()) { ad.destroy(); return; }
                    exit.setEnabled(true);
                    slot.setVisibility(View.GONE);
                }
            });
            ad.loadAd(new AdRequest.Builder().build());
        });
        return dialog;
    }
    private static int dp(Activity a, int n) { return Math.round(n * a.getResources().getDisplayMetrics().density); }
    private static TextView text(Activity a, String value, int size, int color) {
        TextView text = new TextView(a); text.setText(value); text.setTextSize(size);
        text.setTextColor(color); text.setGravity(Gravity.CENTER); return text;
    }
}
