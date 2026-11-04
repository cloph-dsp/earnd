---
name: earnd
description: Manage Earnd habits and tasks on the phone via adb
version: 1.1.0
author: Earnd contributors
platforms: [linux]
metadata:
  hermes:
    tags: [android, habits, tasks, productivity, adb]
    category: productivity
    requires_toolsets: [terminal]
    config:
      - key: earnd.adb_target
        description: host:port of the phone's adb over the private network
        default: ""
        prompt: Phone adb address (e.g. 100.x.y.z:5555), empty for USB
---

# Earnd (habits and tasks on the phone)

Earnd is an Android app where finishing habits and tasks earns screen time. This skill reads
and manages the user's habits and tasks through `earnd-cli`, which talks to the app over adb.

## When to Use
- The user asks what is on their list today, what is overdue, or how their habits are going.
- The user asks to add, reschedule, rename, complete or delete a task.
- The user asks to create or adjust a habit, or to log / undo a habit check-in.
- The user asks how many minutes they have saved or whether the phone is unlocked (read only).
- The user asks to set reminders/alarms, block an app or website, or make settings stricter.

## What You Cannot Do (by design)
Rule: creating or strengthening is allowed, weakening never is. Do not look for workarounds;
tell the user it has to be done on the phone.
Refused with `forbidden`: unlocking or locking, sessions, the minute balance/ledger, vacation,
remote access itself, unblocking apps/sites or marking apps always-open (no such command).
Refused with `forbidden_weaken`: raising reward minutes of an existing habit or task, turning
photo proof off, and any settings change that loosens blocking (raising/removing the unlock cap,
hard mode/blocking/bedtime off, segment mode, etc.). Lowering rewards is fine.
Photo habits (`"mode":"PHOTO"`) cannot be checked in remotely (`photo_required`).

## Procedure
1. Make sure `earnd-cli` is on PATH and `EARND_ADB_TARGET` is set (from config
   `earnd.adb_target`) unless a USB device is attached.
2. Start with `earnd-cli status` and `earnd-cli tasks.list` or `earnd-cli habits.list` to get ids.
   Never guess ids.
3. Run the change. Pass values as `key=value`; quote values containing spaces.
4. Read the JSON reply. On `"ok":true` confirm briefly in calm, plain words. On an error, explain it.
5. Before `tasks.delete` or `habits.archive`, confirm with the user unless they were explicit.

