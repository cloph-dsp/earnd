#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Import TickTick tasks (and habits, if present) into Earnd via earnd-cli.

Sources (pick one):
  --export FILE       JSON from the TickTick Open API (a task list, {"tasks": [...]},
                      {"projects": [{"tasks": [...]}]}, or a list of project-data objects).
  --token-file PATH   Read an Open API access token from PATH and fetch open tasks.

Nothing is written unless --apply is given (dry run is the default).
"""
import argparse
import re
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request

API = "https://api.ticktick.com/open/v1"
DAYS = {"MO": "mon", "TU": "tue", "WE": "wed", "TH": "thu", "FR": "fri", "SA": "sat", "SU": "sun"}
PRIORITY = {0: "NONE", 1: "LOW", 3: "MEDIUM", 5: "HIGH"}


def parse_rrule(rule):
    """Map an RRULE to earnd-cli args. Returns (args, note) where note explains a fallback."""
    if not rule:
        return {}, None
    body = rule.split("RRULE:", 1)[-1]
    parts = dict(p.split("=", 1) for p in body.split(";") if "=" in p)
    freq = parts.get("FREQ", "").upper()
    interval = int(parts.get("INTERVAL", "1") or 1)
    if freq == "DAILY":
        return ({"repeat": "DAILY"} if interval == 1 else {"repeat": "EVERY_N_DAYS", "interval": str(interval)}), None
    if freq == "WEEKLY":
        days = [DAYS[d[-2:]] for d in parts.get("BYDAY", "").split(",") if d[-2:] in DAYS]
        args = {"repeat": "WEEKLY"}
        if days:
            args["days"] = ",".join(days)
        if interval != 1:
            args["interval"] = str(interval)
        return args, None
    if freq == "MONTHLY":
        note = "BYDAY/BYSETPOS monthly rule imported as same day each month" if ("BYDAY" in parts or "BYSETPOS" in parts) else None
        return {"repeat": "MONTHLY", "interval": str(interval)}, note
    if freq == "YEARLY":
        # Earnd repeats on the due date's month/day; BYMONTH/BYMONTHDAY normally match it.
        note = f"every {interval} years imported as yearly" if interval != 1 else None
        if "BYDAY" in parts or "BYSETPOS" in parts:
            note = "BYDAY yearly rule imported as same date each year"
        return {"repeat": "YEARLY"}, note
    return {}, f"unsupported rule {body!r}, imported without repeat"


# Internal reference junk some TickTick notes carry, e.g. "[id:12]" or "adhd-34".
JUNK = re.compile(r"^\s*(?:\[id:\s*\d+\]|adhd-\d+)[\s:,;.-]*", re.IGNORECASE)


def clean_notes(text):
    out = []
    for line in (text or "").splitlines():
        while True:
            stripped = JUNK.sub("", line, count=1)
            if stripped == line:
                break
            line = stripped
        if line.strip():
            out.append(line.rstrip())
    return "\n".join(out).strip()


def parse_due(task):
    raw = task.get("dueDate") or task.get("startDate")
    if not raw:
        return {}
    try:
        when = dt.datetime.strptime(raw, "%Y-%m-%dT%H:%M:%S.%f%z")
    except ValueError:
        try:
            when = dt.datetime.strptime(raw, "%Y-%m-%dT%H:%M:%S%z")
        except ValueError:
            return {"due": raw[:10]}
    tz = task.get("timeZone")
    try:
        from zoneinfo import ZoneInfo
        when = when.astimezone(ZoneInfo(tz)) if tz else when.astimezone()
    except Exception:
        when = when.astimezone()
    args = {"due": when.date().isoformat()}
    if not task.get("isAllDay", False):
        args["time"] = when.strftime("%H:%M")
    return args


def extract(data):
    """Return (tasks, habits) from any of the accepted export shapes."""
    tasks, habits = [], []
    if isinstance(data, list):
        for item in data:
            t, h = extract(item) if isinstance(item, dict) and ("tasks" in item or "project" in item) else ([item], [])
            tasks += t
            habits += h
        return tasks, habits
    if not isinstance(data, dict):
        return tasks, habits
    names = {p.get("id"): p.get("name") for p in data.get("projects", []) or [] if isinstance(p, dict)}
    own = (data.get("project") or {}).get("name") if isinstance(data.get("project"), dict) else None
    for t in data.get("tasks", []) or []:
        t.setdefault("_project", own or names.get(t.get("projectId")))
        tasks.append(t)
    for p in data.get("projects", []) or []:
        for t in p.get("tasks", []) or []:
            t.setdefault("_project", p.get("name"))
            tasks.append(t)
    habits += data.get("habits", []) or []
    return tasks, habits


def fetch_api(token):
    def get(path):
        req = urllib.request.Request(API + path, headers={"Authorization": f"Bearer {token}"})
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r)
    tasks = []
    for project in get("/project"):
        for t in get(f"/project/{project['id']}/data").get("tasks", []) or []:
            t["_project"] = project.get("name")
            tasks.append(t)
    for t in get("/project/inbox/data").get("tasks", []) or []:
        t["_project"] = "Inbox"
        tasks.append(t)
    return tasks, []


class Earnd:
    def __init__(self, cli, apply):
        self.cli, self.apply, self.fake_id = cli, apply, 0

    def run(self, cmd, args):
        argv = [self.cli, cmd] + [f"{k}={v}" for k, v in args.items() if v not in (None, "")]
        if not self.apply:
            self.fake_id += 1
            print("  would run:", " ".join(argv[1:]))
            return {"ok": True, "id": -self.fake_id}
        proc = subprocess.run(argv, capture_output=True, text=True)
        try:
            out = json.loads(proc.stdout.strip() or "{}")
        except json.JSONDecodeError:
            out = {"ok": False, "error": "bad_output", "message": proc.stdout[:200]}
        if not out.get("ok"):
            print(f"  failed {cmd}: {out.get('error')} {out.get('message') or ''}", file=sys.stderr)
        return out


def import_task(earnd, t, notes):
    if t.get("status", 0) != 0:
        return False
    args = {"title": t.get("title", "").strip() or "(untitled)"}
    body = clean_notes(t.get("content") or t.get("desc") or "")
    if body:
        args["notes"] = body
    args.update(parse_due(t))
    prio = PRIORITY.get(t.get("priority", 0))
    if prio and prio != "NONE":
        args["priority"] = prio
    rep, note = parse_rrule(t.get("repeatFlag"))
    if rep and "due" not in args:
        note, rep = "repeating task without a due date imported without repeat", {}
    args.update(rep)
    if note:
        notes.append(f"{args['title']}: {note}")
    res = earnd.run("tasks.create", args)
    if res.get("ok"):
        for item in t.get("items", []) or []:
            if item.get("status", 0) == 0 and item.get("title"):
                earnd.run("tasks.create", {"title": item["title"], "parent": res["id"]})
    return res.get("ok", False)


def import_habit(earnd, h, notes):
    rep, note = parse_rrule(h.get("repeatRule"))
    args = {"name": h.get("name", "").strip() or "(habit)"}
    if rep.get("repeat") == "WEEKLY" and rep.get("interval"):
        notes.append(f"{args['name']}: every {rep['interval']} weeks imported as every week")
    if rep.get("repeat") == "WEEKLY" and rep.get("days"):
        args.update({"schedule": "WEEKDAYS", "days": rep["days"]})
    elif rep.get("repeat") == "EVERY_N_DAYS":
        args.update({"schedule": "EVERY_N_DAYS", "every": rep["interval"]})
    elif rep and rep.get("repeat") != "DAILY":
        notes.append(f"{args['name']}: rule imported as daily")
    if note:
        notes.append(f"{args['name']}: {note}")
    goal = h.get("goal")
    if isinstance(goal, (int, float)) and goal > 1 and float(goal).is_integer() and goal <= 20:
        args["times"] = str(int(goal))
    return earnd.run("habits.create", args).get("ok", False)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--export", help="TickTick JSON export file")
    src.add_argument("--token-file", help="file containing a TickTick Open API access token")
    ap.add_argument("--apply", action="store_true", help="actually create items (default: dry run)")
    ap.add_argument("--dry-run", action="store_true", help="preview only (the default)")
    ap.add_argument("--no-habits", action="store_true")
    ap.add_argument("--skip-project", action="append", default=[], metavar="NAME",
                    help="skip every task in this TickTick project (case-insensitive; repeatable)")
    ap.add_argument("--cli", default=os.environ.get("EARND_CLI") or shutil.which("earnd-cli")
                    or os.path.join(os.path.dirname(os.path.abspath(__file__)), "earnd-cli"))
    a = ap.parse_args()
    apply = a.apply and not a.dry_run

    if a.export:
        with open(a.export, encoding="utf-8") as f:
            tasks, habits = extract(json.load(f))
    else:
        with open(os.path.expanduser(a.token_file), encoding="utf-8") as f:
            token = f.read().strip()
        if not token:
            sys.exit("token file is empty")
        tasks, habits = fetch_api(token)

    skip = {n.strip().casefold() for n in a.skip_project}
    if skip:
        before = len(tasks)
        tasks = [t for t in tasks if (t.get("_project") or "").strip().casefold() not in skip]
        print(f"Skipping {before - len(tasks)} task(s) in project(s): {', '.join(a.skip_project)}")

    earnd = Earnd(a.cli, apply)
    notes = []
    print("Dry run: nothing will be written. Re-run with --apply." if not apply else "Importing into Earnd...")
    nt = sum(import_task(earnd, t, notes) for t in tasks)
    nh = 0 if a.no_habits else sum(import_habit(earnd, h, notes) for h in habits)
    print(f"Tasks: {nt} of {len(tasks)}; habits: {nh} of {0 if a.no_habits else len(habits)}"
          f" ({'created' if apply else 'would be created'}). Completed tasks are skipped.")
    for n in notes:
        print("  note:", n)


if __name__ == "__main__":
    main()
