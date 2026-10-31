# Earnd — product requirements (clean-room source of truth)

This document is the ONLY behavioral input for the open-source Earnd codebase. It is written from the
author's own needs and decisions. Implementers must not consult decompiled code, specs derived from
other apps, or any third-party proprietary material. Ideas below are described in our own words; all
names, constants, data layouts and code structure are to be designed independently.

## 1. Purpose
Earnd is an open-source (GPL-3.0) Android app for people with ADHD/executive dysfunction. It does not
just remind; it makes doing the boring thing the path of least resistance by putting a tangible,
immediate consequence on leisure apps. Local-first, offline, no account, no cloud, no analytics, no paid
tier.

## 2. Core mechanism: earned screen time
- The user picks "leisure" apps (and optionally websites). By default these are locked.
- Completing habits and tasks earns minutes of leisure time. The user spends earned minutes by starting
  an unlock session; while a session runs, leisure apps are open; when it ends, they lock again.
- Earned balance resets on a period the user picks (daily, or weekly). Balance never carries past the
  reset.
- "All done today" reward: when every habit scheduled for today is complete, leisure apps unlock for
  the rest of the day (configurable on/off).
- Hard mode: stricter rules, more resistance to impulsive changes (e.g. cannot spend minutes manually,
  only all-done unlocks; settings that weaken blocking require a cooldown/confirmation delay).
- Bedtime lock: a configurable nightly window during which leisure apps are locked regardless of
  balance; a short one-off grace (couple of minutes) is allowed.
- Vacation mode: pauses blocking and streak accounting between two dates.
- Days with nothing scheduled do not lock the phone.
- Essential apps are NEVER blockable: phone/dialer, SMS/messaging defaults, emergency, maps/navigation
  defaults, system settings, this app, launcher, system UI, permission controller. Provide a whitelist
  UI the user can extend.
- Blocking is implemented with an AccessibilityService that detects the foreground app (and browser
  address bar for websites) and shows a full-screen "locked" screen listing what can be done to earn
  time, with a one-tap path into check-in. Exit goes to the home screen.
- Tasks earn minutes only if the task has a reward set (default reward configurable); tasks never lock
  the phone on their own.

## 3. Habits
- Unlimited habits. Name, icon, color, times per day, schedule (every day / chosen weekdays / every N
  days), optional time-of-day segment (morning/afternoon/evening) or none (flexible), optional
  quantity target with unit, minutes earned per check-in.
- Check-in: quick tap, or photo-required (camera capture is the commitment device; no AI verification).
- Undo a check-in (reverses the minutes it earned).
- History per habit; streaks kept minimal (no heavy gamification).

## 4. Tasks
- Separate entity from habits: title, notes, optional due date (+ optional time), priority, subtasks,
  flexible recurrence (RRULE subset: every N days/weeks/months/years, chosen weekdays, weekdays-only,
  day of month incl. last day, Nth weekday e.g. "last Friday", yearly; from due date or after completion;
  ends never / until a date / after N times; skip an occurrence), optional reward minutes,
  per-task alarm settings (see §5). Completing a recurring task creates the next occurrence.
- Overdue recurring tasks never get stuck: an unfinished past occurrence of a recurring task rolls
  forward automatically to the next valid occurrence.
- Views: Overdue, Today, Upcoming, No date, Completed.

## 5. Alarms and nagging (ADHD-specific, learned from years of failed systems)
- The notification is the product: it must tell the whole story at a glance.
- One consolidated "pending" notification (replaced, not stacked; removed when nothing is pending).
  Each pending item is its own action ("Done: <item>"); no generic buttons to match against text.
- Ordered by how many times an item has been nagged (most ignored first); header shows short labels
  with counts.
- Default cadence: first nag 10 min after due, then progressively spaced (e.g. 15, 25, 40, 60, 90, 120,
  180, 240 min). Quiet hours (default 00:00–08:00). Items more than a week overdue collapse into at most
  one daily digest.
