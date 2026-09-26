# Building MesOS

This document is the reproducible build procedure. If a step here is wrong or
missing, fix this file in the same change.

## Toolchain

| Component | Version | Pinned in |
| --- | --- | --- |
| Gradle | 8.14.3 (SHA-256 verified) | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.13.2 | `gradle/libs.versions.toml` |
| Kotlin + Compose compiler plugin | 2.2.21 | `gradle/libs.versions.toml` |
| Compose BOM | 2025.12.01 | `gradle/libs.versions.toml` |
| JDK | 17 or newer (Android Studio's bundled JBR 21 works) | — |
| compileSdk / targetSdk | 36 (Android 16) | module `build.gradle.kts` |
| minSdk | 26 (Android 8.0) | module `build.gradle.kts` |

AGP 8.13 is the newest AGP that **Android Studio Otter (2025.2.1)** can open, and
it also opens in every newer Studio. Moving to AGP 9 requires Studio Panda or newer.

## Windows + Android Studio (primary development setup)

1. Clone the repository (or `git pull` an existing clone).
2. In Android Studio: **File → Open…** and select the repository root folder.
3. Let Gradle sync finish. If Studio asks for SDK Platform 36 or Build-Tools 35.0.0,
   install them through the prompt (≈ 200 MB together). Do not install extra
   platforms or system images for MesOS 0.1.
4. Start the existing Android Virtual Device from **Device Manager**.
5. Select the `shell` run configuration and press **Run** (it opens MesOS Settings).

A local debug build installs as **MesOS Dev** (`org.mesos.shell.dev`), next to the
signed **MesOS** (`org.mesos.shell`) installed from GitHub releases. The two never
overwrite each other, and MesOS Dev never receives updates from the release channel.
To test the published build and its updater, follow [TESTING.md](TESTING.md).

## Command line

From the repository root:

```sh
# Windows: use gradlew.bat instead of ./gradlew
./gradlew :shell:assembleDebug        # build the MesOS Dev debug APK
./gradlew testDebugUnitTest            # run JVM unit tests for all modules
./gradlew :shell:assembleRelease       # unsigned release APK (signing happens in CI)
```

The debug APK is written to `shell/build/outputs/apk/debug/shell-debug.apk`.

Install and start it on a running emulator:

```sh
adb install -r shell/build/outputs/apk/debug/shell-debug.apk
adb shell am start -n org.mesos.shell.dev/org.mesos.settings.SettingsActivity
```

From MesOS Settings, tap **Set as home** to make MesOS Home the home screen.

`adb` lives in `<Android SDK>/platform-tools` (on Windows usually
`%LOCALAPPDATA%\Android\Sdk\platform-tools`).

## Logs

MesOS components log with consistent tags. Show only MesOS output:

```sh
adb logcat -s MesOS MesOSLauncher MesOSSettings MesOSUpdater
```

A successful start logs lines such as:

```
I MesOSLauncher: MesOS Home started (MesOS 0.1 Developer Preview)
I MesOSLauncher: Loaded 42 apps
```

## Versioning

All release identity lives in `mesos.properties` (name, code, label, channel).
Change it there only; `core` exposes it at runtime through `MesOSRelease.current`
and `shell` uses it as the APK `versionCode`/`versionName`.

The build number comes from the `MESOS_BUILD_NUMBER` environment variable and is
`local` when unset. CI sets it to `ci-<run number>`.

## Continuous integration

- `.github/workflows/build.yml` — on pull requests and pushes to `main`: builds the
  debug APK and an unsigned release APK, runs all unit tests, uploads the debug APK
  as the `mesos-shell-debug` artifact.
- `.github/workflows/release.yml` — on `mesos-v*` tags: builds the release APK signed
  with the MesOS developer key and publishes a GitHub release with the update
  manifest. See [UPDATES.md](UPDATES.md#publishing-a-release).

## Disk usage (approximate, MesOS 0.1)

| Item | Size |
| --- | --- |
| SDK Platform 36 + Build-Tools 35.0.0 (if missing) | ≈ 200 MB |
| Gradle 8.14.3 distribution | ≈ 150 MB |
| Gradle dependency caches (AGP, Kotlin, AndroidX) | ≈ 1–2 GB |
| Project build output | ≈ 100–300 MB |

MesOS 0.1 needs no additional emulator system image if a working AVD already
exists. A full AOSP build is a different order of magnitude; see
[ARCHITECTURE.md](ARCHITECTURE.md#aosp-migration-path).

## Build environments

- **Windows desktop with Android Studio** — full workflow: build, emulator, update test.
- **Claude Code cloud session** — edits code, pushes and publishes releases; it cannot
  run the Android Emulator (no KVM) and, under the default network policy, cannot
  reach `dl.google.com` (Google Maven, SDK), so Android builds run in GitHub Actions.
