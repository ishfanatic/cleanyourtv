package com.majnioui.tv.prayer;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/**
 * Puts the TV back to standby after an adhan woke it and nobody pressed a button (wake_standby_minutes). An app
 * cannot turn the screen off itself; the accessibility "lock screen" action can, and on a TV that is standby.
 * It asks for no events, reads nothing on screen and filters no keys. Off until the person switches it on:
 * Settings > Accessibility, or
 * settings put secure enabled_accessibility_services com.majnioui.tv.prayer/.StandbyService
 */
public class StandbyService extends AccessibilityService {
    private static StandbyService running;

    /** True while the person has the service switched on and the system has bound it. */
    static boolean available() {
        return running != null && Build.VERSION.SDK_INT >= 28;
    }

    /** Standby now; false if the service is off or the TV refused. */
    static boolean sleep() {
        return available() && running.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() {
        running = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        running = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
