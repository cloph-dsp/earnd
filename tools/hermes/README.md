# Earnd remote tools (adb + Hermes Agent)

These tools let an agent on your own Linux machine read and manage Earnd habits and tasks on your
phone. Everything goes through adb; Earnd has no network permission and no server.

- `earnd-cli`: bash wrapper around `adb shell am broadcast`, prints clean JSON.
- `skills/earnd/SKILL.md`: a Hermes Agent skill that teaches the agent the commands.
- `import-ticktick.py`: one-off importer from TickTick into Earnd.

## What remote access can and cannot do

Allowed: read status (balance, lock state, counts; read-only), list/get/create/update/archive/
unarchive/reorder habits, check in (with quantity) and undo, list/get/create/update/complete/uncomplete/delete tasks.

Always refused with `forbidden`: unlocking, changing the balance or reward minutes, reward
mode, segment mode, changing blocked apps, disabling blocking, bedtime, vacation, hard mode and
all other settings. Photo habits must be checked in on the phone, and photo proof can only be
turned off there. Remote access is **off by default**; turn it on in
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
```

Recurrence mapping: `FREQ=DAILY` -> daily (or every N days with INTERVAL), `FREQ=WEEKLY;BYDAY=..`
-> weekly on those days, `FREQ=MONTHLY` -> monthly. Other rules are imported without repeat
and listed in the summary. Habits (from the export's habit section, if present) become daily
or weekday habits.
