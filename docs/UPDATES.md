# MesOS Updates

Status: **design only**. The prototype updater is implemented in Phase 4 of
MesOS 0.1. This document is written first so the implementation has a contract.

## Two different kinds of update

| | Prototype component update (0.x) | Full MesOS OTA (from 0.5) |
| --- | --- | --- |
| What changes | The MesOS Shell APK (`org.mesos.shell`): launcher, settings, updater | The whole OS: system, product, vendor partitions, kernel, framework |
| Mechanism | Android `PackageInstaller` session, user-confirmed | `update_engine` applying a signed `payload.bin` (A/B / Virtual A/B) |
| Needs a ROM | No | Yes |
| Can change Android itself | **No** | Yes |

Replacing the Shell APK is **not** an operating-system update. It updates MesOS
userland components only; the Android version, security patch level and system
image stay exactly as they were. The UI must say "MesOS component update" in 0.x.

## Update manifest (schema v1)

Served over HTTPS as JSON. One manifest per channel.

```json
{
  "schemaVersion": 1,
  "packageName": "org.mesos.shell",
  "channel": "developer",
  "versionName": "0.1.1",
  "versionCode": 2,
  "minimumSupportedVersion": 1,
  "releaseNotes": "Update system verified.",
  "packageUrl": "https://…/mesos-shell-0.1.1.apk",
  "sha256": "<64 hex chars>",
  "packageSize": 12345678
}
```

| Field | Rule |
| --- | --- |
| `schemaVersion` | Must be a version this updater understands; otherwise ignore the manifest. |
| `packageName` | Must equal the installed Shell package. |
| `channel` | Must equal the device's channel (`MesOSRelease.current.channel`). |
| `versionCode` | Offer only if greater than the installed MesOS version code. |
| `minimumSupportedVersion` | Installed version code must be ≥ this, otherwise "update not supported from this version". |
| `packageUrl` | Must be `https://`. |
| `sha256` | SHA-256 of the downloaded file must match before anything is installed. |
| `packageSize` | Download is aborted if it exceeds this size. |

## Verification pipeline (Phase 4)

1. Fetch the manifest off the main thread over HTTPS; reject non-HTTPS URLs.
2. Validate every rule in the table above.
3. Download to app-private storage, enforcing `packageSize`.
4. Verify SHA-256. On mismatch: delete the file, report failure, install nothing.
5. Inspect the APK before installing: package name and version code must match the
   manifest, and its signing certificate must match the installed Shell's
   certificate. (Android enforces signature continuity too; checking first gives a
   clear error instead of a generic install failure.)
6. Install through a `PackageInstaller` session. Android shows its own confirmation
   dialog; MesOS never bypasses it, never disables package verification and never
   installs silently.
7. After the install, the updated Shell restarts; About MesOS shows the new version.

Failures leave the installed version untouched: Android installs APKs atomically,
so an interrupted or rejected install keeps the old version working.

Not in 0.1 and planned for later: signed manifests (detached signature verified with
a MesOS update key), channel switching, resumable downloads, automatic checks.

## 0.1.1 test update

The Phase 4 test proves Settings → System → MesOS Update → Check → Download →
Install end-to-end:

- Installed: MesOS 0.1 (`versionCode` 1).
- Update: MesOS 0.1.1 (`versionCode` 2), built by changing only `mesos.properties`.
- Visible result: About MesOS shows 0.1.1 and the release note
  "Update system verified."

Both APKs must be signed with the **same key** — build both on the same machine (or
with the same developer keystore). A CI-built APK cannot update a locally built one.

## Full OTA (MesOS 0.5 and later)

Planned on top of the AOSP build:

- `ota_from_target_files` to produce full and incremental signed OTA packages.
- A/B (seamless) updates with `update_engine`; Virtual A/B where the device uses it.
- `payload.bin` + `payload_properties.txt` served from the MesOS update server;
  the MesOS updater UI drives `update_engine` through `UpdateEngine` APIs.
- Verified boot (AVB) with rollback-index protection against downgrades.
- Automatic rollback to the previous slot when the new slot fails to boot.

## Signing and keys

| Key | Used for | 0.x prototype |
| --- | --- | --- |
| APK signing key | MesOS Shell APK | Android debug key (development only) |
| Platform / shared / media / networkstack keys | ROM system apps and framework | Not used yet |
| Release key | Remaining ROM APKs | Not used yet |
| OTA signing key | Signing OTA packages; public part baked into the ROM | Not used yet |
| AVB key | Verified boot | Not used yet |

Rules:

- Private keys and passwords are never committed (`.gitignore` blocks common key
  formats) and never printed in logs or CI output.
- Production keys are generated offline and stored in a password manager / hardware
  token or HSM, with an offline backup. CI receives signing material only through
  encrypted secrets, and only for release jobs.
- AOSP test keys (`build/make/target/product/security`) are public and must never
  sign a build distributed to users.
- Key rotation: APK Signature Scheme v3 lineage for app keys; OTA key rotation ships
  the new public key in an update signed by the old key before switching.
