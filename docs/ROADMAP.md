# MesOS Roadmap

The roadmap is flexible: a feature moves to a later release if the architecture under
it is not stable yet. Rule: **first make MesOS boot and work, then improve it.**

## MesOS 0.1 — Bootstrap (current)

| Phase | Scope | State |
| --- | --- | --- |
| 0 — Environment audit | Tools, disk, emulator; build route | Done (cloud session + Studio Otter / API 37 AVD reported by the developer) |
| 1 — Bootstrap | Gradle project, version single source of truth, `:core`, `:shell`, docs, CI | Done |
| 2 — Launcher | MesOS Home (time, date, pinned apps, dock), swipe-up app drawer via `LauncherApps` | Done, awaiting emulator test |
| 3 — Settings | Categories (delegating to Android), Display appearance, About MesOS | Done, awaiting emulator test |
| 4 — Updater prototype | Manifest, HTTPS, SHA-256 + signature checks, `PackageInstaller`, signed GitHub releases | Done, awaiting signing secrets and emulator test |
| 5 — Emulator verification | [TESTING.md](TESTING.md) checklist incl. reboot and 0.1 → 0.1.1 update | Not started |
| 6 — Release | Final report, `MESOS 0.1 BOOTSTRAP VERIFIED` | Not started |

### 0.1 verification checklist

- [x] Development environment inspected
- [x] Disk usage checked
- [x] Project created
- [x] Project builds successfully (CI)
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
