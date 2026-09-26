# MesOS Roadmap

The roadmap is flexible: a feature moves to a later release if the architecture under
it is not stable yet. Rule: **first make MesOS boot and work, then improve it.**

## MesOS 0.1 — Bootstrap (current)

| Phase | Scope | State |
| --- | --- | --- |
| 0 — Environment audit | Inspect tools, disk, emulator; choose the build route | Cloud session audited; Windows desktop audit pending |
| 1 — Bootstrap | Gradle project, version single source of truth, `:core`, `:shell`, docs, CI | In progress |
| 2 — Launcher | MesOS Home (time, date, app grid, dock), swipe-up app drawer via `LauncherApps` | Not started |
| 3 — Settings | MesOS Settings categories (delegating to Android where sensible), About MesOS with live device data | Not started |
| 4 — Updater prototype | Manifest, HTTPS fetch, SHA-256 + signature checks, `PackageInstaller` install, 0.1.1 test update | Not started |
| 5 — Emulator verification | Full checklist below on the Android Studio emulator, including reboot | Not started |
| 6 — Release | Tag `mesos-0.1`, final report | Not started |

### 0.1 verification checklist

- [ ] Development environment inspected (Windows desktop)
- [ ] Disk usage checked
- [x] Project created
- [ ] Project builds successfully (CI + desktop)
- [ ] Emulator starts
- [ ] MesOS home experience appears
- [ ] Installed applications can be launched
- [ ] App drawer works
- [ ] Settings opens
- [ ] About MesOS shows MesOS branding/version
- [ ] Configuration survives reboot where applicable
- [ ] MesOS update page exists
- [ ] Update check works
- [ ] Test update path demonstrated
- [ ] Updated version is shown correctly
- [ ] Application/system remains functional after update
- [x] README exists
- [x] BUILDING documentation exists
- [x] UPDATE documentation exists
- [ ] Git repository is clean and usable

## MesOS 0.2 — Visual foundation

Proper design system, wallpapers, light/dark themes, launcher improvements, widgets
foundation, improved app drawer, quick search, animation foundation.

## MesOS 0.3 — System experience

Notification customization, quick settings exploration, lock screen, status bar
integration, deeper Settings integration.

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
