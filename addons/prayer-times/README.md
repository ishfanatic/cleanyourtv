# Prayer Times

Today's five prayer times and sunrise for one city, full screen on the TV, with the next prayer highlighted and a
countdown to it, plus today's Gregorian and Hijri dates. Each prayer has its Arabic and English name. For the
[Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md), so once you answer
**Show card** its tile reads "Prayer Times" with a green glow; on any other Android TV home it is an ordinary app.

![The prayer times page](https://raw.githubusercontent.com/majnioui/cleanyourtv-contrib/pr-assets/addon-prayer-times/prayer-times.png)

- **Data** comes from [Aladhan](https://aladhan.com/), which needs no account or key.
- **The city and the calculation method** are set before you build, in `app/src/main/res/values/config.xml`:
  `latitude`, `longitude`, `place` (the name shown) and `method`, Aladhan's
  [method number](https://aladhan.com/calculation-methods) (2 ISNA, 3 Muslim World League, 4 Umm al-Qura,
  5 Egyptian, 13 Turkey, 21 Morocco...). The default is Casablanca with the Morocco method.
- **The Asr school** is `school`, Aladhan's juristic school for Asr: `0` Shafi (the default) or `1` Hanafi.
  Hanafi users see the later Asr time; leave it at `0` and nothing changes.
- **Reminders switch**: In `app/src/main/res/values/config.xml`, `reminders` controls whether to schedule
  on-screen alert cards and notifications when a prayer time arrives. It is `false` (off) by default. When
  enabled (`true`), an unobtrusive, frameless compact card appears at the top-right corner of the screen
  displaying the localized prayer name, Arabic script, time, and city, accompanied by a single notification chime.
  It dismisses automatically after 10 seconds or immediately on any remote button press.
- It reads Aladhan when the page opens and again after midnight. The countdown updates every minute.
  Offline, the last times stay on screen.
- Times follow the TV's own 12- or 24-hour setting. Localized prayer names and strings for English,
  Indonesian (`values-in`), Italian (`values-it`) and Arabic (`values-ar`).

## Optional: adhan, iqamah, Jumuʿah, Ramadan and the Home line

Everything below is off in `config.xml` until you turn it on, and all of it but the Home line needs `reminders`.

| Setting | Off | What it does when set |
|---|---|---|
| `adhan` | `false` | At each prayer the card plays an adhan from the add-on's folder on the TV, `/sdcard/Android/data/com.majnioui.tv.prayer/files/`: `adhan_fajr.mp3`, `adhan_dhuhr.mp3`, `adhan_asr.mp3`, `adhan_maghrib.mp3`, `adhan_isha.mp3`, or one `adhan.mp3` for any prayer without its own. It asks other apps to pause, Volume adjusts it, any other button stops it, and the card stays until it ends. Without a file the chime plays as before. No recording ships with the add-on: push your own. |
| `adhan_dua` | `true` | After the adhan, a centred panel shows the dua for after the adhan (Ṣaḥīḥ al-Bukhārī 614) in Arabic with its English meaning. `dua_ar` then `dua_en` (`.mp3` or `.m4a`) in the same folder are recited while it shows. |
| `adhan_wake` | empty | Prayers that turn the TV on from standby for the adhan, e.g. `Dhuhr,Asr,Maghrib,Isha`. The others play only when the TV is already on. |
| `adhan_wake_volume` | `-1` | The volume for an adhan that woke the TV; your own level comes back after the dua. |
| `fajr_volume` | `-1` | The volume for the Fajr adhan when the TV is already on. |
| `wake_standby_minutes` | `0` | After an adhan woke the TV, a small card counts down and the TV goes back to standby unless a button is pressed. Needs the standby service switched on (below). |
| `iqamah_minutes` | empty | Minutes from each adhan to the iqamah, Fajr to Isha, e.g. `20,15,15,5,15`: the page and the Home line count down to the iqamah in between. |
| `heads_up_minutes` | `0` | A quiet card this many minutes before each prayer, only while the TV is on. |
| `jumuah_time` | empty | Fridays: Dhuhr reads Jumuʿah, at this time (`13:30`) instead of the calculated one. |
| `kahf_time` | empty | Fridays: a card at this time reminding to read Sūrat al-Kahf, while the TV is on. |
| `ramadan` | `off` | `auto` follows Aladhan's Hijri month, `on` forces it: "Suhoor ends" before Fajr, "Iftar" before Maghrib, and the iftar dua (Sunan Abī Dāwūd 2357) after the Maghrib adhan, recited from `dua_iftar_ar` and `dua_iftar_en` if they are there. |
| `home_hijri` | `false` | The Home line shows today's Hijri date in place of the city. |

Push recordings with ADB, for example:

```bash
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push my-adhan.mp3 /sdcard/Android/data/com.majnioui.tv.prayer/files/adhan.mp3
```

(`tools/b64push.py` for files over a few MB.)

**The Home line.** The add-on publishes the next prayer through the launcher's `game_day` contract
([docs/modules.md](../../docs/modules.md)): add `"game_day": "com.majnioui.tv.prayer"` to a theme's `config.json`
(or `config.xml`) and Home shows "Casablanca · 13:29 / Dhuhr in 2h 5m" under the clock, then the iqamah, then the
next prayer. The provider is read-only and holds only what the page shows.

**Back to standby** uses `StandbyService`, an accessibility service that asks for no events, reads no window
content and filters no keys; its one job is Android's lock-screen action, which on a TV is standby. Switch it on
only if you use `wake_standby_minutes`:

```bash
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 's=$(settings get secure enabled_accessibility_services); settings put secure enabled_accessibility_services "${s:+$s:}com.majnioui.tv.prayer/.StandbyService"; settings put secure accessibility_enabled 1'
```

On TCL sets the firmware will not bind a sideloaded app's service without its own auto-start app-op (the log says
`[TclAppBoot] forbid bind service`); grant it as `launcher/build-and-install.sh` does for the launcher:

```bash
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'cmd appops set com.majnioui.tv.prayer AUTO_START allow; cmd appops set com.majnioui.tv.prayer APP_AUTO_START allow; appops write-settings'
```

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Aladhan: today's times for the configured coordinates. |
| `SCHEDULE_EXACT_ALARM` | Exact alarm scheduling for prayer time reminders. |
| `RECEIVE_BOOT_COMPLETED` | Reschedules prayer alarms automatically when the TV boots. |
| `WAKE_LOCK` | Briefly wakes the screen to deliver the on-screen alert card. |
| `SYSTEM_ALERT_WINDOW` | Displays the unobtrusive top-right alert card over other apps and video playback on Android 10+. |

That is all. What is sent: today's date, the latitude and longitude from `config.xml`, the method number and the
Asr school number. No account, no telemetry, no ads, no background service, no location permission. Backups are off.

Two exported pieces, both optional: `StandbyService` (an accessibility service, off until you enable it, see above)
and `NextPrayerProvider`, a read-only content provider the launcher reads only when a theme names the add-on in
`game_day`; it holds the next prayer's name and time and today's Hijri date, nothing else.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/prayer-times && ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

If `reminders` is set to `true`, grant the overlay permission so the on-screen alert card can display over playing video and other apps:

```bash
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'appops set com.majnioui.tv.prayer SYSTEM_ALERT_WINDOW allow'
```

On Android 14+ (API 34), exact alarms are off by default. The add-on safely falls back to `setAndAllowWhileIdle`, or exact timing can be explicitly granted via:

```bash
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'appops set com.majnioui.tv.prayer SCHEDULE_EXACT_ALARM allow'
```

> **Note:** Force-stopping the app (`am force-stop com.majnioui.tv.prayer`) removes all pending alarms scheduled in Android's `AlarmManager`. When testing real alarms, do not force-stop the app.

Press Home: the launcher asks once whether to show the card. Change the answer later by holding OK on
Settings, then Module cards.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Credits

Prayer icons: [Lucide](https://lucide.dev) (ISC) and [Feather](https://feathericons.com) (MIT). Font: Outfit
(SIL Open Font License, `FONT-LICENSE-OFL.txt`).

## Tested on

- Xiaomi TV (MiTV-MOOR2), Google TV 11, RTM5.220609.003: the page, the countdown, the card prompt and the card shown in the launcher's dock.
- TCL 43A50 (Smart TV Pro), Android 11 (build RP1A.200622.001): background prayer alarms, top-right unobtrusive alert card over active apps and home screen, and auto-dismiss after 10 seconds.
- TCL Smart TV Pro (G17, G17_4K_US), Google TV 14, firmware UTT2.250416.001 AU27: per-prayer adhan with the dua
  and its recitation, waking from standby (the player waits for the speakers to unmute) and back to standby, the
  wake and Fajr volumes, iqamah, heads-up, Jumuʿah and Kahf cards, Ramadan and the iftar dua (Friday and Ramadan
  forced in a test build), the Home line through `game_day`, and 12-hour times from the TV's own setting.

## Maintainer

[@majnioui](https://github.com/majnioui)
