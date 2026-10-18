package com.anshu.home;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sends the remote's Home button to our home screen.
 * Escape hatch: press Home twice quickly to open Google TV's own home screen.
 * Google TV's home screen is never turned off, so if this ever fails the TV just behaves normally.
 */
public class HomeKeyService extends AccessibilityService {

    private static final long DOUBLE_PRESS_MS = 600;
    private long lastHomeUp;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        openOurs(); // also covers TV start-up
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            long now = SystemClock.uptimeMillis();
            if (now - lastHomeUp < DOUBLE_PRESS_MS) {
                lastHomeUp = 0;
                openGoogleTv();
            } else {
                lastHomeUp = now;
                openOurs();
            }
        }
        return true;
    }

    private void openOurs() {
        try {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            openGoogleTv();
        }
    }

    private void openGoogleTv() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setComponent(new ComponentName("com.google.android.apps.tv.launcherx",
                    "com.google.android.apps.tv.launcherx.home.HomeActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
