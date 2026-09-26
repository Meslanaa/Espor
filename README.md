# MesOS

MesOS is an Android-based operating system under development. The goal is a real,
bootable, AOSP-derived OS with its own launcher, settings experience, system
applications, visual identity and update architecture, while staying compatible
with normal Android applications and Google Play.

MesOS is built incrementally. The first milestone, **MesOS 0.1 — Bootstrap**, proves
the development, build and update pipeline on the Android Emulator before any large
feature work begins.

## Current status

**MesOS 0.2** — MesOS apps and a MesOS-only home screen.

| Area | State |
| --- | --- |
| MesOS Home: clock/date, dock, app drawer with search; MesOS mode (only MesOS apps, Google Play, user apps) | Done |
| MesOS apps: Camera, Photos, Files, Downloads, Calculator, Notes | Done |
| MesOS Settings: Display (light/dark), Apps (show Android apps), System, About MesOS | Done |
| MesOS Update: signed GitHub releases, SHA-256 + signature checks, Android installer | Done (verified 0.1 → 0.1.1) |
| English and Turkish | Done |
| Status bar, lock screen, boot animation, Android Settings | Android's (needs the MesOS ROM) |

MesOS 0.x is a **system shell running on stock Android** (installed as an app and
set as the home screen), not yet a ROM. Google Play and all Android apps keep
working. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the path to a real
ROM and [docs/plans/MESOS_0.2.md](docs/plans/MESOS_0.2.md) for the 0.2 scope.

## Install and test

1. Download `mesos-shell-<version>.apk` from the
   [latest release](https://github.com/Meslanaa/Espor/releases/latest).
2. Drag it onto the running Android Emulator window.
3. Open **MesOS Settings** → **Set as home**.

Later versions install from **MesOS Settings → System → MesOS Update**.
Full checklist: [docs/TESTING.md](docs/TESTING.md).

## Build

- Android Studio Otter (2025.2.1) or newer, or JDK 17+ for command-line builds
- Android SDK Platform 36

```sh
./gradlew :shell:assembleDebug testDebugUnitTest   # Windows: gradlew.bat …
```

Local builds install as **MesOS Dev** next to the released MesOS.
Full steps: [docs/BUILDING.md](docs/BUILDING.md).

## Repository layout

```
mesos.properties   MesOS release identity (version, channel, update URL)
core/              MesOSRelease, MesOSApps, preferences, theme, shared UI
launcher/          MesOS Home and app drawer
settings/          MesOS Settings, About MesOS, MesOS Update screen
updater/           Update engine (manifest, verification, installer)
apps/              MesOS Camera, Photos, Files (+ Downloads), Calculator, Notes
shell/             MesOS Shell APK (org.mesos.shell) bundling the modules
release/           Release notes and release packaging script
scripts/           Signing key creation, MesOS-only emulator mode
docs/              Architecture, building, testing, updates, roadmap
```

## Known limitations

- Runs on top of the stock Android image; the boot animation, status bar, quick
  settings, lock screen and permission dialogs are still Android's.
- Phone, Messages and Contacts are still Android's apps (MesOS versions planned for 0.3).
- Updates replace MesOS components only, not Android itself.
- Placeholder logo and wordmark.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Building](docs/BUILDING.md)
- [Testing on the emulator](docs/TESTING.md)
- [Updates and signing](docs/UPDATES.md)
- [Roadmap](docs/ROADMAP.md)
- Original project brief (Turkish title, English content):
  `Claude için MesOS Android İşletim Sistemi Geliştirme Promptu.md`
