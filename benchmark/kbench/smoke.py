"""Smoke test of the benchmark stack: boots it with a small set, and checks that every stub monitor ends up in the state
its target behavior implies, that every type writes its per-check rows, and that notifications reach the sink."""

import argparse
import json
import subprocess
import time

import httpx

from kbench import generate, schema
from kbench.analyze import due_for_down
from kbench.stack import KUVASZ_URL, ROOT, Stack, StackSettings, db, load_profiles


def due_after(e: dict, push_grace: int) -> float:
    """Seconds after the checks started (Kuvasz became healthy) by which the monitor must have reached its expected
    status."""
    if e["expected"] == "DOWN":
        return due_for_down(e, push_grace)
    # UP: the first check of a monitor runs within one interval, a push monitor is UP right after its first heartbeat
    return 30 if e["type"] == "push" else e["interval"] + 30


def evaluate(expectations: dict, statuses: dict, elapsed: float, push_graces: dict) -> dict:
    result = {"ok": [], "mismatch": [], "not_due": [], "no_expectation": []}
    for name, e in expectations.items():
        if e["expected"] is None:
            result["no_expectation"].append(name)
            continue
        if elapsed < due_after(e, push_graces.get(name, 0)):
            result["not_due"].append(name)
            continue
        # Without the pusher, nobody keeps the push clients alive after their first heartbeat
        if e["type"] == "push" and e["expected"] == "UP" and elapsed > e["interval"] + push_graces.get(name, 0):
            result["not_due"].append(name)
            continue
        actual = statuses.get(name, {}).get("status")
        if actual == e["expected"]:
            result["ok"].append(name)
        else:
            result["mismatch"].append((name, e["expected"], actual, statuses.get(name, {}).get("error")))
    return result


def send_first_heartbeats(pushers: list[dict]):
    """Kuvasz evaluates a push monitor only after its first heartbeat, so every client sends one, even the "down" ones,
    which are clients that die right after it."""
    with httpx.Client(timeout=5) as client:
        for p in pushers:
            client.post(f"{KUVASZ_URL}/api/v2/push-monitors/heartbeats/{p['secret']}").raise_for_status()


def webhook_hits() -> int:
    result = subprocess.run(
        ["docker", "exec", "kb-openresty-a", "wget", "-qO-", "http://127.0.0.1/stats"],
        capture_output=True, text=True, check=False,
    )
    return int(result.stdout.strip() or 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", default="S")
    parser.add_argument("--mix", default="balanced")
    parser.add_argument("--scale", type=int, default=1)
    parser.add_argument("--wait", type=int, default=420, help="seconds to let the checks run before evaluating")
    parser.add_argument("--keep", action="store_true", help="leave the stack running afterwards")
    args = parser.parse_args()

    out = ROOT / "generated"
    summary = generate.write_step(out, args.mix, args.scale)
    subprocess.run(["bash", str(ROOT / "stack" / "pki.sh")], check=True)
    expectations = json.loads((out / "expectations.json").read_text())
    pushers = json.loads((out / "pushers.json").read_text())

    profiles = load_profiles()
    stack = Stack(
        StackSettings(args.profile, profiles["defaults"]["heap"], False, profiles["defaults"]["kuvasz_tag"],
                      out / "gc"),
        profiles,
    )
    print(f"Monitors: {summary['stub_monitors']} stub + {summary['real_slice_monitors']} real")
    stack.reset()
    stack.up_dependencies()
    startup = stack.start_kuvasz()
    # Wall clock on purpose: the monotonic clock of macOS stops while the machine sleeps
    started = time.time()
    print(f"Kuvasz healthy after {startup:.1f}s")
    send_first_heartbeats(pushers)

    while (elapsed := time.time() - started) < args.wait:
        print(f"  waiting... {elapsed:.0f}/{args.wait}s", end="\r", flush=True)
        time.sleep(10)
    print()

    with db() as conn:
        statuses = schema.current_statuses(conn)
        log_counts = schema.check_log_counts(conn)
    result = evaluate(expectations, statuses, time.time() - started, {p["name"]: p["grace"] for p in pushers})

    print(f"OK: {len(result['ok'])}, mismatches: {len(result['mismatch'])}, not due yet: {len(result['not_due'])}, "
          f"no expectation: {len(result['no_expectation'])}")
    for name, expected, actual, error in result["mismatch"]:
        print(f"  MISMATCH {name}: expected {expected}, got {actual} ({error})")
    print("Real slice:")
    for name, e in expectations.items():
        if e["behavior"] == "real":
            s = statuses.get(name, {})
            print(f"  {name}: {s.get('status')} {s.get('error') or ''}")
    print(f"Per-check rows: {log_counts}")
    print(f"Webhook hits: {webhook_hits()}")
    errors = [line for line in stack.kuvasz_logs().splitlines() if " ERROR " in line]
    print(f"ERROR lines in the Kuvasz log: {len(errors)}")
    for line in errors[:20]:
        print(f"  {line[:300]}")

    if not args.keep:
        stack.reset()
    failed = result["mismatch"] or errors or any(count == 0 for count in log_counts.values())
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
