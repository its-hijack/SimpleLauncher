package com.simplehome.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Brings SimpleLauncher back whenever Amazon's home screen appears.
 *
 * After Home is pressed, Android holds back app launches for about five seconds
 * (a built-in rule for apps that aren't part of the system), so SimpleLauncher can't
 * appear straight away. To hide Amazon's screen during that wait, this service
 * immediately covers the screen with SimpleLauncher's background, logo and clock,
 * and removes the cover as soon as SimpleLauncher is showing.
 *
 * The "Amazon Home" button on SimpleLauncher gives a pass: Amazon's home screen and its
 * Settings are left alone until you come back to Amazon's main screen from somewhere
 * else, open SimpleLauncher, or the stick goes to sleep.
 */
public class HomeButtonService extends AccessibilityService {

    private static final long LONG_PRESS_MS = 600;
    private static final long COVER_TIMEOUT_MS = 8000;
    private static final long PASS_MAX_MS = 30 * 60_000;

    /** Pop-ups that don't count as leaving Amazon's home screen. */
    private static final Set<String> OVERLAYS = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.amazon.vizzini",
            "com.amazon.tv.notificationcenter", "com.amazon.tv.quicksettings.ui"));

    private static HomeButtonService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable removeCover = this::hideCover;
    private View cover;
    private long homeDownAt;
    private long lastOpen;

    // The Amazon Home pass
    private boolean pass;
    private long passSince;
    private String amazonHomeClass;
    private boolean leftAmazonHome;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pass = false;
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public void onDestroy() {
        hideCover();
        try {
            unregisterReceiver(screenOff);
        } catch (IllegalArgumentException ignored) {
        }
        if (instance == this) instance = null;
        super.onDestroy();
    }

    /** Called by SimpleLauncher's "Amazon Home" button just before it opens Amazon's home screen. */
    static void allowAmazonHome() {
        HomeButtonService s = instance;
        if (s == null) return;
        s.hideCover();
        s.pass = true;
        s.passSince = SystemClock.uptimeMillis();
        s.amazonHomeClass = null;
        s.leftAmazonHome = false;
    }

    // Used if Fire OS passes the Home key to us (some versions do).
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME || !Prefs.hook(this) || !Mode.fireTv(this)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) homeDownAt = event.getEventTime();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (event.getEventTime() - homeDownAt >= LONG_PRESS_MS) {
                Apps.openFireSettings(this);
            } else {
                openLauncher();
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!Prefs.hook(this) || !Mode.fireTv(this)) return;
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : null;
        String cls = event.getClassName() != null ? event.getClassName().toString() : null;
        boolean amazon = Apps.AMAZON_LAUNCHER.equals(pkg);

        if (pass && SystemClock.uptimeMillis() - passSince > PASS_MAX_MS) pass = false;
        if (pass) {
            if (Prefs.passUntilHome(this)) return;   // "Stay in Amazon": only SimpleLauncher itself ends the pass
            if (amazon && amazonHomeClass == null) {
                amazonHomeClass = cls;          // the main screen we just opened
                return;
            }
            if (amazon && cls != null && cls.equals(amazonHomeClass)) {
                if (!leftAmazonHome) return;    // still on Amazon's main screen
                pass = false;                   // back from Settings/an app: SimpleLauncher again
            } else {
                if (pkg != null && !OVERLAYS.contains(pkg) && !pkg.equals(getPackageName())) leftAmazonHome = true;
                return;
            }
        }
        if (amazon) openLauncher();
    }

    @Override
    public void onInterrupt() {
    }

    private void openLauncher() {
        showCover();
        long now = SystemClock.uptimeMillis();
        if (now - lastOpen < 400) return;
        lastOpen = now;
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Apps.tryStart(this, intent);
    }

    /** Called by the home screen once it's showing. */
    static void launcherShown() {
        HomeButtonService s = instance;
        if (s == null) return;
        s.pass = false;
        s.handler.post(s::hideCover);
    }

    // ------------------------------------------------------------------ the cover

    private void showCover() {
        handler.removeCallbacks(removeCover);
        handler.postDelayed(removeCover, COVER_TIMEOUT_MS);
        if (cover != null) return;

        FrameLayout view = new FrameLayout(this);
        view.setBackgroundColor(Prefs.background(this));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        // Swallow remote presses so they don't move around Amazon's screen underneath.
        view.setOnKeyListener((v, keyCode, e) -> true);

        // Same logo and clock position as the home screen, so the switch-over is seamless.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 42), Ui.dp(this, 32), Ui.dp(this, 42), 0);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        header.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 52)));

        TextClock clock = new TextClock(this);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        clock.setTextColor(0xFFFFFFFF);
        clock.setIncludeFontPadding(false);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));

        TextClock ampm = new TextClock(this);
        ampm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        ampm.setTextColor(0xFFFFFFFF);
        ampm.setIncludeFontPadding(false);
        ampm.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        ampm.setPadding(Ui.dp(this, 6), 0, 0, 0);

        TextClock date = new TextClock(this);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        date.setTextColor(Ui.TEXT_DIM);
        date.setPadding(0, Ui.dp(this, 2), 0, 0);
        Ui.applyClock(this, clock, ampm, date);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setBaselineAligned(true);
        timeRow.addView(clock);
        timeRow.addView(ampm);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(timeRow);
        texts.addView(date);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(Ui.dp(this, 16), 0, 0, 0);
        header.addView(texts, clp);

        view.addView(header, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(view, lp);
            cover = view;
            view.requestFocus();
        } catch (RuntimeException ignored) {
            // If Fire OS refuses the cover, the launcher still opens, just without it.
        }
    }

    private void hideCover() {
        handler.removeCallbacks(removeCover);
        if (cover == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (RuntimeException ignored) {
        }
        cover = null;
    }

    // ------------------------------------------------------------------ switching it on

    static ComponentName component(Context c) {
        return new ComponentName(c, HomeButtonService.class);
    }

    static boolean isEnabled(Context c) {
        String value = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (value == null) return false;
        ComponentName me = component(c);
        for (String part : value.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part.trim());
            if (me.equals(cn)) return true;
        }
        return false;
    }

    /** Needs WRITE_SECURE_SETTINGS, which the setup script grants over ADB. */
    static boolean enable(Context c) {
        try {
            ContentResolver cr = c.getContentResolver();
            String value = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String me = component(c).flattenToString();
            String updated = (value == null || value.isEmpty()) ? me : value + ":" + me;
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
