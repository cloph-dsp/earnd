<div align="center">

<img src="docs/logo.svg" alt="" width="96" height="96">

# Earnd

**Do the boring things first, then earn your screen time.**

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat&logo=android&logoColor=white)](#install)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](#build)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](#build)
[![GitHub stars](https://img.shields.io/github/stars/cloph-dsp/earnd?style=flat&logo=github)](https://github.com/cloph-dsp/earnd/stargazers)

</div>

Earnd is a local-first Android app for people with ADHD or executive dysfunction. Habits and tasks
earn minutes; leisure apps stay locked until you spend them. The app has no internet permission, no
account and no analytics: everything stays in a database on the phone.

> [!NOTE]
> Early software (v0.1.x), built and used daily on one device: a Nothing Phone 3a.

## Features

**Earn**
- Habits on weekly schedules, with streaks and partial credit for a job half done
- Tasks with sizes and deadlines that pay into the same time bank
- Timed and focus habits; photo check-ins; optional habits that help but are not needed to close the day
- A comeback bonus after time away, and a full refund when a check-in was a mistap

**Spend**
- Earned minutes unlock leisure apps for as long as the balance lasts
- An accessibility service blocks and meters the apps you mark as leisure, and shows a lock screen with what would earn time
- Optional 75% rate, so one earned minute buys less than a minute of leisure
- Per-app usage in Stats, a nudge when a single session runs long, and a weekly quota per app

**Remind**
- Exact alarms that ring full screen with Done, Silence, Snooze and Ignore
- A nag ladder for anything still pending, restarting whenever you make partial progress
- One consolidated notification with per-item actions

**See**
- Today, Habits, Tasks and Stats screens, all on one time bank
- Glance widgets that follow the same balance

**Guard**
- Hard mode: no easy way out of the lock screen
- A local agent surface that can tighten limits, never loosen or unlock them

## Install

1. Download the APK from the [latest release](https://github.com/cloph-dsp/earnd/releases/latest).
2. Allow installing from your browser when Android asks.
3. Open Earnd and grant accessibility, usage access, notifications and exact alarms. The Today screen
   shows a banner while any of them is off.

There is no Play Store build. Updates are installed over the old APK and keep the data.

## Privacy

No account, no server, no telemetry, and no `INTERNET` permission in the manifest. Habits, balances and
usage counters never leave the phone. Backups are whatever your Android backup settings do.

## Build

```sh
./gradlew assembleDebug
```

Needs JDK 17 and the Android SDK (API 34). minSdk is 29. Tests: `./gradlew testDebugUnitTest`.

## Docs

- [REQUIREMENTS.md](REQUIREMENTS.md): what the app does and the rules behind it
- [ARCHITECTURE.md](ARCHITECTURE.md): modules, data flow, the time bank
- [DESIGN.md](DESIGN.md): the visual language

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
