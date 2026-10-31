#!/usr/bin/env bash
# Rings a test alarm on a connected device (debug build of Earnd must be installed).
# Usage: tools/dev/test-alarm.sh [ring|stop] [label] [timeout_ms]
#   ring  (default)  start a test alarm; optional label and auto-timeout (default 5 min)
#   stop             silence any ringing alarm
# Manual checks: screen off/locked -> full-screen alarm; screen on -> alarm screen or heads-up
# with Stop/Snooze; volume key silences; Back does nothing; Stop silences at once.
set -euo pipefail
PKG=com.earnd.app
CMD=${1:-ring}
case "$CMD" in
  ring)
    LABEL=${2:-Test alarm}
    TIMEOUT=${3:-0}
    adb shell am broadcast -n "$PKG/.alarms.DebugAlarmReceiver" \
      -a com.earnd.app.alarms.DEBUG_RING \
      --es label "\"$LABEL\"" --el timeout_ms "$TIMEOUT"
    ;;
  stop)
    adb shell am broadcast -n "$PKG/.alarms.DebugAlarmReceiver" -a com.earnd.app.alarms.DEBUG_STOP
    ;;
  *) echo "usage: $0 [ring|stop] [label] [timeout_ms]" >&2; exit 2 ;;
esac
