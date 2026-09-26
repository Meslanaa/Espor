# MesOS Architecture

## Target architecture

```
Linux kernel + Android hardware abstraction
        ↓
Android framework (AOSP)
        ↓
MesOS system layer      product config, ro.mesos.* properties, framework overlays
        ↓
MesOS SystemUI          status bar, quick settings, lock screen (needs a system image)
        ↓
MesOS Launcher          home, app drawer
        ↓
MesOS Settings          MesOS pages integrated with Android Settings
        ↓
MesOS system apps
        ↓
MesOS design system
```

Android applications keep working unmodified unless a MesOS feature deliberately
changes behaviour.

## Where 0.x sits

MesOS 0.x (0.3 "Aurora" now) is a **userland on stock Android**: the MesOS components are
built with Gradle into one APK, installed on the emulator's existing system image
and chosen by the user as the home app. This validates code, UX and the update flow
cheaply. Nothing in the Android system image is modified yet.

This is deliberate. A full AOSP build does not fit the current development machine
(see [AOSP migration path](#aosp-migration-path)), and building MesOS components as
self-contained Android apps is also how they will later be dropped into an AOSP tree.

## Modules

| Module | Type | Responsibility |
| --- | --- | --- |
| `:core` | Android library | Release identity (`MesOSRelease`, `ReleaseChannel`), log tags (`MesOSLog`), preferences (`MesOSPreferences`), roles (`HomeRole`), cross-module intents (`MesOSIntents`, `MesOSApps`), search text helpers, Aurora design system (`MesOSTheme`, fonts, palette, glyphs, shared components, wallpaper renderer and live wallpaper) |
| `:launcher` | Android library | `HomeActivity` (HOME): pages, dock, folders, drag and drop, MesOS and Android widgets (`AppWidgetHost`), drawer, universal search, control and notification center (`NotificationListenerService`) |
| `:settings` | Android library | `SettingsActivity` (MesOS pages with search, hand-offs to Android settings, About MesOS, MesOS Update) and `SetupActivity` (setup wizard) |
| `:updater` | Android library | `UpdateController`: manifest fetch, `UpdatePolicy`, SHA-256, `ApkVerifier`, `PackageInstaller` session; daily background check (`JobScheduler`) with a notification |
| `:apps:camera` | Android library | MesOS Camera (CameraX): photo/video, `ACTION_IMAGE_CAPTURE` |
| `:apps:photos` | Android library | MesOS Photos: MediaStore gallery, albums, viewer, editor (crop, rotate, filters; saves a copy), `ACTION_VIEW` for images/videos |
| `:apps:files` | Android library | MesOS Files and Downloads: file manager with all-files access, `FileProvider` |
| `:apps:calculator` | Android library | MesOS Calculator: `BigDecimal` expression engine (also used by search) |
| `:apps:notes` | Android library | MesOS Notes: SQLite notes |
| `:apps:clock` | Android library | Alarms (`AlarmManager` exact alarms, full-screen ring), timer, stopwatch, world clock |
| `:apps:calendar` | Android library | MesOS event store, month and agenda views, reminders |
| `:apps:weather` | Android library | Open-Meteo forecast over HTTPS (no key), saved cities |
| `:apps:music` | Android library | MediaStore library, Media3 playback service |
| `:apps:recorder` | Android library | Voice recorder and screen recorder (`MediaProjection`, consent every time) |
| `:apps:scanner` | Android library | QR/barcode scanner (CameraX + ZXing); content shown before any action |
| `:apps:contacts` | Android library | Android contacts through `ContactsContract` |
| `:apps:phone` | Android library | Dial pad, call log, `InCallService` call screen (DIALER role) |
| `:apps:messages` | Android library | SMS conversations, sending, receiving and quick reply (SMS role) |
| `:apps:browser` | Android library | WebView browser: tabs, bookmarks, history, downloads (BROWSER role) |
| `:apps:care`, `:apps:tips` | Android library | Device Care (battery, storage, memory, security) and Tips |
| `:shell` | Android application `org.mesos.shell` | Bundles the modules into the deployable MesOS userland APK; signing and app identity |

Dependencies: `shell → everything`; every module → `core`; `settings, launcher →
updater`; `launcher → calculator, notes, weather, calendar` (search and widgets);
`phone, messages → contacts` (names and photos). Otherwise modules reach each other
through `MesOSApps` (activity class names) and `MesOSIntents`, never through
compile-time dependencies. Every app activity has its own `taskAffinity`, so each
app is a separate task (and separate card in Recents).

Pure logic (home layout, alarm times, recurrence, weather parsing, colour and crop
maths, URL policy, QR payloads, dial pad, MMS headers, update policy) lives in plain
Kotlin files without Android imports and is covered by JVM unit tests.

### Why one APK in 0.x

- one version number describes the whole MesOS userland (`mesos.properties`);
- one update unit keeps the prototype updater simple and testable;
- one signing identity, so Android's signature-continuity check protects updates.

Splitting into separate APKs (e.g. `MesOSLauncher`, `MesOSSettings`) later means
adding application modules; the feature modules do not change.

### Persistence

| Data | Store | Survives reboot / update |
| --- | --- | --- |
| Appearance, accent, wallpaper, icon shape, "Show Android apps", setup done | `MesOSPreferences` (SharedPreferences) | Yes |
| Home layout (pages, dock, folders, widgets) | Versioned JSON file in app storage, written atomically | Yes |
| Notes, calendar events, alarms, browser bookmarks and history | Private SQLite / SharedPreferences per app | Yes |
| Contacts, call log, SMS | Android's providers (owned by Android) | Yes |
| Last update check, last seen version, pending release notes | `UpdatePreferences` (SharedPreferences) | Yes |
| Default home, phone, SMS and browser apps | Android `RoleManager` (owned by Android) | Yes |

## Release identity flow

```
mesos.properties ──► root build.gradle.kts (parse + validate)
                         ├─► :core BuildConfig.MESOS_*  ──► MesOSRelease.current
                         └─► :shell versionCode / versionName
MESOS_BUILD_NUMBER env ──► BuildConfig.MESOS_BUILD_NUMBER (default "local")
```

In a ROM build the same identity will be published as read-only system properties
(`ro.mesos.version`, `ro.mesos.version_code`, `ro.mesos.channel`, …) from the MesOS
product configuration. `MesOSRelease.current` is the only place that will need to
change.

## Logging

Tags: `MesOS` (system-wide), `MesOSLauncher`, `MesOSSettings`, `MesOSUpdater`,
defined in `MesOSLog`. Debug-level logging is suppressed in release builds via
`BuildConfig.DEBUG`. Log state changes and failures only — no per-frame or polling
logs.

## Design system

Aurora (0.3) lives in `:core`: bundled Sora and Manrope fonts (SIL OFL), six accent
colours with light and dark schemes, squircle shapes, glass surfaces, a stroke glyph
set, shared components (large-title list screens, grouped rows, cards, empty
states, search field) and the animated Aurora wallpaper (also available as an
Android live wallpaper). MesOS does not use wallpaper-derived dynamic colour, to
keep its own identity.

## Performance rules

- No polling loops; react to system broadcasts/callbacks (e.g. package changes).
- Query installed apps once; reload only when Android reports a package change.
- No network or disk I/O on the main thread.
- No long-running services without a user-visible purpose.

## Google Play

MesOS stays an Android system so Google Play apps keep working:

- **MesOS 0.x (now):** MesOS runs on a stock emulator image. With a *Google Play*
  system image, Play Store, Play Services and every app work exactly as on stock
  Android; MesOS Home simply replaces the home screen.
- **MesOS ROM (later):** Google's apps (GMS) may only be preinstalled on devices
  certified by Google, so a self-built ROM cannot ship Play Store preinstalled.
  Like other community ROMs, MesOS would let the user add Google apps afterwards
  (e.g. a separately flashed GApps package) or use microG. Some apps that demand
  strong Play Integrity (banking, some games) may refuse to run on any custom ROM.

## Installing MesOS

| Stage | Emulator | Real phone |
| --- | --- | --- |
| 0.x (now) | Install the release APK, set MesOS as home, update from MesOS Settings | Same APK works on any Android 8.0+ phone; nothing is flashed |
| ROM (after the AOSP migration) | Build an emulator system image (`emu_img_zip`) and select it as a custom system image in Android Studio | Unlock the bootloader (erases data), flash MesOS images with `fastboot`, then update over the air |

For real Pixel devices, Google stopped publishing Pixel device trees in AOSP in
2025, so phone support would build on community device trees (e.g. LineageOS).
Re-check the current situation at migration time.

## AOSP migration path

When hardware allows, MesOS moves from "app on stock Android" to a real ROM:

1. **AOSP source** for a current release, targeting the emulator first
   (`sdk_phone64_x86_64`-style product, then Cuttlefish/real devices).
2. **MesOS product configuration** (e.g. `vendor/mesos/`): product makefiles that
   inherit the base product, set `ro.mesos.*` properties and `PRODUCT_PACKAGES`.
3. **MesOS apps as system apps**: the Gradle-built APKs imported with
   `android_app_import` (or rebuilt with `Android.bp`), installed as privileged
   apps; MesOS Launcher `overrides` Launcher3.
4. **Overlays (RROs)** for framework and SystemUI resources: colors, shapes,
   default wallpaper, boot animation (`bootanimation.zip`).
5. **Settings integration**: MesOS pages injected into / replacing parts of Android
   Settings rather than duplicating the whole Settings app.
6. **SystemUI and framework changes** as source patches, kept small and rebased
   per Android release.
7. **Signed OTA builds** via `update_engine` (see [UPDATES.md](UPDATES.md)).

Resource reality (to be re-checked against current AOSP documentation at migration
time, since Google's AOSP publishing model has been changing):

- **Linux host required** (Ubuntu LTS); Windows is not a supported AOSP build host.
- **Disk:** on the order of **400 GB free** (≈ 250 GB source checkout + ≈ 150 GB
  build output). The current ≈ 100 GB free on the development PC is not enough.
- **RAM:** 64 GB recommended; 32 GB is workable only with reduced build parallelism.
- **Time:** first full build takes hours even on a strong machine.

Until then, an intermediate step is possible without AOSP: running the emulator with
`-writable-system` and installing MesOS as a privileged system app and boot
animation on an existing Google APIs (non-Play) image. It is fragile and only for
experiments; it does not replace the ROM path.