## Commands
| Command | Arguments |
|---|---|
| `status` | - (returns balanceMinutes, resetMode DAILY/WEEKLY/ROLLING, resetsAt (null in ROLLING), nextExpiryAt (epoch ms) and minutesExpiringNext: in ROLLING mode each earned entry expires after N hours, and minutesExpiringNext minutes lapse at nextExpiryAt) |
| `habits.list` | `all=true` to include archived |
| `habits.get` | `id` |
| `habits.create` | `name` (required), `times` (1-20/day), `schedule` = DAILY, WEEKDAYS, EVERY_N_DAYS, TIMES_PER_WEEK, RULE, `days` = mon,wed,fri / weekdays / weekends (implies schedule=WEEKDAYS), `every` (N days), `per_week` (1-7, any days; implies TIMES_PER_WEEK), `weeks` (N, with `days`: every N weeks), `monthdays` (e.g. `1,15,last`), `rule` (RRULE), `start` (date), `segment` = NONE, MORNING, AFTERNOON, EVENING, `target`, `unit`, `icon`, `color` (#RRGGBB), `mode` = TAP, PHOTO |
| (habits.create, more) | `minutes` (reward per check-in, 0-240), `photo` = true/false, `reminders` = HH:MM list e.g. `08:00,14:00,21:00` or `none`, `remind_days` = mon,tue / all, `message` (reminder text) |
| (habits.create, timed) | `focus_min` (main focus length 1-240 or none), `focus_options` = durations with rewards e.g. `10:5,20:10,30:15` (reward optional = linear) or `none`, `stopwatch` = on/off (open-ended count-up focus), `sw_rate` (earned min per focused min, default 0.5), `sw_min` (minimum session minutes, default 5, 0 = none), `sw_cap` (counted minutes cap or none) |
| (habits.create, over target) | `over_target_reward` = on/off (default on: check-ins beyond `times` earn up to +50%; off: they are recorded but earn 0). Off is allowed remotely, on is phone-only |
| `habits.update` | `id` plus any create field (`minutes` only lower, `photo` only on; timed fields only tighter: rate per minute never higher (max 1.0 if the habit was not timed), `sw_min` only up, `sw_cap` only down, never turn timing/stopwatch off) |
| alarm keys (habits and tasks create/update) | `alarm` = SILENT, NOTIFICATION, FULL_SCREEN, `at_due`, `offsets` (minutes before, tasks: `60,15`), `nag` = OFF, PROGRESSIVE, FIXED, `nag_every`, `nag_max` (or none), `vibrate`, `snooze_max`, `ring_sec` (1-600 seconds, or `none` = app default; allowed both directions), `volume` (10-100 % of the alarm stream, or `none` = default; allowed both directions), `sound` = `default` (clears), `system:<title>` or `custom:<file>` (keys from `sounds.list`) |
| `sounds.list` | - (keys `default`, `system:<title>`, `custom:<filename>` with title, uri, type alarm/notification/ringtone/custom) |
| `sound.push` (local, not a phone command) | `<local file> [name]`: `adb push` to the app's media dir (.mp3 .ogg .wav .m4a), prints the key `custom:<name>` |
| `habits.archive` / `habits.unarchive` | `id` |
| `habits.reorder` | `ids` = comma-separated habit ids to put first, in that order (others keep their order) |
| `habits.checkin` | `id`, optional `qty` (0-1000000, added to today's total) |
| `habits.undo` | `id` (undoes today's latest check-in) |
| `tasks.list` | optional `bucket` = OVERDUE, TODAY, UPCOMING, NO_DATE |
| `tasks.get` | `id` (includes subtasks) |
| `tasks.create` | `title` (required), `notes`, `due` (YYYY-MM-DD, today, tomorrow, none), `time` (HH:MM), `priority` = NONE, LOW, MEDIUM, HIGH, `repeat` = NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, EVERY_N_DAYS or an RRULE (`FREQ=MONTHLY;BYDAY=-1FR`), `days` (weekly), `interval` (N days/weeks/months/years), `monthday` (`15`, `1,15,last`), `nth` (`2TU`, `-1FR`, `last fri`), `count` (N times or none), `until` (date or none), `repeat_from` = due, completion, `parent` (id, makes a subtask) |
| (tasks.create, more) | `reward` (minutes, 0-240) plus alarm keys |
| `tasks.update` | `id` plus any create field (`reward` only lower) |
| `rules.list` | blocked apps, blocked sites, always-open apps |
| `rules.blockable_apps` | launchable apps as label + pkg (find the package to block) |
| `rules.block_app` | `pkg` (e.g. com.instagram.android), optional `label` |
| `rules.block_site` | `domain` (e.g. reddit.com; URLs are reduced to the host) |
| `rules.set_charging` | `pkg` (a blocked app), `rate` = `full`, 0, 25, 50 or 100. Metered apps (rate 0-100 %) open without a session while the balance is above 0 and cost that % of their foreground time; background playback is free. Remotely only stricter: a higher rate, or metered -> full. Lowering a rate or full -> metered is phone-only (`forbidden_weaken`). `rules.list` shows each app's `charging` |
| `settings.get` | all settings plus the list of tightenable keys |
| `settings.tighten` | `key=value` pairs: unlockCapMin, hardMode, blockingEnabled, allDoneUnlock, resetPeriod (DAILY only; WEEKLY and ROLLING are weakening), weakenCooldownMin, bedtimeEnabled, bedtimeStartMin/EndMin (HH:MM ok), bedtimeGraceMin, rollingExpiryHours (only lower, e.g. 15), defaultTaskRewardMin, defaultHabitRewardMin, rewardMode, photoRequiredDefault, removalPuzzle |
| `tasks.complete` / `tasks.uncomplete` / `tasks.skip` / `tasks.delete` | `id` (`skip` moves a recurring task to its next occurrence without reward) |
| `audit.list` | optional `limit` (1-500); recent remote changes |

Dates are in the phone's local time zone. Repeating tasks need a due date; the first due date anchors the series (every-N-weeks parity, Nth weekday, yearly date). Monthly day 31 and yearly Feb 29 clamp to the month's last day. `repeat_from=completion` counts the next date from when it is done ("3 days after completion"). Task JSON reports `repeat` (canonical RRULE), `repeatFrom`, `repeatSummary` (e.g. "Monthly on the last Friday, 6 times") and `occurrence`; habits report `rule`, `perWeek` and `scheduleSummary`.
Limits: at most 24 arguments; `title` 200 chars, `name` 80, `unit` 24, `notes` 4000, other
values 200. Over-limit input returns `bad_args`.

## Examples
```bash
earnd-cli tasks.list bucket=TODAY
earnd-cli tasks.create title="Renew passport" due=2026-11-15 priority=high
earnd-cli tasks.create title="Water plants" due=today repeat=WEEKLY days=mon,thu
earnd-cli tasks.create title="Bins" due=2026-11-01 repeat=WEEKLY interval=2 days=sun   # every other Sunday
earnd-cli tasks.create title="Birthday" due=2028-02-29 repeat=YEARLY   # Feb 28 in non-leap years
earnd-cli tasks.create title="Team report" due=2026-11-27 repeat="FREQ=MONTHLY;BYDAY=-1FR"   # last Friday
earnd-cli tasks.create title="Pay rent" due=2026-11-01 repeat=MONTHLY monthday=1 count=12
earnd-cli tasks.create title="Water cactus" due=today repeat=DAILY interval=10 repeat_from=completion
earnd-cli habits.create name="Gym" per_week=3
earnd-cli habits.create name="Long run" weeks=2 days=sun
earnd-cli tasks.update id=42 due=tomorrow time=none
earnd-cli habits.create name="Read 20 pages" schedule=WEEKDAYS days=weekdays segment=EVENING
earnd-cli habits.checkin id=3
earnd-cli habits.checkin id=5 qty=250
earnd-cli habits.reorder ids=5,2
earnd-cli habits.create name="Drink water" times=3 minutes=10 reminders="08:00,14:00,21:00" remind_days="mon,tue,wed,thu,fri" message="Glass of water"
earnd-cli habits.update id=3 minutes=5 nag=FIXED nag_every=15 alarm=FULL_SCREEN
earnd-cli tasks.create title="Tax form" due=2026-11-20 time=10:00 reward=30 offsets=1440,60
earnd-cli sounds.list
earnd-cli sound.push ~/sounds/gong.mp3
earnd-cli habits.update id=3 sound=custom:gong.mp3
earnd-cli tasks.create title="Call" due=today time=18:00 sound="system:Argon"
earnd-cli tasks.update id=42 sound=default
earnd-cli rules.blockable_apps
earnd-cli rules.block_app pkg=com.instagram.android
earnd-cli rules.block_site domain=reddit.com
earnd-cli rules.set_charging pkg=com.aspiro.tidal rate=100   # stricter only; rate=full = back to sessions
earnd-cli habits.update id=3 over_target_reward=off
earnd-cli settings.tighten rollingExpiryHours=15
earnd-cli settings.tighten unlockCapMin=30
earnd-cli habits.update id=6 focus_min=10 focus_options=10:5,20:10,30:15
earnd-cli habits.update id=5 stopwatch=on sw_rate=0.5 sw_min=10
earnd-cli settings.tighten comebackBonus=false   # turning it back on is phone-only
earnd-cli settings.tighten hardMode=true bedtimeEnabled=true bedtimeStartMin=23:00 bedtimeEndMin=07:00
earnd-cli habits.list all=true   # find archived ids, then: earnd-cli habits.unarchive id=7
```

## Debug build only: set charging on the phone (agent setup)
Bypasses the weakening gates (needs adb shell DUMP). Adds the app to the leisure list if missing:
`adb shell am broadcast -n com.earnd.app/.blocking.DebugChargingReceiver -a com.earnd.app.blocking.DEBUG_SET_CHARGING --es pkg com.aspiro.tidal --es rate 50`
(rate = full | 0 | 25 | 50 | 100; packages: AntennaPod `de.danoeh.antennapod`, YouTube `app.morphe.android.youtube`, TIDAL `com.aspiro.tidal`).

## Pitfalls
- `{"error":"disabled"}`: remote access is off. Ask the user to enable it in Earnd settings.
- `bad_command` / `bad_args`: fix the command name or values; do not retry unchanged.
- `forbidden_weaken`: the change would loosen blocking or raise rewards; only the phone can do it.
- `forbidden_caller`: the request did not come from the adb shell; only `earnd-cli` over adb works.
- `no_reply` or `adb_failed`: the phone is unreachable or wireless debugging turned off after a
  reboot. Ask the user to re-enable it; do not retry in a loop.
- Exit code 1 means the app answered with `"ok":false`; exit code 2 is a transport problem.

## Verification
After a write, re-read with `tasks.get id=...` or `habits.get id=...` and check the field changed.
