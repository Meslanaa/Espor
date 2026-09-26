# Testing MesOS on the Android Emulator

Manual verification on Android Studio's emulator. No local build is needed: MesOS
is installed once from a GitHub release and then updated from MesOS Settings.

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

Every later version is installed from MesOS itself (MesOS Settings → System →
MesOS Update). Do not reinstall by hand unless something is broken.

## 2. MesOS 0.2 checklist

| # | Check | Expected |
| --- | --- | --- |
| 1 | MesOS Settings → System → MesOS Update → Check → Download and install | 0.2 installs; "MesOS was updated from 0.1.1 to 0.2" with release notes |
| 2 | Press Home | Clock, date; home grid Files, Downloads, Calculator, Notes; dock Camera, Photos, Play Store, Settings |
| 3 | Swipe up | Drawer lists only MesOS apps, Play Store and apps you installed |
| 4 | Settings → Apps → Show Android apps: on, then off | Android apps appear in the drawer (and Phone, Messages, Browser on Home), then disappear |
| 5 | Camera: allow access, take a photo | Short white flash; thumbnail appears; tapping it opens the photo in MesOS Photos |
| 6 | Camera: Video, allow microphone, record 5 s, stop | Timer runs while recording; thumbnail shows the video |
| 7 | Camera: flash button, switch camera | Flash cycles Off → Auto → On; front camera preview |
| 8 | Photos: allow access | Grid shows the new photo and video, newest first; Albums tab shows "MesOS" |
| 9 | Photos: open, pinch/double-tap zoom, swipe, play the video | Zoom and pan work; swiping changes items when not zoomed; video plays |
| 10 | Photos: Share, Details, Delete (confirm in Android's dialog) | Share sheet opens; details show size/date; item disappears after delete |
| 11 | Files: "Allow access to all files" → enable for MesOS → back | Storage overview and folder shortcuts appear |
| 12 | Files: open DCIM → MesOS; tap a photo | Opens in MesOS Photos |
| 13 | Files: new folder, rename, copy/move a file into it, delete it | Each action works; a folder can't be moved into itself |
| 14 | Downloads (home screen) | Opens directly in the Download folder; Back leaves the app |
| 15 | Calculator: `12.5 × 4 − 10 =`, `1 ÷ 0 =`, `( )`, `%` | 40; "Can't divide by zero"; parentheses and percent work |
| 16 | Notes: +, write, back; search; open, edit, delete | Note is saved, found by search, updated, deleted |
| 17 | Settings → Display → Dark; reboot the emulator | After reboot: MesOS Home, dark theme, notes still there |
| 18 | Recents (□) | Camera, Photos, Files, Settings… are separate cards |

Device language Turkish → all MesOS text is Turkish.

## 3. Optional: MesOS-only emulator

`scripts/mesos-emulator-mode.ps1` switches off the Google/Android apps MesOS
replaces (Photos, Files by Google, Camera, Calculator, Keep) so that other apps
also open photos, files and the camera with MesOS. Undo with `-Restore`.

```powershell
powershell -ExecutionPolicy Bypass -File .\mesos-emulator-mode.ps1
powershell -ExecutionPolicy Bypass -File .\mesos-emulator-mode.ps1 -Restore
```

## 4. Logs

```sh
adb logcat -s MesOS MesOSLauncher MesOSSettings MesOSUpdater
adb logcat -b crash -d
```

Report a crash with the second command's output.

## 5. Update test (history)

MesOS 0.1 → 0.1.1 was the first update delivered through MesOS Update (verified on
the emulator). Every release since arrives the same way. What an update changes:
the MesOS Shell APK (Home, Settings, MesOS apps, Updater). What it does not change:
Android, its security patch level, or anything else in the system image.
