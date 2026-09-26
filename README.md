# MesOS

MesOS is an Android-based operating system under development. The goal is a real,
bootable, AOSP-derived OS with its own launcher, settings experience, system
applications, visual identity and update architecture, while staying compatible
with normal Android applications.

MesOS is built incrementally. The first milestone, **MesOS 0.1 — Bootstrap**, proves
the development, build and update pipeline on the Android Emulator before any large
feature work begins.

## Current status

**MesOS 0.1 Developer Preview — Phase 1 (Bootstrap).**

| Area | State |
| --- | --- |
| Gradle project, version single source of truth | Done (`mesos.properties`) |
| `:core` — release identity, logging tags, design tokens | Done |
| `:shell` — MesOS userland APK | Bootstrap identity screen only |
| MesOS Home / app drawer | Phase 2 |
| MesOS Settings / About MesOS | Phase 3 |
| Updater prototype | Phase 4 |
| Emulator verification | Phase 5 |

At this stage MesOS is a **prototype userland running on stock Android**, not yet a
ROM. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how it becomes one.

## Requirements

- Android Studio Panda 2 (2025.3.2) or newer, or JDK 17+ for command-line builds
- Android SDK Platform 36 and SDK Build-Tools 36.0.0
- An Android Emulator image (API 26 or newer; API 34–36 recommended)

## Build and run

```sh
./gradlew :shell:assembleDebug          # Windows: gradlew.bat :shell:assembleDebug
adb install -r shell/build/outputs/apk/debug/shell-debug.apk
adb shell am start -n org.mesos.shell/.BootstrapActivity
```

Or open the repository folder in Android Studio and run the `shell` configuration.
Full, reproducible steps: [docs/BUILDING.md](docs/BUILDING.md).

## Repository layout

```
mesos.properties   MesOS release identity (version name/code, label, channel)
core/              Shared foundation: MesOSRelease, MesOSLog, MesOSTheme
shell/             MesOS Shell APK (org.mesos.shell)
docs/              Architecture, building, updates, roadmap
.github/workflows  CI: builds the debug APK and runs unit tests
```

## Known limitations

- Runs as an ordinary app on stock Android; it does not replace system components yet.
- No launcher, settings or updater functionality yet (Phases 2–4).
- The placeholder logo and wordmark are not final artwork.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Building](docs/BUILDING.md)
- [Updates](docs/UPDATES.md)
- [Roadmap](docs/ROADMAP.md)
- Original project brief (Turkish title, English content):
  `Claude için MesOS Android İşletim Sistemi Geliştirme Promptu.md`
