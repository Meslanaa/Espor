# MesOS 0.2 — MesOS Apps and MesOS-only Experience

Status: in progress. Delivered as an update through MesOS Settings → System →
MesOS Update (0.1.1 → 0.2); no reinstall.

## Goal

On the Google Play emulator, everything the user sees and uses is MesOS, with
Google Play kept for installing apps:

- MesOS provides its own everyday apps: **Camera, Photos, Files, Downloads,
  Calculator, Notes**.
- MesOS Home shows only MesOS apps, Google Play and apps the user installed.
  Preinstalled Android/Google apps are hidden (a switch brings them back).
- Android's replaced apps can be switched off on the emulator with a reversible
  script, so MesOS apps also become the handlers other apps use.

## What "Android removed, only Google Play kept" means in 0.2

MesOS is Android-based by design: the Linux kernel and the Android framework stay
underneath, and Google Play depends on them.

| Part | 0.2 on the Google Play emulator | Needs the MesOS ROM (AOSP build) |
| --- | --- | --- |
| Home screen, app drawer | MesOS | — |
| Camera, Photos, Files, Downloads, Calculator, Notes | MesOS apps | — |
| Visible app set | MesOS apps + Google Play + user apps | — |
| Replaced Google/Android apps | Hidden in MesOS; optionally disabled via `adb` (reversible) | Removed from the image |
| Status bar, notifications, quick settings, lock screen | Android | MesOS SystemUI (0.3–0.4) |
| Boot animation, Android Settings, permission dialogs | Android | MesOS overlays / framework changes |

Google Play system images are locked (no root, read-only system), so the right
column is impossible on them. It needs our own system image: a Linux host with
roughly 400 GB free and 64 GB RAM (see ARCHITECTURE.md → AOSP migration path).

## Architecture decisions

- **One APK stays the update unit.** All MesOS apps are Gradle library modules
  bundled into the MesOS Shell APK (`org.mesos.shell`), so 0.2 arrives through the
  existing updater. They split into separate APKs when MesOS becomes a ROM.
- **Modules**: `:apps:camera`, `:apps:photos`, `:apps:files`, `:apps:calculator`,
  `:apps:notes`, each owning its activities, strings and icon.
- **Separate tasks**: every app activity declares its own `taskAffinity`, so apps
  appear separately in Recents and never capture each other's launches (the 0.1
  Settings bug).
- **Component registry**: `core/MesOSApps` holds the activity class names so the
  launcher can pin MesOS apps without compile-time dependencies on them.
- **Shared UI**: `MesOSTopBar` and `PermissionGate` in `:core`.
- **Localisation**: English base strings plus Turkish (`values-tr`) for every
  module.

## Apps

### MesOS Camera
- CameraX `LifecycleCameraController` + `PreviewView` (tap to focus, pinch to zoom).
- Photo and video modes, front/back switch, flash off/auto/on for photos.
- Saves to `DCIM/MesOS` through MediaStore.
- Shows a thumbnail of the last capture; tapping it opens MesOS Photos.
- Answers `STILL_IMAGE_CAMERA` (camera role) and `ACTION_IMAGE_CAPTURE` (other
  apps asking for a photo, with or without `EXTRA_OUTPUT`).
- Permissions: `CAMERA`; `RECORD_AUDIO` only when recording video.

### MesOS Photos
- Grid of all photos and videos from MediaStore, newest first; Albums grouped by
  folder (Camera, Screenshots, Download, …).
- Full-screen viewer: swipe between items, pinch/double-tap zoom, video playback,
  share, delete (Android's confirmation on API 30+), details.
- Reloads automatically when media changes (content observer, debounced).
- Opens `image/*` and `video/*` from other apps (`ACTION_VIEW`), so it is the
  gallery for Camera and Files.
- Permissions: `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` (API 33+),
  partial access handled on API 34+, `READ_EXTERNAL_STORAGE` on API ≤ 32.

### MesOS Files and MesOS Downloads
- Home: storage usage and shortcuts (Downloads, DCIM, Pictures, Documents,
  Music, Movies), then the internal storage tree.
- Browse folders (folders first, name order), open files with the right app via
  `FileProvider`, share, rename, delete (confirmation), new folder, copy and move.
- "Downloads" is its own launcher entry that opens straight into `Download/`.
- Permission: "All files access" (`MANAGE_EXTERNAL_STORAGE`, granted by the user in
  Android's settings) on API 30+; storage permissions on older versions.

### MesOS Calculator
- `+ − × ÷ %`, parentheses, decimals, sign change, backspace, clear.
- Exact `BigDecimal` arithmetic and a live result; errors such as division by
  zero are shown instead of crashing. Evaluator unit-tested on the JVM.

### MesOS Notes
- List of notes (latest edited first), search, create, edit with autosave, delete
  with confirmation. Stored in a private SQLite database, which survives reboots
  and updates.

## Launcher and Settings changes

- Dock: Camera, Photos, Google Play, MesOS Settings.
- Home grid: Files, Downloads, Calculator, Notes (plus Phone, Messages and Browser
  when Android apps are shown).
- Drawer (MesOS mode): MesOS apps, Google Play and user-installed apps only.
- MesOS Settings → **Apps** becomes a MesOS page: "Show Android apps" switch
  (persisted) and a link to Android's app management.

## Emulator helper (optional, reversible)

`scripts/mesos-emulator-mode.ps1` runs `adb shell pm disable-user --user 0` on the
Google/Android apps that MesOS 0.2 replaces (Photos, Files by Google, Camera,
Calculator, Keep) and `-Restore` re-enables them. It never touches system
components (System UI, Play services, Play Store, WebView, the system file picker).

## Out of scope for 0.2 (planned)

- Phone, Messages, Contacts (need the dialer and SMS roles): 0.3.
- Clock with alarms, Music player, Recorder, Browser: 0.3+.
- Status bar, quick settings, lock screen, boot animation: need the ROM.

## Verification

- CI: all modules build (debug and release), unit tests for the calculator
  evaluator, launcher filtering, file helpers and the updater.
- Emulator checklist (docs/TESTING.md): update 0.1.1 → 0.2 from MesOS Settings;
  each app's main flows; permissions denied and granted; reboot persistence.
