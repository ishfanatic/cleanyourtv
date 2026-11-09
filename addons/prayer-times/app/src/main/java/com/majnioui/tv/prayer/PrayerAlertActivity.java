package com.majnioui.tv.prayer;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PrayerAlertActivity extends Activity {
    // Ultra-compact card dimensions: sleek, narrow, and unobtrusive for TV watching
    private static final int CARD_WIDTH_DP = 285;
    private static final int MARGIN_RIGHT_DP = 24;
    private static final int MARGIN_TOP_DP = 20;
    private static final int TIMEOUT_SECONDS = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int secondsRemaining = TIMEOUT_SECONDS;
    private TextView timerView;
    private MediaPlayer player;
    private AudioFocusRequest focus;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                finish();
            } else {
                if (timerView != null) {
                    timerView.setText(getString(R.string.prayer_alert_dismiss, secondsRemaining));
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Set by PrayerReceiver only for a prayer chosen in adhan_wake while the TV was in standby.
        if (getIntent().getBooleanExtra(PrayerScheduler.EXTRA_WAKE, false) && Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        String rawName = getIntent().getStringExtra(PrayerScheduler.EXTRA_PRAYER_NAME);
        String time = getIntent().getStringExtra(PrayerScheduler.EXTRA_PRAYER_TIME);
        String place = getIntent().getStringExtra(PrayerScheduler.EXTRA_PLACE);
        if (rawName == null) rawName = "Fajr";
        if (time == null) time = "";
        if (place == null) place = getString(R.string.place);

        // A prayer's card, or a quiet one: the prayer coming up (heads_up_minutes), or Surat al-Kahf on a Friday.
        String kind = getIntent().getStringExtra(PrayerScheduler.EXTRA_KIND);
        boolean kahf = PrayerScheduler.KIND_KAHF.equals(kind);
        int idx = PrayerScheduler.getPrayerIndex(rawName);
        prayerIndex = idx;
        String displayName = idx >= 0 ? PrayerScheduler.displayName(this, idx) : rawName;
        int iconRes = kahf ? R.drawable.ic_book : idx >= 0 ? PrayerScheduler.ICONS[idx] : R.drawable.icon;
        String arabicText = kahf ? "سورة الكهف" : idx >= 0 ? PrayerScheduler.displayArabic(idx) : "";
        String title = getString(R.string.prayer_alert_title, displayName);
        if (PrayerScheduler.KIND_HEADS_UP.equals(kind)) {
            title = getString(R.string.prayer_soon, displayName, getIntent().getIntExtra(PrayerScheduler.EXTRA_MINUTES, 0));
        } else if (kahf) {
            title = getString(R.string.kahf_title);
            time = getString(R.string.kahf_body);
            place = "";
        }

        Typeface font = getResources().getFont(R.font.outfit);
        int accent = getColor(R.color.accent);

        // Compact Slim Notification Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(10), dp(14), dp(10));

        // Dark acrylic translucent background (frameless / borderless)
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF212161A); // 95% opacity dark slate
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);

        // 1. Mini Prayer Icon container (frameless)
        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setGravity(Gravity.CENTER);
        int boxSize = dp(38);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(boxSize, boxSize);
        blp.rightMargin = dp(11);
        iconBox.setLayoutParams(blp);
        GradientDrawable iconBoxBg = new GradientDrawable();
        iconBoxBg.setColor(0x282BB673);
        iconBoxBg.setCornerRadius(dp(10));
        iconBox.setBackground(iconBoxBg);

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        iconView.setImageTintList(ColorStateList.valueOf(accent));
        iconBox.addView(iconView, new LinearLayout.LayoutParams(dp(22), dp(22)));
        card.addView(iconBox);

        // 2. Text Column (Title + Prayer Info)
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        infoCol.setLayoutParams(clp);

        // Row 1: Prayer Name + Arabic script
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(title);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameView.setTextColor(Color.WHITE);
        nameView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 700, false) : font);
        titleRow.addView(nameView);

        if (!arabicText.isEmpty()) {
            TextView arabicView = new TextView(this);
            arabicView.setText(arabicText);
            arabicView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            arabicView.setTextColor(0xCC2BB673);
            arabicView.setPadding(dp(6), 0, 0, 0);
            titleRow.addView(arabicView);
        }

        infoCol.addView(titleRow);

        // Row 2: Time · Place
        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        subRow.setPadding(0, dp(1), 0, 0);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        timeView.setTextColor(0xFF4ADE80); // soft vibrant green
        timeView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 600, false) : font);
        subRow.addView(timeView);

        TextView dot = new TextView(this);
        if (place.isEmpty()) dot.setVisibility(View.GONE);
        dot.setText(" · ");
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dot.setTextColor(0x66FFFFFF);
        subRow.addView(dot);

        TextView placeView = new TextView(this);
        placeView.setText(place);
        placeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        placeView.setTextColor(0xB3FFFFFF);
        placeView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 400, false) : font);
        subRow.addView(placeView);

        infoCol.addView(subRow);
        card.addView(infoCol);

        // 3. Mini Countdown Pill on the right
        timerView = new TextView(this);
        timerView.setText(getString(R.string.prayer_alert_dismiss, secondsRemaining));
        timerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        timerView.setTextColor(0x80FFFFFF);
        timerView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 400, false) : font);
        timerView.setPadding(dp(8), 0, 0, 0);
        card.addView(timerView);

        ViewGroup.LayoutParams vlp = new ViewGroup.LayoutParams(dp(CARD_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        setContentView(card, vlp);

        applyWindowPosition();
        if (kind == null) startAdhan(getIntent().getStringExtra(PrayerScheduler.EXTRA_ADHAN));
    }

    /** Play the adhan file, if there is one, pausing other media; the card stays until it ends. */
    private void startAdhan(String path) {
        if (path == null || !new File(path).canRead()) return;
        AudioManager am = getSystemService(AudioManager.class);
        if (am != null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(ATTRS).build();
            am.requestAudioFocus(focus);
        }
        handler.removeCallbacks(countdownRunnable);
        if (timerView != null) timerView.setText("");
        playing = true;
        // Coming out of standby the TV keeps its speakers muted for about a second, and a player started
        // into that stays paused (TCL G17): give the sound time to come back first.
        boolean woken = getIntent().getBooleanExtra(PrayerScheduler.EXTRA_WAKE, false);
        if (woken) {
            handler.postDelayed(() -> setVolume(getResources().getInteger(R.integer.adhan_wake_volume)), WAKE_DELAY_MS - 200);
        } else if (prayerIndex == 0) {
            setVolume(getResources().getInteger(R.integer.fajr_volume));   // a softer Fajr for someone already up
        }
        if (!play(new File(path), this::afterAdhan, woken ? WAKE_DELAY_MS : 0)) finish();
    }

    private static final AudioAttributes ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    private static final long WAKE_DELAY_MS = 2500;
    private static final int RESUME_CHECKS = 10;
    private static final int DUA_WIDTH_DP = 760;
    /** How long the dua stays after its recitation, or in all when there is no recitation to play. */
    private static final long DUA_LINGER_MS = 8000;
    private static final long DUA_SILENT_MS = 60000;
    private int resumeChecks;
    private boolean playing;
    private boolean showingDua;
    private int savedVolume = -1;
    private int prayerIndex = -1;
    private TextView standbyView;
    private int standbySeconds;

    /** adhan_wake_volume or fajr_volume for this adhan; onDestroy puts the person's own level back. */
    private void setVolume(int level) {
        AudioManager am = getSystemService(AudioManager.class);
        if (level < 0 || am == null) return;
        savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(level, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
    }

    /** Play one file after a delay, then run next; false if it cannot be opened. */
    private boolean play(File file, Runnable next, long delayMs) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(ATTRS);
            player.setDataSource(file.getAbsolutePath());
            Runnable done = () -> {
                releasePlayer();
                next.run();
            };
            player.setOnCompletionListener(mp -> done.run());
            player.setOnErrorListener((mp, what, extra) -> {
                done.run();
                return true;
            });
            player.prepare();
            resumeChecks = 0;
            handler.postDelayed(keepPlaying, delayMs);
            return true;
        } catch (Exception e) {
            releasePlayer();
            return false;
        }
    }

    /** Start the player, then check once a second for the first ten that nothing has paused it. */
    private final Runnable keepPlaying = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            if (!player.isPlaying()) player.start();
            if (++resumeChecks < RESUME_CHECKS) handler.postDelayed(this, 1000);
        }
    };

    private void releasePlayer() {
        handler.removeCallbacks(keepPlaying);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    /** The adhan ended: its dua, then in Ramadan at Maghrib the iftar dua, then standby if the adhan woke the TV. */
    private void afterAdhan() {
        boolean iftar = getIntent().getBooleanExtra(PrayerScheduler.EXTRA_IFTAR, false);
        Runnable afterIftar = this::afterDuas;
        Runnable afterDua = iftar ? () -> showDuaThenRecite(R.string.dua_iftar_title, R.string.dua_iftar_arabic,
                R.string.dua_iftar_english, R.string.dua_iftar_source, "dua_iftar", afterIftar) : afterIftar;
        if (getResources().getBoolean(R.bool.adhan_dua)) {
            showDuaThenRecite(R.string.dua_title, R.string.dua_arabic, R.string.dua_english, R.string.dua_source, "dua", afterDua);
        } else {
            afterDua.run();
        }
    }

    /** Show a dua, recite prefix_ar then prefix_en if those files are there, let it rest, then run next. */
    private void showDuaThenRecite(int title, int arabic, int english, int source, String prefix, Runnable next) {
        showDua(title, arabic, english, source);
        File dir = getExternalFilesDir(null);
        List<File> recitations = new ArrayList<>();
        for (String name : new String[] {prefix + "_ar", prefix + "_en"}) {
            for (String ext : new String[] {"mp3", "m4a"}) {
                File f = dir == null ? null : new File(dir, name + "." + ext);
                if (f != null && f.canRead()) {
                    recitations.add(f);
                    break;
                }
            }
        }
        recite(recitations, 0, next);
    }

    private void recite(List<File> recitations, int i, Runnable next) {
        if (i >= recitations.size()) {
            handler.postDelayed(next, recitations.isEmpty() ? DUA_SILENT_MS : DUA_LINGER_MS);
        } else if (!play(recitations.get(i), () -> recite(recitations, i + 1, next), 0)) {
            recite(recitations, i + 1, next);
        }
    }

    /** After the duas: back to standby in wake_standby_minutes if the adhan woke the TV, else close. */
    private void afterDuas() {
        int minutes = getResources().getInteger(R.integer.wake_standby_minutes);
        if (!getIntent().getBooleanExtra(PrayerScheduler.EXTRA_WAKE, false) || minutes <= 0 || !StandbyService.available()) {
            finish();
            return;
        }
        stopAudio();   // the volume and the paused video come back now; only the countdown stays
        showingDua = false;
        standbySeconds = minutes * 60;
        standbyView = new TextView(this);
        standbyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        standbyView.setTextColor(Color.WHITE);
        standbyView.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF212161A);
        bg.setCornerRadius(dp(14));
        standbyView.setBackground(bg);
        setContentView(standbyView, new ViewGroup.LayoutParams(dp(CARD_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT));
        applyWindowPosition();
        handler.post(standbyTick);
    }

    /** Counts down once a second; any button closes the card first (onKeyDown) and the TV stays on. */
    private final Runnable standbyTick = new Runnable() {
        @Override
        public void run() {
            if (standbySeconds <= 0) {
                StandbyService.sleep();
                finish();
                return;
            }
            standbyView.setText(getString(R.string.standby_in, standbySeconds / 60, standbySeconds % 60));
            standbySeconds--;
            handler.postDelayed(this, 1000);
        }
    };

    /** The card grows into a centred panel: the dua in Arabic, its English meaning and where it is narrated. */
    private void showDua(int titleRes, int arabicRes, int englishRes, int sourceRes) {
        showingDua = true;
        Typeface font = getResources().getFont(R.font.outfit);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(40), dp(28), dp(40), dp(26));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF212161A);
        bg.setCornerRadius(dp(22));
        panel.setBackground(bg);

        TextView title = duaText(titleRes, 13, getColor(R.color.accent), font, 600);
        title.setLetterSpacing(0.08f);
        panel.addView(title);

        TextView arabic = duaText(arabicRes, 26, Color.WHITE, null, 0);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setLineSpacing(dp(6), 1f);
        arabic.setPadding(0, dp(18), 0, 0);
        panel.addView(arabic);

        TextView english = duaText(englishRes, 17, 0xCCFFFFFF, font, 400);
        english.setLineSpacing(dp(3), 1f);
        english.setPadding(0, dp(20), 0, 0);
        panel.addView(english);

        TextView source = duaText(sourceRes, 12, 0x80FFFFFF, font, 400);
        source.setPadding(0, dp(16), 0, 0);
        panel.addView(source);

        setContentView(panel, new ViewGroup.LayoutParams(dp(DUA_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT));
        applyWindowPosition();
    }

    private TextView duaText(int res, float sp, int color, Typeface font, int weight) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        if (font != null) t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private void stopAudio() {
        releasePlayer();
        AudioManager am = getSystemService(AudioManager.class);
        if (am != null && savedVolume >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
        savedVolume = -1;
        if (am != null && focus != null) am.abandonAudioFocusRequest(focus);
        focus = null;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopAudio();
        super.onDestroy();
    }

    private void applyWindowPosition() {
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            if (showingDua) {
                window.setGravity(Gravity.CENTER);
                lp.width = dp(DUA_WIDTH_DP);
                lp.x = 0;
                lp.y = 0;
            } else {
                window.setGravity(Gravity.TOP | Gravity.END);
                lp.width = dp(CARD_WIDTH_DP);
                lp.x = dp(MARGIN_RIGHT_DP);
                lp.y = dp(MARGIN_TOP_DP);
            }
            window.setAttributes(lp);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyWindowPosition();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyWindowPosition();
        handler.removeCallbacks(countdownRunnable);
        if (playing) return; // the adhan, then the dua, close the card
        secondsRemaining = TIMEOUT_SECONDS;
        if (timerView != null) {
            timerView.setText(getString(R.string.prayer_alert_dismiss, secondsRemaining));
        }
        handler.postDelayed(countdownRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(countdownRunnable);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Volume adjusts the adhan; any other button stops it and closes the card.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(keyCode, event);
        }
        finish();
        return super.onKeyDown(keyCode, event);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
