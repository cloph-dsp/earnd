#!/usr/bin/env bash
# Offline checks for earnd-cli quoting and validation (no phone needed).
# SPDX-License-Identifier: GPL-3.0-or-later
set -u
cd "$(dirname "$0")"
fail=0
cli() { EARND_DRY_RUN=1 ./earnd-cli "$@"; }
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$3] got [$2]"; fail=1; fi; }

# Re-parse the generated command as the phone's sh would and print one arg per line.
reparse() { eval "set -- $1"; for a in "$@"; do printf '%s\n' "$a"; done; }

out="$(cli tasks.update id='1; reboot' due='$(rm -rf /)' time="it's")"
args="$(reparse "$out")"
check "semicolon stays inside value" "$(printf '%s\n' "$args" | grep -c '^1; reboot$')" 1
check "command substitution not run" "$(printf '%s\n' "$args" | grep -c '^\$(rm -rf /)$')" 1
check "single quote round-trips" "$(printf '%s\n' "$args" | grep -c "^it's\$")" 1

out="$(cli tasks.create title='Buy "milk" & eggs')"
check "free text is base64" "$(printf '%s' "$out" | grep -c 'title_b64')" 1

cli 'tasks.list;id' >/dev/null; check "bad command name refused" "$?" 2
cli 'x.y.z' >/dev/null; check "nested command refused" "$?" 2
cli tasks.list 'bad-key=1' >/dev/null; check "bad key refused" "$?" 2
cli tasks.list novalue >/dev/null; check "missing = refused" "$?" 2

out="$(cli habits.create name=x reminders="08:00,14:00" remind_days="mon,tue")"
args="$(reparse "$out")"
check "list value stays one arg" "$(printf '%s\n' "$args" | grep -c '^08:00,14:00$')" 1

out="$(cli sound.push '/tmp/My Song!.MP3' 'x;y z.mp3')"
check "sound.push sanitizes name" "$(printf '%s' "$out" | tail -1)" "push /tmp/My Song!.MP3 /sdcard/Android/media/com.earnd.app/sounds/x_y_z.mp3"
cli sound.push /tmp/a.flac >/dev/null; check "sound.push bad extension refused" "$?" 2
cli sound.push >/dev/null; check "sound.push needs file" "$?" 2

exit $fail
