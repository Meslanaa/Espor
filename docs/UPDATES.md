# MesOS Updates

Status: **prototype component updater implemented** (MesOS 0.1). Full OS OTA is
planned for MesOS 0.5.

## Two different kinds of update

| | Prototype component update (0.x) | Full MesOS OTA (from 0.5) |
| --- | --- | --- |
| What changes | The MesOS Shell APK (`org.mesos.shell`): Home, Settings, Updater | The whole OS: system, product, vendor partitions, kernel, framework |
| Mechanism | Android `PackageInstaller` session, user-confirmed | `update_engine` applying a signed `payload.bin` (A/B / Virtual A/B) |
| Needs a ROM | No | Yes |
| Can change Android itself | **No** | Yes |

Replacing the Shell APK is **not** an operating-system update. The Android version,
security patch level and system image stay exactly as they were. MesOS says so on
the MesOS Update and About MesOS screens.

## How a release flows

```
mesos.properties + release/notes/<version>.md
        │  git tag mesos-v<version> && git push origin mesos-v<version>
        ▼
.github/workflows/release.yml
        │  build :shell:assembleRelease signed with the MesOS key (from secrets)
        │  apksigner verify
        │  release/make_release.py → mesos-shell-<version>.apk + mesos-update.json
        ▼
GitHub release "MesOS <version> …" (marked latest)
        ▼
Device: MesOS Settings → System → MesOS Update
        GET https://github.com/Meslanaa/Espor/releases/latest/download/mesos-update.json
```

The manifest URL is set once in `mesos.properties` (`mesos.update.manifestUrl`) and
compiled into the updater.

## Update manifest (schema v1)

```json
{
  "schemaVersion": 1,
  "packageName": "org.mesos.shell",
  "channel": "developer",
  "versionName": "0.1.1",
  "versionCode": 2,
  "minimumSupportedVersion": 1,
  "releaseNotes": "Update system verified.",
  "packageUrl": "https://github.com/Meslanaa/Espor/releases/download/mesos-v0.1.1/mesos-shell-0.1.1.apk",
  "sha256": "<64 lowercase hex chars>",
  "packageSize": 12345678
}
```

| Field | Rule enforced on the device (`UpdatePolicy`) |
| --- | --- |
| `schemaVersion` | Must be 1; otherwise "needs a newer MesOS updater". |
| `packageName` | Must equal the installed package (development builds `org.mesos.shell.dev` never update from releases). |
| `channel` | Must equal the build's channel (`developer`). |
| `versionCode` | Offered only if greater than the installed version code. |
| `minimumSupportedVersion` | Installed version code must be ≥ this. |
| `packageUrl` | Must be `https://`; redirects are followed only within HTTPS. |
| `sha256` | 64 hex chars; the download must hash to exactly this. |
| `packageSize` | 1 byte … 512 MB; the download is aborted past this size and must match it exactly. |

## Verification pipeline (implemented)

1. Fetch the manifest off the main thread over HTTPS (max 64 KB).
2. Apply every rule above.
3. Stream the APK into app-private storage, hashing while downloading.
4. Compare SHA-256. On mismatch the file is deleted and nothing is installed.
5. Inspect the APK: package name and version code must match the manifest, and its
   signing certificate must match the installed MesOS. (If Android does not report
   archive signers, this pre-check is skipped and Android's own install-time
   signature check still rejects a foreign key.)
6. Hand the APK to a `PackageInstaller` session. Android asks the user to allow
   installs from MesOS (first time) and to confirm the update. MesOS never installs
   silently and never disables package verification.
7. Android replaces the APK and restarts MesOS. On first start the new version shows
   "MesOS was updated from X to Y" plus the release notes.

Failures leave the installed version untouched: Android installs APKs atomically.

Not in 0.1: signed manifests (detached signature with a MesOS update key), channel
switching, resumable downloads, background checks.

## Signing key setup (once)

All releases must be signed with the same key, or Android refuses the update.
The key is created on the developer's PC and stored only in GitHub Actions secrets.

1. On Windows, run `scripts/new-signing-key.ps1`:
   `powershell -ExecutionPolicy Bypass -File .\new-signing-key.ps1`
   It creates `%USERPROFILE%\.mesos\mesos-developer.jks` + a password file and copies
   the keystore (Base64) to the clipboard. Running it again never creates a second key;
   it only copies the existing one to the clipboard again.
2. GitHub → repository → Settings → Secrets and variables → Actions → New repository
   secret:
   - `MESOS_KEYSTORE_BASE64` = clipboard content
   - `MESOS_KEYSTORE_PASSWORD` = the printed password
3. Back up the keystore and password. Losing them means installed copies can only be
   updated by uninstalling and reinstalling MesOS.

## Publishing a release

1. Edit `mesos.properties`: raise `mesos.version.code` by one and set
   `mesos.version.name`.
2. Add `release/notes/<version name>.md`.
3. Commit, then tag and push: `git tag mesos-v<version name>` and
   `git push origin mesos-v<version name>`.
4. The release workflow refuses to publish if the tag does not match
   `mesos.properties`, notes are missing, secrets are missing, or the release exists.

## Full OTA (MesOS 0.5 and later)

Planned on top of the AOSP build:

- `ota_from_target_files` for full and incremental signed OTA packages.
- A/B (seamless) updates with `update_engine`; Virtual A/B where used.
- `payload.bin` + `payload_properties.txt` served from the MesOS update server; the
  MesOS Update screen drives `update_engine` through the `UpdateEngine` API.
- Verified boot (AVB) with rollback-index protection against downgrades.
- Automatic fallback to the previous slot when the new slot fails to boot.

## Signing and keys

| Key | Used for | 0.x prototype |
| --- | --- | --- |
| MesOS developer key | MesOS Shell release APKs | In use (GitHub Actions secrets) |
| Android debug key | Local `MesOS Dev` builds | Per machine, never released |
| Platform / shared / media / networkstack keys | ROM system apps and framework | Not used yet |
| Release key | Remaining ROM APKs | Not used yet |
| OTA signing key | OTA packages; public part baked into the ROM | Not used yet |
| AVB key | Verified boot | Not used yet |

Rules:

- Private keys and passwords are never committed (`.gitignore` blocks common key
  formats) and never printed by CI.
- Production ROM keys are generated offline and kept in a password manager /
  hardware token or HSM with an offline backup. CI gets signing material only
  through encrypted secrets, and only in the release job.
- AOSP test keys are public and must never sign a build given to users.
- Key rotation: APK Signature Scheme v3 lineage for app keys; OTA key rotation ships
  the new public key in an update signed by the old key before switching.
