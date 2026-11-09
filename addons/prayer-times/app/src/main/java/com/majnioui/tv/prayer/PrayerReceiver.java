package com.majnioui.tv.prayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.util.Arrays;

public class PrayerReceiver extends BroadcastReceiver {
    private static final String TAG = "PrayerReceiver";
    private static final String CHANNEL_ID = "prayer_reminder";
    // The adhan is the sound, so its notification is silent; a channel's sound cannot change once created.
    private static final String CHANNEL_QUIET_ID = "prayer_reminder_quiet";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!context.getResources().getBoolean(R.bool.reminders)) {
            Log.i(TAG, "Reminders are disabled in config.xml");
            PrayerScheduler.cancel(context);
            return;
        }

        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "onReceive: " + action);

        if (PrayerScheduler.ACTION_REFRESH_ALARM.equals(action)) {
            PrayerScheduler.fetchTimingsAndSchedule(context);
            return;
        }

        // The quiet cards: a prayer coming up (heads_up_minutes), Surat al-Kahf on Fridays (kahf_time). Only while
        // the TV is on; they never wake it and make no sound.
        boolean headsUp = PrayerScheduler.ACTION_HEADS_UP.equals(action);
        if (headsUp || PrayerScheduler.ACTION_KAHF.equals(action)) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isInteractive()) {
                Intent card = new Intent(context, PrayerAlertActivity.class);
                card.putExtras(intent);
                card.putExtra(PrayerScheduler.EXTRA_KIND, headsUp ? PrayerScheduler.KIND_HEADS_UP : PrayerScheduler.KIND_KAHF);
                String rawTime = intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER_TIME);
                card.putExtra(PrayerScheduler.EXTRA_PRAYER_TIME, rawTime == null ? "" : PrayerScheduler.clock(context, rawTime));
                card.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try {
                    context.startActivity(card);
                } catch (Exception e) {
                    Log.w(TAG, "Launch card error: " + e);
                }
            }
            return;
        }

        if (PrayerScheduler.ACTION_PRAYER_ALARM.equals(action)) {
            String name = intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER_NAME);
            String time = intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER_TIME);
            String place = intent.getStringExtra(PrayerScheduler.EXTRA_PLACE);
            if (name == null) name = "Fajr";
            if (time == null) time = "";
            time = PrayerScheduler.clock(context, time);
            if (place == null) place = context.getString(R.string.place);

            int idx = PrayerScheduler.getPrayerIndex(name);
            String localizedName = idx >= 0 ? PrayerScheduler.displayName(context, idx) : name;

            // TV in standby: only the prayers listed in adhan_wake turn it on; the rest pass quietly.
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean asleep = pm != null && !pm.isInteractive();
            boolean wake = asleep && Arrays.asList(context.getString(R.string.adhan_wake).split("\\s*,\\s*")).contains(name);
            if (asleep && !wake) {
                Log.i(TAG, name + " while the TV is off and not in adhan_wake: nothing shown");
                PrayerScheduler.scheduleNext(context);
                return;
            }
            if (wake) {
                // Keep the TV awake until the card's activity turns the screen on.
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "prayer:adhan").acquire(30_000);
            }
            String adhanPath = adhanFile(context, name);

            // 1. Post High-Priority Notification (handles sound and tray indicator with fixed ID)
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID,
                            context.getString(R.string.app_name),
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    ch.setDescription(context.getString(R.string.app_name));
                    ch.enableLights(true);
                    ch.setLightColor(Color.parseColor("#2BB673"));

                    Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .build();
                    ch.setSound(soundUri, audioAttributes);
                    nm.createNotificationChannel(ch);

                    NotificationChannel quiet = new NotificationChannel(
                            CHANNEL_QUIET_ID,
                            context.getString(R.string.app_name),
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    quiet.setSound(null, null);
                    nm.createNotificationChannel(quiet);
                }

                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent contentPi = PendingIntent.getActivity(
                        context,
                        0,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );

                Notification.Builder nb;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nb = new Notification.Builder(context, adhanPath != null ? CHANNEL_QUIET_ID : CHANNEL_ID);
                } else {
                    nb = new Notification.Builder(context);
                }

                String title = context.getString(R.string.notification_title, localizedName);
                String body = context.getString(R.string.notification_body, localizedName, time, place);

                nb.setContentTitle(title)
                        .setContentText(body)
                        .setSmallIcon(R.drawable.icon)
                        .setAutoCancel(true)
                        .setContentIntent(contentPi)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setColor(Color.parseColor("#2BB673"));

                nm.notify(NOTIFICATION_ID, nb.build());
            }

            // 2. Launch floating alert banner on screen (single launch path)
            try {
                Intent popup = new Intent(context, PrayerAlertActivity.class);
                popup.putExtra(PrayerScheduler.EXTRA_PRAYER_NAME, name);
                popup.putExtra(PrayerScheduler.EXTRA_PRAYER_TIME, time);
                popup.putExtra(PrayerScheduler.EXTRA_PLACE, place);
                popup.putExtra(PrayerScheduler.EXTRA_ADHAN, adhanPath);
                popup.putExtra(PrayerScheduler.EXTRA_WAKE, wake);
                popup.putExtra(PrayerScheduler.EXTRA_IFTAR, idx == 4 && PrayerScheduler.ramadan(context));
                popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(popup);
            } catch (Exception e) {
                Log.w(TAG, "Launch alert activity error: " + e);
            }

            // 3. Schedule next prayer
            PrayerScheduler.scheduleNext(context);
        }
    }

    /**
     * The adhan to play for this prayer, or null for the chime: adhan_<prayer>.mp3 (adhan_fajr.mp3,
     * adhan_dhuhr.mp3...) if there is one, else adhan.mp3, in the app's own folder on the TV
     * (/sdcard/Android/data/com.majnioui.tv.prayer/files/), so a recording can change without a rebuild.
     */
    static String adhanFile(Context context, String name) {
        if (!context.getResources().getBoolean(R.bool.adhan)) return null;
        File dir = context.getExternalFilesDir(null);
        if (dir == null) return null;
        File own = new File(dir, "adhan_" + name.toLowerCase(java.util.Locale.ROOT) + ".mp3");
        File f = own.canRead() ? own : new File(dir, "adhan.mp3");
        return f.canRead() ? f.getAbsolutePath() : null;
    }
}
