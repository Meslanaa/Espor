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

## 2. MesOS 0.3 checklist

| # | Check | Expected |
| --- | --- | --- |
| 1 | MesOS Settings → MesOS Update → Check → Download and install | 0.3 installs; "MesOS was updated from 0.2 to 0.3" with release notes |
| 2 | Press Home | The MesOS setup wizard opens: Welcome, Style, Permissions, Home, Done |
| 3 | Setup → Style: pick dark/light, an accent colour and a wallpaper | The wizard and later MesOS Home use them |
| 4 | Setup → Permissions: allow notifications, notification access | Each row turns to "Allowed"; everything can be skipped |
| 5 | Finish setup | MesOS Home: animated Aurora wallpaper, clock and weather/agenda widgets, dock |
| 6 | Swipe left/right; swipe up | Second page with more apps; app drawer with search and recent apps |
| 7 | Long press Home → edit; drag an app onto another, into the dock, to another page | Folder is created; dock and pages change; layout is kept after reboot |
| 8 | Edit mode → Widgets → add Music, Notes, Battery or an Android widget | Widget appears and can be removed |
| 9 | Tap the search pill; type `12*4`, an app name, a contact, a note | Calculator result 48; apps, contacts, notes, settings and web search listed |
| 10 | Swipe down on Home | Control center: toggles, brightness, volume, media, notifications (reply, dismiss) |
| 11 | Control center → Screen record; stop from the notification | Android asks for permission every time; the video appears in Recorder and Photos |
| 12 | Settings: search "wallpaper"; Appearance → icon shape; Language → Türkçe | Search finds the page; icons change shape; MesOS switches to Turkish |
| 13 | Phone → set as default phone app; dial a number with the dial pad | Call screen opens (emulator: use Extended controls → Phone to call in) |
| 14 | Messages → set as default SMS app; Extended controls → Phone → send SMS | Notification with Reply; the conversation shows the message; reply works |
| 15 | Contacts: create, edit, favourite, delete | Changes show in Contacts, Phone and search |
| 16 | Browser → set as default; open a site, new tab, bookmark, history, download a file | Tabs, bookmarks and history work; the download appears in Downloads |
| 17 | Clock: alarm in 1 minute; timer; stopwatch; world clock | Alarm rings full screen with snooze; timer notifies |
| 18 | Calendar: new event with a reminder; Weather: allow location or add a city | Reminder notification; forecast shows hourly and 10 days |
| 19 | Music: play a song; lock the screen | Plays in the background; controls in the notification and control center |
| 20 | Recorder: record 5 s, play, rename, delete; Scanner: scan a QR code | Recording plays; scanned content is shown before anything opens |
| 21 | Photos: open a photo → Edit → crop 1:1, rotate, Noir filter, brightness → Save copy | A new photo appears next to the original; the original is unchanged |
| 22 | Device Care; Tips | Battery, storage, memory and security score; tips open the right apps |
| 23 | Reboot the emulator | MesOS Home, theme, wallpaper, layout, notes, alarms and events kept |
| 24 | Recents (□) | Every MesOS app is a separate card |

Device language Turkish, or Settings → Language → Türkçe → all MesOS text is Turkish.

The emulator test covers the same screens automatically on every push (see
`scripts/ci/smoke-test.sh`): it installs the debug build, finishes setup, opens
every MesOS app and fails on any crash.

## 3. Optional: MesOS-only emulator

`scripts/mesos-emulator-mode.ps1` switches off the Google/Android apps MesOS
replaces (Photos, Files by Google, Camera, Calculator, Keep, Clock, Contacts) so
that other apps also open photos, files, the camera, alarms and contacts with MesOS. Phone, Messages and Chrome stay installed; choose MesOS Phone, Messages and
Browser as default apps instead. Undo with `-Restore`.

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
