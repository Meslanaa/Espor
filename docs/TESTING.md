# Testing MesOS on the Android Emulator

This is the manual verification procedure for MesOS 0.1 (Phase 5). It needs only
Android Studio's emulator and a web browser; no local build is required.

## 0. Emulator

- Use an AVD whose system image says **Google Play** (Device Manager → the AVD's
  "Play Store" column, or the image name). Google Play works unchanged under MesOS
  0.x because MesOS runs on top of the stock image.
- A Google APIs image (no Play Store) also works; the Play Store tile is then simply
  absent from MesOS Home.
- Tested target: Pixel 8 Pro AVD, Android API 37, x86_64.

## 1. First install (once)

1. Open the latest release: <https://github.com/Meslanaa/Espor/releases/latest>.
2. Download `mesos-shell-<version>.apk` to your PC.
3. Drag the APK file onto the running emulator window. Android installs it.
   (Alternative: `adb install mesos-shell-<version>.apk`.)
4. In the emulator's app list open **MesOS Settings** and tap **Set as home**, then
   choose **MesOS**. (Alternative: Android Settings → Apps → Default apps → Home app.)
5. Press Home: MesOS Home appears.

Every later version is installed from MesOS itself (step 4 below). Do not reinstall
by hand unless something is broken.

## 2. Checklist

| # | Check | Expected |
| --- | --- | --- |
| 1 | Press Home | MesOS Home: time, date, pinned apps, dock |
| 2 | Tap apps on Home and in the dock | They open (Phone, Messages, Chrome, Play Store, …) |
| 3 | Swipe up on Home (or tap **Apps**) | App drawer with every launchable app, sorted by name |
| 4 | Type in the drawer search | List filters; tapping launches the app |
| 5 | Swipe down at the top of the drawer, or Back | Drawer closes |
| 6 | Install any app from Play Store | It appears in the drawer without restarting MesOS |
| 7 | Dock → MesOS Settings | MesOS Settings opens |
| 8 | Network / Sound / Apps / Storage | The matching Android settings screen opens |
| 9 | About MesOS | MesOS version, Android base, build, security patch, device, update status |
| 10 | Display → Dark, press Home | Home and Settings switch to dark |
| 11 | Reboot the emulator (Device Manager → ⋮ → Cold Boot, or hold power → Restart) | MesOS Home starts after boot; Display is still Dark |
| 12 | System → MesOS Update → Check for updates | "MesOS is up to date." or an available update |

## 3. Logs

```sh
adb logcat -s MesOS MesOSLauncher MesOSSettings MesOSUpdater
```

Report any crash with the output of `adb logcat -b crash -d`.

## 4. Update test (0.1 → 0.1.1)

Precondition: MesOS 0.1 installed as above and a 0.1.1 release published.

1. MesOS Settings → System → MesOS Update → **Check for updates**.
   Expected: "MesOS 0.1.1 — Update system verified." with the download size.
2. Tap **Download and install**.
   - First time only: Android asks to allow installs from MesOS → **Allow MesOS
     updates** → enable the switch → go back → tap **Download and install** again.
3. Progress, then "Verifying update (SHA-256 and signature)…".
4. Android shows its **Update** confirmation dialog → confirm.
5. MesOS restarts (Home reappears).
6. MesOS Settings → System → MesOS Update shows "MesOS was updated from 0.1 to 0.1.1"
   and the release notes; About MesOS shows **0.1.1 Developer Preview**.
7. Repeat checklist items 1, 3, 10 and 11: the theme choice survived the update.

What this updates: the MesOS Shell APK (Home, Settings, Updater). What it does not
update: Android, its security patch level, or anything else in the system image.
