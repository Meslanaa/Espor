#!/usr/bin/env bash
# MesOS emulator smoke test (CI only).
#
# Installs the debug build, makes it the home app, opens every MesOS screen,
# saves a screenshot of each and fails when any MesOS process crashed.
# Usage: smoke-test.sh <dir with the debug APK> <output dir>
set -u -o pipefail

APK_DIR="$1"
OUT="$2"
PKG=org.mesos.shell.dev
mkdir -p "$OUT"

APK="$(find "$APK_DIR" -name "*.apk" | head -n 1)"
echo "Installing $APK"

shot() {
  adb exec-out screencap -p > "$OUT/$1.png" 2> /dev/null || echo "screenshot $1 failed"
}

home() {
  adb shell input keyevent KEYCODE_HOME
  sleep 2
}

# Starts an activity of the MesOS package and screenshots it.
# screen <name> <activity class> [extra am arguments...]
screen() {
  local name="$1" cls="$2"
  shift 2
  echo "== $name"
  adb shell am start -W -n "$PKG/$cls" "$@" | grep -E 'Status|Error' || true
  sleep 4
  shot "$name"
  home
}

# Writes MesOS preferences directly (debug builds only), with MesOS stopped.
set_pref_bool() {
  adb shell am force-stop "$PKG"
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/$1.xml'" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="$2" value="$3" />
</map>
XML
}

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb install -r -g "$APK"

adb shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" || true
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow || true
adb shell appops set "$PKG" WRITE_SETTINGS allow || true
adb shell cmd notification allow_listener "$PKG/org.mesos.launcher.control.MesOSNotificationService" || true
adb shell cmd notification allow_dnd "$PKG" || true
adb logcat -b all -c

# Screens are listed in scripts/ci/smoke-screens.txt: <name> <activity> [am args].
# fd 3: adb reads stdin and would otherwise swallow the list.
while read -r name cls args <&3; do
  case "$name" in ''|'#'*) continue ;; esac
  if [ "$name" = "@setup" ]; then
    # Home opens the setup wizard until it is finished; walk through every step.
    echo "== setup"
    home
    sleep 8
    shot setup-welcome
    for step in style permissions home finish; do
      adb shell input tap 540 2220
      sleep 3
      shot "setup-$step"
    done
    adb shell input tap 540 2220
    sleep 3
    continue
  fi
  if [ "$name" = "@setup-done" ]; then
    set_pref_bool mesos_preferences setup_done true
    continue
  fi
  if [ "$name" = "@home" ]; then
    echo "== home"
    home
    sleep 8
    shot home
    # Slow swipes: the CI emulator is busy and may merge a fast swipe into down/up.
    adb shell input swipe 540 700 540 1900 500
    sleep 3
    shot control-center
    adb shell input keyevent KEYCODE_BACK
    sleep 3
    adb shell input swipe 540 1700 540 500 500
    sleep 3
    shot drawer
    adb shell input keyevent KEYCODE_BACK
    sleep 2
    adb shell input tap 540 1985
    sleep 3
    shot search
    adb shell input text "12x4"
    sleep 3
    shot search-results
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    adb shell input keyevent KEYCODE_BACK
    sleep 2
    # Away from the screen edges, which belong to Android's back gesture.
    adb shell input swipe 880 1500 200 1500 400
    sleep 3
    shot home-page-2
    # The same swipe as separate touch events (each MOVE is its own command), in
    # case the emulator delivered the swipe above without moves.
    adb shell input keyevent KEYCODE_HOME
    sleep 3
    adb shell input motionevent DOWN 880 1500
    for x in 800 700 600 500 400 300 200; do adb shell input motionevent MOVE "$x" 1500; done
    adb shell input motionevent UP 200 1500
    sleep 3
    shot home-page-2-touch
    adb shell input swipe 540 1200 540 1210 1500
    sleep 2
    shot home-long-press
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    home
    continue
  fi
  # shellcheck disable=SC2086
  screen "$name" "$cls" $args
done 3< "$(dirname "$0")/smoke-screens.txt"

adb logcat -d > "$OUT/logcat.txt"
adb logcat -b crash -d > "$OUT/crash.txt"

if grep -q -E "org\.mesos" "$OUT/crash.txt"; then
  echo "::error::A MesOS process crashed during the smoke test"
  grep -A 30 -E "FATAL EXCEPTION|org\.mesos" "$OUT/crash.txt" | head -n 200
  exit 1
fi
if grep -q -E "ANR in $PKG" "$OUT/logcat.txt"; then
  echo "::error::MesOS stopped responding (ANR) during the smoke test"
  grep -A 20 "ANR in $PKG" "$OUT/logcat.txt" | head -n 100
  exit 1
fi
echo "Smoke test passed"
