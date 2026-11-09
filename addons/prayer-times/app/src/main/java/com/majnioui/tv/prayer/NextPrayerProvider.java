package com.majnioui.tv.prayer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The next prayer for the Clean Your TV launcher's game_day panel under the clock (docs/modules.md):
 * one row, "Johns Creek · 12:21 PM" over "Dhuhr in 13m", or the iqamah, Suhoor's end or Iftar when those
 * come next (PrayerScheduler.upcoming). Read-only, and it holds nothing the page does not show. It reads the
 * times the page or the reminder fetch saved today; with none saved yet, or after Isha, the cursor is empty
 * and the launcher hides the panel.
 */
public class NextPrayerProvider extends ContentProvider {
    static final String AUTHORITY = "com.majnioui.tv.prayer.next";
    static final Uri TURNS = Uri.parse("content://" + AUTHORITY + "/turns");
    private static final String[] COLUMNS = {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"};

    /** Tell a watching launcher that the next prayer may have changed. */
    static void notifyChanged(Context ctx) {
        ctx.getContentResolver().notifyChange(TURNS, null);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor out = new MatrixCursor(COLUMNS);
        Context ctx = getContext();
        if (ctx == null) return out;
        SharedPreferences prefs = ctx.getSharedPreferences(PrayerScheduler.PREFS_NAME, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        if (!today.equals(prefs.getString(PrayerScheduler.KEY_DATE, ""))) return out;

        Calendar now = Calendar.getInstance();
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int[] minutes = new int[PrayerScheduler.NAMES.length];
        for (int i = 0; i < minutes.length; i++) minutes[i] = prefs.getInt("min_" + PrayerScheduler.NAMES[i], -1);
        PrayerScheduler.Upcoming up = PrayerScheduler.upcoming(ctx, minutes, nowMinute);
        if (up == null) return out;

        Calendar at = (Calendar) now.clone();
        at.set(Calendar.HOUR_OF_DAY, up.atMinute / 60);
        at.set(Calendar.MINUTE, up.atMinute % 60);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        long start = at.getTimeInMillis();
        // The top line: the city, or today's Hijri date (home_hijri); then the time, or which prayer's iqamah.
        String hijri = prefs.getString(PrayerScheduler.KEY_HIJRI, "");
        String lead = ctx.getResources().getBoolean(R.bool.home_hijri) && !hijri.isEmpty()
                ? hijri : prefs.getString(PrayerScheduler.KEY_PLACE, ctx.getString(R.string.place));
        String detail = up.iqamah ? PrayerScheduler.displayName(ctx, up.index) + " " + PrayerScheduler.clock(ctx, up.atMinute)
                : PrayerScheduler.clock(ctx, up.atMinute);
        // A one-second row: the launcher drops it as the time arrives and asks again for what follows.
        out.addRow(new Object[] {lead + " · " + detail, up.label, start, start + 1000,
                ctx.getColor(R.color.accent), "prayer-times", 30});
        return out;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
