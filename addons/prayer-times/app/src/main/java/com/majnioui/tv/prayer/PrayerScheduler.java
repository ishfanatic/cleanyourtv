package com.majnioui.tv.prayer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PrayerScheduler {
    private static final String TAG = "PrayerScheduler";
    public static final String ACTION_PRAYER_ALARM = "com.majnioui.tv.prayer.ACTION_PRAYER_ALARM";
    public static final String ACTION_REFRESH_ALARM = "com.majnioui.tv.prayer.ACTION_REFRESH_ALARM";
    public static final String ACTION_HEADS_UP = "com.majnioui.tv.prayer.ACTION_HEADS_UP";
    public static final String ACTION_KAHF = "com.majnioui.tv.prayer.ACTION_KAHF";

    public static final String EXTRA_PRAYER_NAME = "prayer_name";
    public static final String EXTRA_PRAYER_TIME = "prayer_time";
    public static final String EXTRA_PLACE = "place";
    public static final String EXTRA_ADHAN = "adhan";
    public static final String EXTRA_WAKE = "wake";
    /** PrayerAlertActivity: KIND_HEADS_UP or KIND_KAHF for a quiet card; absent for a prayer. */
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_MINUTES = "minutes";
    /** Ramadan Maghrib: the iftar dua follows the dua after the adhan. */
    public static final String EXTRA_IFTAR = "iftar";
    public static final String KIND_HEADS_UP = "heads_up";
    public static final String KIND_KAHF = "kahf";

    static final String PREFS_NAME = "prayer_prefs";
    static final String KEY_DATE = "cached_date";
    static final String KEY_PLACE = "place";
    static final String KEY_HIJRI = "hijri";
    static final String KEY_HIJRI_MONTH = "hijri_month";

    private static final int CODE_PRAYER = 1001;
    private static final int CODE_REFRESH = 1002;
    private static final int CODE_HEADS_UP = 1003;
    private static final int CODE_KAHF = 1004;

    public static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    public static final int[] NAME_RES = {
            R.string.prayer_fajr,
            R.string.prayer_sunrise,
            R.string.prayer_dhuhr,
            R.string.prayer_asr,
            R.string.prayer_maghrib,
            R.string.prayer_isha
    };
    public static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final String ARABIC_JUMUAH = "الجمعة";
    public static final int[] ICONS = {
            R.drawable.ic_fajr,
            R.drawable.ic_sunrise,
            R.drawable.ic_dhuhr,
            R.drawable.ic_asr,
            R.drawable.ic_maghrib,
            R.drawable.ic_isha
    };

    /**
     * "17:37" from Aladhan as the TV shows its own clock: "5:37 PM" when the TV is set to 12-hour time, "17:37"
     * when it is set to 24-hour. The 24-hour form stays for the maths.
     */
    public static String clock(Context ctx, String hhmm) {
        if (!android.text.format.DateFormat.is24HourFormat(ctx)) {
            try {
                return java.time.LocalTime.parse(hhmm)
                        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()));
            } catch (RuntimeException e) {
                return hhmm;
            }
        }
        return hhmm;
    }

    /** Minutes after midnight, shown as clock(ctx, "HH:mm") shows it. */
    static String clock(Context ctx, int minuteOfDay) {
        return clock(ctx, String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60 % 24, minuteOfDay % 60));
    }

    /** "13:30" as minutes after midnight, or -1 if it is not a time. */
    static int toMinutes(String hhmm) {
        try {
            String v = hhmm.trim();
            return Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static int getPrayerIndex(String name) {
        if (name == null) return -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    static boolean isFriday() {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
    }

    /** The prayer's name as shown: Dhuhr reads Jumu'ah on Fridays. */
    static String displayName(Context ctx, int i) {
        if (i == 2 && isFriday()) return ctx.getString(R.string.prayer_jumuah);
        return ctx.getString(NAME_RES[i]);
    }

    static String displayArabic(int i) {
        return i == 2 && isFriday() ? ARABIC_JUMUAH : ARABIC[i];
    }

    /** Fridays: jumuah_time replaces the calculated Dhuhr, in both arrays. */
    static void adjustForFriday(Context ctx, String[] times, int[] minutes) {
        String jumuah = ctx.getString(R.string.jumuah_time).trim();
        int m = toMinutes(jumuah);
        if (!isFriday() || m < 0) return;
        times[2] = jumuah;
        minutes[2] = m;
    }

    /** ramadan in config.xml: on, off, or auto from the Hijri month Aladhan gave for today. */
    static boolean ramadan(Context ctx) {
        String mode = ctx.getString(R.string.ramadan).trim();
        if ("on".equals(mode)) return true;
        if (!"auto".equals(mode)) return false;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_HIJRI_MONTH, 0) == 9;
    }

    /** Minutes from adhan to iqamah for prayer i, or 0 for none (Sunrise, Jumu'ah, or iqamah_minutes empty). */
    static int iqamahAfter(Context ctx, int i) {
        if (i == 1 || (i == 2 && isFriday())) return 0;
        String[] parts = ctx.getString(R.string.iqamah_minutes).split(",");
        int slot = i == 0 ? 0 : i - 1;
        if (slot >= parts.length) return 0;
        try {
            return Math.max(0, Integer.parseInt(parts[slot].trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** What the page and the Home line count down to. */
    static final class Upcoming {
        final int index;
        final boolean iqamah;
        final int atMinute;
        final String label;

        Upcoming(int index, boolean iqamah, int atMinute, String label) {
            this.index = index;
            this.iqamah = iqamah;
            this.atMinute = atMinute;
            this.label = label;
        }
    }

    /**
     * At a minute of the day: the iqamah of a prayer just called, else the next prayer (Suhoor's end and Iftar
     * in Ramadan); null once Isha and its iqamah have passed.
     */
    static Upcoming upcoming(Context ctx, int[] minutes, int now) {
        boolean ramadan = ramadan(ctx);
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1 || minutes[i] < 0) continue;
            int iqamah = iqamahAfter(ctx, i);
            if (iqamah > 0 && now >= minutes[i] && now < minutes[i] + iqamah) {
                return new Upcoming(i, true, minutes[i] + iqamah, ctx.getString(R.string.iqamah));
            }
            if (minutes[i] > now) {
                String label = displayName(ctx, i);
                if (ramadan && i == 0) label = ctx.getString(R.string.suhoor_ends);
                if (ramadan && i == 4) label = ctx.getString(R.string.iftar);
                return new Upcoming(i, false, minutes[i], label);
            }
        }
        return null;
    }

    /** "29 Jumādā al-ūlā 1448 AH" from an Aladhan timings answer's data object. */
    static String hijriText(JSONObject data) {
        JSONObject hijri = data.optJSONObject("date") == null ? null : data.optJSONObject("date").optJSONObject("hijri");
        if (hijri == null) return "";
        JSONObject month = hijri.optJSONObject("month");
        return hijri.optString("day") + " " + (month == null ? "" : month.optString("en")) + " " + hijri.optString("year") + " AH";
    }

    static int hijriMonth(JSONObject data) {
        JSONObject date = data.optJSONObject("date");
        JSONObject hijri = date == null ? null : date.optJSONObject("hijri");
        JSONObject month = hijri == null ? null : hijri.optJSONObject("month");
        return month == null ? 0 : month.optInt("number", 0);
    }

    /** Store today's times (Friday already applied) for the Home line and the alarms, then schedule. */
    public static void saveTimingsAndSchedule(Context ctx, String day, String[] names, String[] times, int[] minutes,
                                              String place, String hijri, int hijriMonth) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DATE, day);
        editor.putString(KEY_PLACE, place);
        editor.putString(KEY_HIJRI, hijri);
        editor.putInt(KEY_HIJRI_MONTH, hijriMonth);
        for (int i = 0; i < names.length; i++) {
            editor.putString("time_" + names[i], times[i]);
            editor.putInt("min_" + names[i], minutes[i]);
        }
        editor.apply();
        NextPrayerProvider.notifyChanged(ctx);

        if (ctx.getResources().getBoolean(R.bool.reminders)) {
            scheduleNext(ctx);
        } else {
            cancel(ctx);
        }
    }

    private static PendingIntent pending(Context ctx, int code, Intent intent, int flags) {
        return PendingIntent.getBroadcast(ctx, code, intent,
                flags | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    private static void cancelOne(Context ctx, AlarmManager am, int code, String action) {
        Intent intent = new Intent(ctx, PrayerReceiver.class).setAction(action);
        PendingIntent pi = pending(ctx, code, intent, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        cancelOne(ctx, am, CODE_PRAYER, ACTION_PRAYER_ALARM);
        cancelOne(ctx, am, CODE_REFRESH, ACTION_REFRESH_ALARM);
        cancelOne(ctx, am, CODE_HEADS_UP, ACTION_HEADS_UP);
        cancelOne(ctx, am, CODE_KAHF, ACTION_KAHF);
        Log.i(TAG, "All prayer reminder alarms cancelled");
    }

    /** Today at a minute of the day, in epoch milliseconds. */
    private static long todayAt(int minuteOfDay) {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        target.set(Calendar.MINUTE, minuteOfDay % 60);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        return target.getTimeInMillis();
    }

    public static void scheduleNext(Context ctx) {
        if (!ctx.getResources().getBoolean(R.bool.reminders)) {
            cancel(ctx);
            return;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDate = prefs.getString(KEY_DATE, "");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());

        if (!today.equals(savedDate)) {
            // Data for today is not available yet, fetch in background
            fetchTimingsAndSchedule(ctx);
            return;
        }

        String place = prefs.getString(KEY_PLACE, ctx.getString(R.string.place));
        int[] minutes = new int[NAMES.length];
        String[] times = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            minutes[i] = prefs.getInt("min_" + NAMES[i], -1);
            times[i] = prefs.getString("time_" + NAMES[i], "");
        }

        Calendar now = Calendar.getInstance();
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int nextIdx = -1;

        // Skip index 1 (Sunrise), only check prayer times
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1) continue;
            if (minutes[i] > nowMinute) {
                nextIdx = i;
                break;
            }
        }

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        if (nextIdx >= 0) {
            long triggerAtMillis = todayAt(minutes[nextIdx]);
            Log.i(TAG, "Scheduling alarm for " + NAMES[nextIdx] + " (" + times[nextIdx] + ") at " + new Date(triggerAtMillis));

            Intent intent = new Intent(ctx, PrayerReceiver.class);
            intent.setAction(ACTION_PRAYER_ALARM);
            intent.putExtra(EXTRA_PRAYER_NAME, NAMES[nextIdx]);
            intent.putExtra(EXTRA_PRAYER_TIME, times[nextIdx]);
            intent.putExtra(EXTRA_PLACE, place);
            setAlarm(am, triggerAtMillis, pending(ctx, CODE_PRAYER, intent, PendingIntent.FLAG_UPDATE_CURRENT));

            // heads_up_minutes before it, while that is still ahead.
            int ahead = ctx.getResources().getInteger(R.integer.heads_up_minutes);
            if (ahead > 0 && minutes[nextIdx] - ahead > nowMinute) {
                Intent heads = new Intent(ctx, PrayerReceiver.class);
                heads.setAction(ACTION_HEADS_UP);
                heads.putExtra(EXTRA_PRAYER_NAME, NAMES[nextIdx]);
                heads.putExtra(EXTRA_PRAYER_TIME, times[nextIdx]);
                heads.putExtra(EXTRA_PLACE, place);
                heads.putExtra(EXTRA_MINUTES, ahead);
                setAlarm(am, todayAt(minutes[nextIdx] - ahead), pending(ctx, CODE_HEADS_UP, heads, PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                cancelOne(ctx, am, CODE_HEADS_UP, ACTION_HEADS_UP);
            }
        } else {
            // All prayers for today have passed. Schedule refresh at 00:05 tomorrow morning
            Calendar target = (Calendar) now.clone();
            target.add(Calendar.DAY_OF_YEAR, 1);
            target.set(Calendar.HOUR_OF_DAY, 0);
            target.set(Calendar.MINUTE, 5);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            Log.i(TAG, "All prayers passed for today. Scheduling daily refresh at " + target.getTime());

            Intent intent = new Intent(ctx, PrayerReceiver.class);
            intent.setAction(ACTION_REFRESH_ALARM);
            setAlarm(am, target.getTimeInMillis(), pending(ctx, CODE_REFRESH, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        // Fridays: the Surat al-Kahf card at kahf_time, while that is still ahead.
        int kahf = toMinutes(ctx.getString(R.string.kahf_time));
        if (isFriday() && kahf > nowMinute) {
            Intent intent = new Intent(ctx, PrayerReceiver.class).setAction(ACTION_KAHF);
            setAlarm(am, todayAt(kahf), pending(ctx, CODE_KAHF, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        }
    }

    private static void setAlarm(AlarmManager am, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                    return;
                } catch (SecurityException e) {
                    Log.w(TAG, "Exact alarm disallowed at runtime, falling back: " + e);
                }
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    public static void fetchTimingsAndSchedule(Context ctx) {
        if (!ctx.getResources().getBoolean(R.bool.reminders)) {
            cancel(ctx);
            return;
        }

        new Thread(() -> {
            try {
                Context appContext = ctx.getApplicationContext();
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
                String dayParam = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date());
                String url = "https://api.aladhan.com/v1/timings/" + dayParam
                        + "?latitude=" + appContext.getString(R.string.latitude)
                        + "&longitude=" + appContext.getString(R.string.longitude)
                        + "&method=" + appContext.getString(R.string.method)
                        + "&school=" + appContext.getString(R.string.school);

                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }

                JSONObject json = new JSONObject(out.toString("UTF-8"));
                JSONObject data = json.getJSONObject("data");
                JSONObject t = data.getJSONObject("timings");

                int[] minutes = new int[NAMES.length];
                String[] times = new String[NAMES.length];
                for (int i = 0; i < NAMES.length; i++) {
                    String v = t.getString(NAMES[i]).trim();
                    int sp = v.indexOf(' ');
                    if (sp > 0) v = v.substring(0, sp);
                    times[i] = v;
                    minutes[i] = toMinutes(v);
                }
                adjustForFriday(appContext, times, minutes);

                saveTimingsAndSchedule(appContext, day, NAMES, times, minutes, appContext.getString(R.string.place),
                        hijriText(data), hijriMonth(data));
                Log.i(TAG, "Background fetch and schedule successful for " + day);
            } catch (Exception e) {
                Log.w(TAG, "Background fetch error: " + e);
            }
        }).start();
    }
}
