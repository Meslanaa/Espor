# MesOS 0.3 "Aurora" — everything MesOS can be on stock Android

Status: in progress. Delivered as one update through MesOS Settings → MesOS
Update (0.2 → 0.3); no reinstall.

## Goal

One release that takes MesOS as far as it can go while it runs as an app on the
Google Play emulator, so that the next big step is the move to Linux:

- A new visual identity (Aurora): typography, icons, colours, glass surfaces,
  motion, live wallpaper. Design reference:
  the "MesOS Aurora Concept" canvas (home, control center, settings, setup, icons).
- A system experience around the home screen: pages, widgets, folders, drag and
  drop, universal search, control and notification center, setup wizard,
  Settings 2.0, background update checks.
- A complete set of everyday MesOS apps, so the emulator is usable with MesOS apps
  and Google Play only.

## What stays Android (needs a MesOS system image)

| Part | Why it cannot change in 0.3 |
| --- | --- |
| Status bar, Android's notification shade, quick settings panel | SystemUI is a privileged system app |
| Lock screen, boot animation, power menu | System image / framework |
| Recents screen | Owned by the system launcher (Quickstep) on Android 10+ |
| Permission and install dialogs | PermissionController / PackageInstaller |
| Turning Wi-Fi / Bluetooth on or off directly | Blocked for apps since Android 10 / 13; MesOS opens Android's panel |
| System language, system dark mode | Need system permissions; MesOS has its own language and theme |

The MesOS control center opens from MesOS Home (swipe down) and complements
Android's shade instead of replacing it.

## Scope

### Aurora design system (`:core`)
- Bundled fonts (SIL OFL): Sora (clock, titles), Manrope (UI text).
- Accent colours: Indigo (default), Teal, Green, Orange, Rose, Violet; light and
  dark schemes derived from the accent.
- Squircle shapes, glass surfaces, shared components (large-title screens,
  grouped rows with coloured icons, search field, buttons, empty states).
- Stroke glyph set for the UI (control toggles, settings rows, widgets).
- Motion: spring animations, finger-following sheets, haptic feedback.

### Icons
- Every MesOS app gets an Aurora adaptive icon (gradient + white glyph) with a
  monochrome layer for themed icons.
- MesOS Home draws every app icon (Play Store and user apps too) in the chosen
  shape: squircle, circle or rounded square; optional themed (tinted) icons.

### MesOS Home 2.0 (`:launcher`)
- Wallpapers: Aurora (animated, rendered by MesOS), static Aurora variants, or
  Android's wallpaper. The Aurora live wallpaper can also be set as Android's
  wallpaper so it shows on the lock screen.
- Home pages on a 4 × 6 grid, dock, page indicator, search pill.
- Edit mode: long press, drag and drop across pages and into the dock, folders,
  remove, add pages; layout persisted and migrated safely.
- Widgets: MesOS Clock, Weather, Agenda, Notes, Music, Battery; Android app
  widgets through `AppWidgetHost`.
- App drawer: search, suggestions (recent apps), alphabetical grid.
- Universal search: apps, calculator results, settings, contacts, notes, files,
  web search.
- App menu: app shortcuts, app info, uninstall, add/remove from Home.
- Notification badges; apps open with a scale-up animation from their icon.
- Control and notification center (swipe down): Wi-Fi, Bluetooth, flashlight, Do
  Not Disturb, auto-rotate, location, battery saver, MesOS dark theme, screen
  recording, QR scanner, brightness and volume sliders, media controls, grouped
  notifications with reply and dismiss. Uses `NotificationListenerService`
  (the user grants notification access once).

### Settings 2.0 and setup (`:settings`, `:updater`)
- Setup wizard on first run and after the 0.3 update: language (MesOS app
  language, Android 13+), appearance, permissions, default apps, wallpaper.
- Settings: search, appearance, home screen, notifications, apps and default apps,
  privacy and special access, language, device care, update, about.
- Background update check (JobScheduler, once a day, network required) with a
  notification. Installing still needs the user's confirmation.

### New MesOS apps (one module each under `apps/`)
| App | Scope |
| --- | --- |
| Clock | Alarms (exact alarms, full-screen ring, snooze, reschedule after reboot), timer, stopwatch, world clock |
| Calendar | MesOS event store, month and agenda views, reminders, simple repeats |
| Weather | Open-Meteo (HTTPS, no API key), current location or saved cities, hourly and 10-day forecast |
| Music | Local library (MediaStore), Media3 playback service, notification and lock screen controls |
| Recorder | Voice recorder (foreground service) and screen recorder (MediaProjection) |
| Scanner | QR and barcode scanner (CameraX + ZXing), also from an image |
| Contacts | Android contacts: list, search, favourites, details, create/edit/delete |
| Phone | Dial pad, favourites, call log, in-call screen (`InCallService`) when MesOS is the default phone app |
| Messages | SMS conversations, send/receive, notifications with reply when MesOS is the default SMS app (MMS not supported yet) |
| Browser | WebView tabs, bookmarks, history, downloads, default browser role |
| Device Care | Battery, storage, large files, memory, security patch level |
| Tips | Short guide to MesOS features |

### Existing apps
Camera, Photos, Files, Downloads, Calculator and Notes move to the Aurora look;
Photos gets an editor (crop, rotate, flip, adjustments, filters; saves a copy).

## Architecture decisions

- **One APK stays the update unit** (`org.mesos.shell`); every app is a Gradle
  library module bundled into it.
- **Pure logic is separate from Android code** (home layout, alarm scheduling,
  recurrence, weather parsing, QR payloads, colour matrices, search ranking) and
  covered by JVM unit tests. This is also the first step towards Linux.
- **Dependencies**: Media3 (music playback), ZXing core (barcode decoding). No
  other new libraries; background work uses JobScheduler and AlarmManager.
- **Roles, not tricks**: Phone, Messages, Browser and Home ask for Android roles
  through `RoleManager`; nothing bypasses Android's permission model.
- **Security**: no new exported component without the system permission that
  protects it (SMS/WAP receivers, `InCallService`, notification listener,
  wallpaper, job service); PendingIntents are immutable unless they carry a
  RemoteInput reply; the browser never exposes JavaScript interfaces and
  sanitises `intent:` links.

## Verification

- CI: debug and release builds, lint (release), unit tests on every push.
- CI emulator smoke test: installs the debug build on an Android emulator, makes
  it the home app, opens every MesOS activity, collects screenshots and fails on
  any crash.
- Manual: the 0.3 checklist in [TESTING.md](../TESTING.md) on the developer's
  emulator after updating from 0.2.
