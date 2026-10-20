"""Emulates the push clients (cron jobs, backups, ...) of the push monitors, according to their target behavior, and
records the latency and the outcome of every heartbeat. Runs on the host, outside the Docker VM, until it's stopped
with SIGTERM/SIGINT."""

import argparse
import asyncio
import json
import random
import signal
import time
from pathlib import Path

import httpx

from kbench.stack import KUVASZ_URL

# The first heartbeats are spread over this many seconds, like clients that don't start at the very same moment
FIRST_HEARTBEAT_SPREAD = 30
# How far a healthy client may drift from its schedule (as a fraction of the grace period)
JITTER = 0.3


def schedule(behavior: str, interval: int, grace: int, rng: random.Random):
    """Yields the delays (seconds) between the consecutive heartbeats of a client, starting with the first one."""
    yield rng.uniform(0, FIRST_HEARTBEAT_SPREAD)
    if behavior == "down":
        return  # dies right after its first heartbeat
    skip = True
    while True:
        if behavior == "slow":
            # Consistently late, but within the grace period
            yield interval + grace * 0.6
        elif behavior == "flapping" and skip:
            # Skips a heartbeat, which is a missed one (DOWN), then comes back (UP) with the next one
            yield interval * 2 + grace + 1
        else:
            yield interval + rng.uniform(-JITTER, JITTER) * grace
        skip = not skip


def send_times(delays, start: float):
    """The moments of the heartbeats: like a cron job, a client keeps its schedule, however long its previous heartbeat
    took to answer."""
    at = start
    for delay in delays:
        at += delay
        yield at


async def run_client(client: httpx.AsyncClient, pusher: dict, records: list, rng: random.Random):
    url = f"{KUVASZ_URL}/api/v2/push-monitors/heartbeats/{pusher['secret']}"
    delays = schedule(pusher["behavior"], pusher["interval"], pusher["grace"], rng)
    for at in send_times(delays, time.monotonic()):
        await asyncio.sleep(max(at - time.monotonic(), 0))
        sent_at = time.time()
        started = time.perf_counter()
        try:
            status = (await client.post(url)).status_code
            error = None
        except httpx.HTTPError as e:
            status, error = None, type(e).__name__
        records.append({
            "ts": sent_at,
            "name": pusher["name"],
            "latency_ms": round((time.perf_counter() - started) * 1000, 2),
            "status": status,
            "error": error,
        })


async def run(pushers: list[dict], out: Path, seed: int):
    records: list[dict] = []
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(sig, stop.set)

    limits = httpx.Limits(max_connections=200, max_keepalive_connections=50)
    async with httpx.AsyncClient(timeout=10, limits=limits) as client:
        tasks = [
            asyncio.create_task(run_client(client, p, records, random.Random(f"{seed}-{p['name']}")))
            for p in pushers
        ]
        await stop.wait()
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

    with out.open("w") as f:
        for record in records:
            f.write(json.dumps(record) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pushers", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    asyncio.run(run(json.loads(args.pushers.read_text()), args.out, args.seed))


if __name__ == "__main__":
    main()