- Per item (task, habit reminder) the user can customize: alarm times (at due and/or offsets before),
  style (silent / notification / full-screen alarm that keeps ringing), nagging (off / progressive /
  fixed every N min, optional max), sound and vibration.
- Snooze exists ONLY on the full-screen alarm, with a fixed duration and a configurable max count
  (default 2). The consolidated notification has no snooze: ignoring it is the snooze.
- Native Android mechanisms: exact alarms, full-screen intents, high-importance channels, re-arm on
  boot / app update / time or timezone change, prompt for battery-optimization exemption.
- Works fully offline.

## 6. Widgets
- 1x1 lock widget: shows locked/unlocked + remaining time; tap toggles (start session if balance and
  allowed; lock if unlocked; open app to earn if no balance).
- 4x2 tasks widget: pending tasks (overdue/today first) with a checkbox per task; tap opens the task;
  "+" adds.

## 7. Remote management by a local agent (adb over a private network)
- A command interface reachable only from the adb shell (protected by a permission that only the shell/
  system hold), returning JSON. Read and manage habits and tasks, read lock status and balance.
- Forbidden remotely: unlocking, changing balance, changing blocked apps, disabling blocking, toggling
  bedtime/vacation.
- Ship a Linux CLI wrapper and an agent skill (Hermes Agent format) in tools/.

## 8. Privacy
- All data on-device. Photos stored in app-private storage. No network permission needed except none;
  no analytics.

## 9. Non-goals
Notification-only design; rigid schedules; heavy gamification; AI for its own sake; multi-service
architecture; tasks blocking the phone indiscriminately.

## 10. Additional features (parity pass, 2026-10-31)
Described in our own words; design independently.
- Habits: custom icon (pick from a built-in icon set) and color; optional quantity target with unit
  (e.g. 2 L, 20 pages) where check-ins add amounts; manual reordering of habits; archive/unarchive with
  an archived list; per-habit reminders on chosen weekdays with optional custom message.
- Day segments: optional "segment mode" where finishing all habits of the current segment (morning /
  afternoon / evening, with configurable start times) unlocks leisure apps until the next segment.
- Reward behavior setting: minutes per check-in (global default + per-habit override), or "only when
  everything is done".
- Photo-required as a global default for new habits ("I promise to prove it" commitment toggle), plus
  per-habit override.
- Morning nudge: optional daily notification at a chosen time summarizing today's habits, with a small
  rotating set of short original messages.
- Daily report: optional evening notification with what was done/missed and leisure time used.
- Screen-time report screen: using Usage Access (optional permission), show today's most-used apps and
  an hour-by-hour bar chart of phone use, plus earned vs spent minutes.
- Habit detail: calendar/history of check-ins, current and best streak, "perfect days" count (days
  where everything scheduled was done).
- "Blocking is off" safeguard: if the accessibility service gets disabled (or after an app update
  resets it), post a persistent notification that opens the right settings screen, and remind again
  after ~30 minutes if still off.
- Onboarding includes a short "how it works" explainer, and OEM battery guidance (detect common
  aggressive manufacturers — e.g. Xiaomi, Samsung, Nothing, OnePlus, Huawei — and show the specific
  steps to set battery to unrestricted).
- Theme: system / light / dark selector.
- Restricted-settings guidance: on Android 13+, sideloaded apps must "Allow restricted settings" in App
  info before the accessibility service can be enabled; onboarding must explain this with a button to
  App info.
- Unlock window: optional cap on each unlock session (off, 15–45 min in 5-min steps); unused earned
  minutes stay in the balance for the next session.
- Removal friction ("puzzle lock"): optional challenge before removing a blocked app/website or
  weakening blocking — none / solve a short arithmetic problem / type out a displayed sentence exactly.
- Backup: export all data to a JSON file (Storage Access Framework) and import it back; "clear all data"
  with confirmation.
- Habit list density option: compact or comfortable rows.
