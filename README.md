# MesOS

MesOS is an Android-based operating system under development. The goal is a real,
bootable, AOSP-derived OS with its own launcher, settings experience, system
applications, visual identity and update architecture, while staying compatible
with normal Android applications and Google Play.

MesOS is built incrementally. The first milestone, **MesOS 0.1 — Bootstrap**, proves
the development, build and update pipeline on the Android Emulator before any large
feature work begins.

## Current status

**MesOS 0.3 "Aurora"** — everything MesOS can be while it runs on stock Android.

| Area | State |
| --- | --- |
| Aurora design system: fonts, colours, glass surfaces, icons for every MesOS app, animated wallpaper | Done |
| MesOS Home: pages, dock, widgets (MesOS and Android), folders, drag and drop, drawer, universal search | Done |
| Control and notification center (swipe down on Home) | Done |
| Setup wizard; MesOS Settings with search; daily background update check | Done |
| MesOS apps: Phone, Messages, Contacts, Browser, Camera, Photos (with editor), Files, Downloads, Calculator, Notes, Clock, Calendar, Weather, Music, Recorder, Scanner, Device Care, Tips | Done |
| MesOS Update: signed GitHub releases, SHA-256 + signature checks, Android installer | Done (0.1 → 0.1.1 → 0.2 → 0.3) |
| English and Turkish | Done |
| Status bar, lock screen, boot animation, recents, Android Settings | Android's (needs a MesOS system image) |

MesOS 0.x is a **system shell running on stock Android** (installed as an app and
set as the home screen), not yet a ROM. Google Play and all Android apps keep
working. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the path to a real
system image and [docs/plans/MESOS_0.3.md](docs/plans/MESOS_0.3.md) for the 0.3 scope.

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
core/              MesOSRelease, MesOSApps, preferences, Aurora design system, shared UI
launcher/          MesOS Home, widgets, app drawer, search, control center
settings/          MesOS Settings, setup wizard, About MesOS, MesOS Update screen
updater/           Update engine (manifest, verification, installer, daily check)
apps/              One module per MesOS app (browser, calculator, calendar, camera,
                   care, clock, contacts, files, messages, music, notes, phone,
                   photos, recorder, scanner, tips, weather)
shell/             MesOS Shell APK (org.mesos.shell) bundling the modules
release/           Release notes and release packaging script
scripts/           Signing key creation, MesOS-only emulator mode, CI smoke test
docs/              Architecture, building, testing, updates, roadmap
```

## Known limitations

- Runs on top of the stock Android image; the boot animation, status bar, Android's
  quick settings, lock screen, recents and permission dialogs are still Android's.
- Phone, Messages and Browser need to be chosen as the default app (MesOS asks);
  Messages does not download new MMS pictures yet.
- Wi-Fi, Bluetooth, location and battery saver open Android's panel or settings
  (Android does not let apps switch them directly).
- Updates replace MesOS components only, not Android itself.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Building](docs/BUILDING.md)
- [Testing on the emulator](docs/TESTING.md)
- [Updates and signing](docs/UPDATES.md)
- [Roadmap](docs/ROADMAP.md)
- Original project brief (Turkish title, English content):
  `Claude için MesOS Android İşletim Sistemi Geliştirme Promptu.md`
