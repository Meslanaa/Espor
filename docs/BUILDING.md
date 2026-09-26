# Building MesOS

This document is the reproducible build procedure. If a step here is wrong or
missing, fix this file in the same change.

## Toolchain

| Component | Version | Pinned in |
| --- | --- | --- |
| Gradle | 9.3.1 (SHA-256 verified) | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.1.1 | `gradle/libs.versions.toml` |
| Kotlin (AGP built-in Kotlin + Compose compiler) | 2.3.21 | `gradle/libs.versions.toml` |
| Compose BOM | 2026.03.01 | `gradle/libs.versions.toml` |
| JDK | 17 or newer (Android Studio's bundled JBR 21 works) | — |
| compileSdk / targetSdk | 36 (Android 16) | module `build.gradle.kts` |
| minSdk | 26 (Android 8.0) | module `build.gradle.kts` |

AGP 9.1 needs **Android Studio Panda 2 (2025.3.2) or newer**. Older Studio versions
refuse to sync the project; update Studio rather than downgrading AGP.

## Windows + Android Studio (primary development setup)

1. Clone the repository (or `git pull` an existing clone).
2. In Android Studio: **File → Open…** and select the repository root folder.
3. Let Gradle sync finish. If Studio asks for SDK Platform 36 or Build-Tools 36.0.0,
   install them through the prompt (≈ 200 MB together). Do not install extra
   platforms or system images for MesOS 0.1.
4. Start the existing Android Virtual Device from **Device Manager**.
5. Select the `shell` run configuration and press **Run**.

## Command line

From the repository root:

```sh
# Windows: use gradlew.bat instead of ./gradlew
./gradlew :shell:assembleDebug        # build the MesOS Shell debug APK
./gradlew testDebugUnitTest            # run JVM unit tests for all modules
```

The debug APK is written to `shell/build/outputs/apk/debug/shell-debug.apk`.

Install and start it on a running emulator:

```sh
adb install -r shell/build/outputs/apk/debug/shell-debug.apk
adb shell am start -n org.mesos.shell/.BootstrapActivity
```

`adb` lives in `<Android SDK>/platform-tools` (on Windows usually
`%LOCALAPPDATA%\Android\Sdk\platform-tools`).

## Logs

MesOS components log with consistent tags. Show only MesOS output:

```sh
adb logcat -s MesOS MesOSLauncher MesOSSettings MesOSUpdater
```

A successful Phase 1 start logs one line such as:

```
I MesOS   : Starting MesOS 0.1 Developer Preview (build local)
```

## Versioning

All release identity lives in `mesos.properties` (name, code, label, channel).
Change it there only; `core` exposes it at runtime through `MesOSRelease.current`
and `shell` uses it as the APK `versionCode`/`versionName`.

The build number comes from the `MESOS_BUILD_NUMBER` environment variable and is
`local` when unset. CI sets it to `ci-<run number>`.

## Continuous integration

`.github/workflows/build.yml` builds `:shell:assembleDebug`, runs all unit tests and
uploads the debug APK as the `mesos-shell-debug` artifact on every push to `main`
or `claude/**` branches and on pull requests.

Note: CI debug APKs are signed with a throwaway debug key generated on the runner.
They cannot update an APK built on your machine (and vice versa), because Android
requires the same signing key for updates.

## Disk usage (approximate, MesOS 0.1)

| Item | Size |
| --- | --- |
| SDK Platform 36 + Build-Tools 36.0.0 (if missing) | ≈ 200 MB |
| Gradle 9.3.1 distribution | ≈ 150 MB |
| Gradle dependency caches (AGP, Kotlin, AndroidX) | ≈ 1–2 GB |
| Project build output | ≈ 100–300 MB |

MesOS 0.1 needs no additional emulator system image if a working AVD already
exists. A full AOSP build is a different order of magnitude; see
[ARCHITECTURE.md](ARCHITECTURE.md#aosp-migration-path).

## Build environments

- **Windows desktop with Android Studio** — full workflow: build, emulator, update test.
- **Claude Code cloud session** — can edit code and push; it cannot run the Android
  Emulator (no KVM) and, under the default network policy, cannot reach
  `dl.google.com` (Google Maven and the SDK repository), so it cannot run Gradle
  Android builds locally. CI covers build verification for pushes from there.
