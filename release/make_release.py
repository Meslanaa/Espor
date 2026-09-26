#!/usr/bin/env python3
"""Package a signed MesOS Shell APK as GitHub release assets.

Reads the release identity from mesos.properties (the single source of truth) and
writes, into --out:
  mesos-shell-<version>.apk   the APK, renamed
  mesos-update.json           the update manifest the on-device updater reads
and, into --meta, the release title and body used by `gh release create`.

Fails if the tag does not match mesos.properties or release notes are missing,
so a release can never be published under the wrong version.
"""

import argparse
import hashlib
import json
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACKAGE_NAME = "org.mesos.shell"  # must match applicationId in shell/build.gradle.kts
SCHEMA_VERSION = 1


def read_properties(path: pathlib.Path) -> dict:
    props = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, sep, value = line.partition("=")
        if not sep:
            raise SystemExit(f"{path}: cannot parse line: {raw!r}")
        props[key.strip()] = value.strip()
    return props


def sha256_of(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=pathlib.Path, help="signed release APK")
    parser.add_argument("--tag", required=True, help="git tag being released, e.g. mesos-v0.1")
    parser.add_argument("--repo", required=True, help="GitHub owner/repo, e.g. Meslanaa/Espor")
    parser.add_argument("--out", required=True, type=pathlib.Path, help="directory for release assets")
    parser.add_argument("--meta", required=True, type=pathlib.Path, help="directory for title/body files")
    args = parser.parse_args()

    props = read_properties(ROOT / "mesos.properties")
    version_name = props["mesos.version.name"]
    version_code = int(props["mesos.version.code"])
    label = props.get("mesos.version.label", "")
    channel = props["mesos.channel"]
    minimum_supported = int(props.get("mesos.update.minimumSupportedVersion", "1"))

    expected_tag = f"mesos-v{version_name}"
    if args.tag != expected_tag:
        print(f"error: tag {args.tag!r} does not match mesos.properties (expected {expected_tag!r})", file=sys.stderr)
        return 1

    notes_file = ROOT / "release" / "notes" / f"{version_name}.md"
    if not notes_file.is_file():
        print(f"error: missing release notes {notes_file.relative_to(ROOT)}", file=sys.stderr)
        return 1
    notes = notes_file.read_text(encoding="utf-8").strip()

    if not args.apk.is_file():
        print(f"error: APK not found: {args.apk}", file=sys.stderr)
        return 1

    args.out.mkdir(parents=True, exist_ok=True)
    args.meta.mkdir(parents=True, exist_ok=True)

    apk_name = f"mesos-shell-{version_name}.apk"
    apk_out = args.out / apk_name
    shutil.copyfile(args.apk, apk_out)

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "packageName": PACKAGE_NAME,
        "channel": channel,
        "versionName": version_name,
        "versionCode": version_code,
        "minimumSupportedVersion": minimum_supported,
        "releaseNotes": notes,
        "packageUrl": f"https://github.com/{args.repo}/releases/download/{args.tag}/{apk_name}",
        "sha256": sha256_of(apk_out),
        "packageSize": apk_out.stat().st_size,
    }
    (args.out / "mesos-update.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    display = f"MesOS {version_name} {label}".strip()
    (args.meta / "title.txt").write_text(display + "\n", encoding="utf-8")
    body = (
        f"{notes}\n\n"
        f"---\n"
        f"- Version code: `{version_code}` · channel: `{channel}`\n"
        f"- APK SHA-256: `{manifest['sha256']}`\n"
        f"- First install: download `{apk_name}` and install it on the emulator. "
        f"Later releases arrive through MesOS Settings → System → MesOS Update.\n"
        f"- This is a MesOS component update (launcher, settings, updater), not an Android OS update.\n"
    )
    (args.meta / "body.md").write_text(body, encoding="utf-8")

    print(json.dumps(manifest, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
