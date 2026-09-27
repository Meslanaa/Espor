# MesOS Roadmap

The roadmap is flexible: a feature moves to a later release if the architecture under
it is not stable yet. Rule: **first make MesOS boot and work, then improve it.**

## MesOS 0.1 — Bootstrap (done)

| Phase | Scope | State |
| --- | --- | --- |
| 0 — Environment audit | Tools, disk, emulator; build route | Done (cloud session + Studio Otter / API 37 AVD reported by the developer) |
| 1 — Bootstrap | Gradle project, version single source of truth, `:core`, `:shell`, docs, CI | Done |
| 2 — Launcher | MesOS Home (time, date, pinned apps, dock), swipe-up app drawer via `LauncherApps` | Done, awaiting emulator test |
| 3 — Settings | Categories (delegating to Android), Display appearance, About MesOS | Done, awaiting emulator test |
| 4 — Updater prototype | Manifest, HTTPS, SHA-256 + signature checks, `PackageInstaller`, signed GitHub releases | Done, awaiting signing secrets and emulator test |
| 5 — Emulator verification | [TESTING.md](TESTING.md) checklist incl. reboot and 0.1 → 0.1.1 update | Done (0.1 → 0.1.1 updated from MesOS Settings) |
| 6 — Release | `mesos-v0.1`, `mesos-v0.1.1` | Done |

### 0.1 verification checklist

- [x] Development environment inspected
- [x] Disk usage checked
- [x] Project created
- [x] Project builds successfully (CI)
- [x] Emulator starts
- [x] MesOS home experience appears
- [x] Installed applications can be launched
- [x] App drawer works
- [x] Settings opens
- [x] About MesOS shows MesOS branding/version
- [ ] Configuration survives reboot where applicable
- [x] MesOS update page exists
- [x] Update check works
- [x] Test update path demonstrated
- [x] Updated version is shown correctly
- [x] Application/system remains functional after update
- [x] README exists
- [x] BUILDING documentation exists
- [x] UPDATE documentation exists
- [x] Git repository is clean and usable

## MesOS 0.2 — MesOS apps (done)

Plan: [plans/MESOS_0.2.md](plans/MESOS_0.2.md). MesOS Camera, Photos, Files,
Downloads, Calculator and Notes; MesOS-only home screen (Android apps hidden unless
enabled); Turkish translation; optional MesOS-only emulator script.

The design system, wallpapers, widgets and animations moved to 0.3.

## MesOS 0.3 "Aurora" — everything MesOS can be on stock Android (current)

Plan: [plans/MESOS_0.3.md](plans/MESOS_0.3.md). One update that combines what was
planned for 0.3 and more: Aurora design system and icons, MesOS Home 2.0 (pages,
widgets, folders, drag and drop, universal search), control and notification
center, setup wizard, Settings 2.0, daily update check, Photos editor, and the new
MesOS apps Phone, Messages, Contacts, Browser, Clock, Calendar, Weather, Music,
Recorder, Scanner, Device Care and Tips.

What still belongs to Android (status bar, lock screen, boot animation, recents,
permission dialogs) needs a MesOS system image; that is the next step.

## Next — preparing the move to Linux

Before 0.4 starts: decide the base (AOSP system image or a Linux distribution with
an Android compatibility layer), keep separating pure logic from Android APIs (as
0.3 did for layouts, alarms, weather, colour and crop maths, URL and QR parsing),
and define how MesOS updates move from an app update to a full system update.

## MesOS 0.4 — System integration

Deeper SystemUI changes, permissions experience, power menu, system dialogs, MesOS
framework resources.

## MesOS 0.5 — OTA infrastructure

Real ROM OTA pipeline, signed builds, update server/manifest, integrity
verification, failed-update handling. Requires the AOSP build environment.

## MesOS 0.6+

Performance, battery features, privacy controls, desktop/tablet improvements,
advanced customization, MesOS applications.

## MesOS 1.0

First stable release.
