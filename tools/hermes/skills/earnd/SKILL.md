---
name: earnd
description: Manage Earnd habits and tasks on the phone via adb
version: 1.0.0
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

## What You Cannot Do (by design)
The app refuses these remotely with `{"ok":false,"error":"forbidden"}`. Do not look for
workarounds; tell the user it has to be done on the phone:
- unlocking or locking, starting sessions, changing the minute balance
- changing reward minutes of tasks or habits
- changing blocked apps or turning blocking off
- bedtime, vacation, hard mode or any other setting, including remote access itself
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
| `status` | - |
| `habits.list` | `all=true` to include archived |
| `habits.get` | `id` |
| `habits.create` | `name` (required), `times` (1-20/day), `schedule` = DAILY, WEEKDAYS, EVERY_N_DAYS, `days` = mon,wed,fri / weekdays / weekends, `every` (N), `start` (date), `segment` = NONE, MORNING, AFTERNOON, EVENING, `target`, `unit`, `icon`, `color` (#RRGGBB), `mode` = TAP, PHOTO |
| `habits.update` | `id` plus any create field |
| `habits.archive` | `id` |
| `habits.checkin` | `id`, optional `qty` |
| `habits.undo` | `id` (undoes today's latest check-in) |
| `tasks.list` | optional `bucket` = OVERDUE, TODAY, UPCOMING, NO_DATE |
| `tasks.get` | `id` (includes subtasks) |
| `tasks.create` | `title` (required), `notes`, `due` (YYYY-MM-DD, today, tomorrow, none), `time` (HH:MM), `priority` = NONE, LOW, MEDIUM, HIGH, `repeat` = NONE, DAILY, WEEKLY, MONTHLY, EVERY_N_DAYS, `days` (weekly), `interval`, `parent` (id, makes a subtask) |
| `tasks.update` | `id` plus any create field |
| `tasks.complete` / `tasks.uncomplete` / `tasks.delete` | `id` |
| `audit.list` | optional `limit`; recent remote changes |

Dates are in the phone's local time zone. Repeating tasks need a due date.

## Examples
```bash
earnd-cli tasks.list bucket=TODAY
earnd-cli tasks.create title="Renew passport" due=2026-11-15 priority=high
earnd-cli tasks.create title="Water plants" due=today repeat=WEEKLY days=mon,thu
earnd-cli tasks.update id=42 due=tomorrow time=none
earnd-cli habits.create name="Read 20 pages" schedule=WEEKDAYS days=weekdays segment=EVENING
earnd-cli habits.checkin id=3
```

## Pitfalls
- `{"error":"disabled"}`: remote access is off. Ask the user to enable it in Earnd settings.
- `no_reply` or `adb_failed`: the phone is unreachable or wireless debugging turned off after a
  reboot. Ask the user to re-enable it; do not retry in a loop.
- Exit code 1 means the app answered with `"ok":false`; exit code 2 is a transport problem.

## Verification
After a write, re-read with `tasks.get id=...` or `habits.get id=...` and check the field changed.
