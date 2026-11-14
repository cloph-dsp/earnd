# Earnd remote tools (adb + Hermes Agent)

These tools let an agent on your own Linux machine read and manage Earnd habits and tasks on your
phone. Everything goes through adb; Earnd has no network permission and no server.

- `earnd-cli`: bash wrapper around `adb shell am broadcast`, prints clean JSON.
- `skills/earnd/SKILL.md`: a Hermes Agent skill that teaches the agent the commands.
- `import-ticktick.py`: one-off importer from TickTick into Earnd.

## What remote access can and cannot do

Policy: **creating or strengthening is allowed; weakening is not.**

Allowed: read status (balance, lock state, counts; read-only), list/get/create/update/archive/
unarchive/reorder habits (including reward minutes, photo proof, reminders and alarm config),
check in (with quantity) and undo, list/get/create/update/complete/uncomplete/delete tasks
(reward on create, alarms), list and add blocked apps/websites (`rules.*`), read settings and
tighten them (`settings.tighten`). Remote check-ins and completions never credit the time bank.

## Alarm sounds

```
earnd-cli sounds.list                                  # keys: default, system:<title>, custom:<file>
earnd-cli sound.push ~/sounds/gong.mp3                 # adb push to /sdcard/Android/media/com.cloph.earnd/sounds/
earnd-cli sound.push ~/a.ogg wake-up.ogg               # optional name (sanitized; .mp3 .ogg .wav .m4a only)
earnd-cli habits.update id=3 sound=custom:gong.mp3
earnd-cli tasks.create title="Call" due=today time=18:00 sound="system:Argon"
earnd-cli tasks.update id=42 sound=default             # back to the default alarm sound
```

If a custom file is later removed, the alarm falls back to the default alarm sound.

Refused with `forbidden_weaken`: raising a habit's or task's reward minutes, turning photo proof
off, and any `settings.tighten` change that the phone's own settings screen classifies as
weakening (e.g. raising or removing the unlock cap, turning hard mode/blocking/bedtime off,
segment mode, vacation, theme and other non-tightening keys).

Always refused with `forbidden`: unlocking/locking, sessions, spending or changing the balance,
the ledger, vacation, remote access itself, and any unblock / always-open command (none exists).
Photo habits must be checked in on the phone. Remote access is **off by default**; turn it on in
Earnd's settings. Every remote change (and every refusal) is kept in an audit log on the
phone (last 200 entries; `earnd-cli audit.list`).

## How it is protected

The command receiver is exported but requires `android.permission.DUMP`. Only the adb shell
user and the system hold it, so other apps on the phone cannot call it. The real security
boundary is therefore **who can reach adb**: anyone with adb access to the phone can do far
more than this tool. Treat adb access like a root password.

Defence in depth: on Android 14+ the receiver also refuses senders whose UID the platform
reports as anything other than shell (2000) or root (0). The app caps argument count and
length and validates every number. `earnd-cli` validates the command name and keys, base64-
encodes free text and single-quotes every other value, because `adb shell` hands one command
line to the phone's `sh`. Run `./test-earnd-cli.sh` to check the quoting offline
(`EARND_DRY_RUN=1 earnd-cli ...` prints the exact device command without running it).

## Setup

1. Install `adb` (Debian/Ubuntu: `sudo apt install adb`; Arch: `android-tools`).
2. Put the CLI on PATH:
   ```bash
   install -m 755 earnd-cli ~/.local/bin/earnd-cli
   ```
3. On the phone: Settings > About phone > tap Build number 7 times; then Developer options.

### Option A: Wireless debugging (Android 11+), recommended

1. Developer options > Wireless debugging > On. Choose "Pair device with pairing code".
2. On Linux: `adb pair <phone-ip>:<pairing-port>` and type the 6-digit code.
3. Connect to the port shown on the Wireless debugging screen (it differs from the pairing port):
   `adb connect <phone-ip>:<port>`.
4. `export EARND_ADB_TARGET=<phone-ip>:<port>`

Caveats: the connect port is random and changes whenever wireless debugging restarts. Android
turns wireless debugging off after a reboot and, on many phones, when Wi-Fi changes. Pairing
survives; you only reconnect.

### Option B: `adb tcpip` (any version, needs USB once per boot)

```bash
adb devices    # plug in by USB and accept the RSA prompt on the phone
adb tcpip 5555
adb connect <phone-ip>:5555
export EARND_ADB_TARGET=<phone-ip>:5555
```

The fixed port is convenient, but tcpip mode is lost on every reboot and must be re-enabled
over USB.

### Over Tailscale

Install Tailscale on the phone and the Linux machine, then use the phone's Tailscale address
(`100.x.y.z`) as `<phone-ip>` above. With Wireless debugging, Android may only advertise it on
Wi-Fi; if the Tailscale address does not answer, pair and connect once on the LAN, then run
`adb tcpip 5555` and connect via Tailscale. Use Tailscale ACLs so only your agent machine can
reach the phone on the adb port.

Check it works:

```bash
earnd-cli status
```

## Installing the Hermes skill

```bash
mkdir -p ~/.hermes/skills/productivity
cp -r skills/earnd ~/.hermes/skills/productivity/earnd
```

Set the skill's `earnd.adb_target` config when Hermes prompts (or export `EARND_ADB_TARGET`
in the agent's environment). Invoke with `/earnd` or just ask about your tasks.

## Security notes

- Never expose adb to the internet or an untrusted LAN; prefer Tailscale with ACLs.
- Revoke access any time: Developer options > Revoke USB debugging authorizations, or turn
  Wireless debugging off. Turning off Earnd's remote access stops this tool even with adb on.
- Review `earnd-cli audit.list` occasionally.
- The importer reads API tokens from a file you point it at; it never stores or prints them.

## TickTick import

```bash
# From an export (preview first; nothing is written without --apply)
./import-ticktick.py --export ticktick.json
./import-ticktick.py --export ticktick.json --apply
# From the Open API, token in a file you control (chmod 600)
./import-ticktick.py --token-file ~/.config/earnd/ticktick-token --apply
# Skip whole TickTick projects by name (repeatable)
./import-ticktick.py --export ticktick.json --skip-project Archive --skip-project "Old stuff" --apply
```

Notes are cleaned of internal reference junk (lines/prefixes like `[id:12]` or `adhd-34`);
notes that end up empty are dropped.

Recurrence: TickTick RRULEs are passed through as-is (FREQ DAILY/WEEKLY/MONTHLY/YEARLY with
INTERVAL, BYDAY incl. ordinals like `2TU`/`-1FR`, BYMONTHDAY, BYMONTH, BYSETPOS with one weekday,
COUNT, UNTIL); other parts (WKST, TT_*) are dropped and listed. TickTick `repeatFrom == "1"` is
imported as `repeat_from=completion` (assumed from exports; anything else = from due date). A rule
the app rejects is imported without repeat and listed in the summary. Habits: `TT_TIMES=n` ->
n times per week, weekly every N weeks -> `weeks=N days=..`, weekdays -> WEEKDAYS, every N days ->
EVERY_N_DAYS, other monthly/yearly rules -> `rule=`.
