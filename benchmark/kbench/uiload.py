"""Emulates a few users clicking through the UI and calling the API at the same time: every user logs in, then walks
through the dashboard, the monitor lists of every type (the pages and the fragments that render the lists), a monitor's
details, and the API lists, again and again, until it's stopped with SIGTERM/SIGINT. Records every request."""

import argparse
import asyncio
import json
import random
import signal
import time
from pathlib import Path

import httpx

from kbench.collect import ADMIN_CREDENTIALS
from kbench.stack import API_KEY, KUVASZ_URL

MONITOR_TYPES = ("http", "push", "icmp", "tcp", "dns", "docker")
# How long a user waits between two clicks (seconds)
THINK_TIME = (0.5, 2.0)


def ui_paths(http_monitor_id: int | None) -> list[tuple[str, str]]:
    """(name, path) of every UI request of one round, in the order a user would make them."""
    paths = [("ui_dashboard", "/"), ("ui_dashboard_fragment", "/fragments/dashboard")]
    for monitor_type in MONITOR_TYPES:
        paths.append((f"ui_{monitor_type}_monitors", f"/{monitor_type}-monitors"))
        paths.append((f"ui_{monitor_type}_monitors_list", f"/{monitor_type}-monitors/fragments/list"))
    if http_monitor_id is not None:
        paths += [
            ("ui_http_monitor_details", f"/http-monitors/{http_monitor_id}"),
            ("ui_http_monitor_heading", f"/http-monitors/fragments/details-heading/{http_monitor_id}"),
            ("ui_http_monitor_incidents", f"/http-monitors/fragments/details-uptime-incidents/{http_monitor_id}"),
        ]
    return paths


def api_paths() -> list[tuple[str, str]]:
    return [(f"api_{monitor_type}_monitors", f"/api/v2/{monitor_type}-monitors") for monitor_type in MONITOR_TYPES]


async def request(client: httpx.AsyncClient, user: int, name: str, path: str, records: list) -> int | None:
    sent_at = time.time()
    started = time.perf_counter()
    try:
        response = await client.get(path)
        status, size, error = response.status_code, len(response.content), None
    except httpx.HTTPError as e:
        status, size, error = None, 0, type(e).__name__
    records.append({
        "ts": sent_at,
        "user": user,
        "name": name,
        "latency_ms": round((time.perf_counter() - started) * 1000, 1),
        "status": status,
        "bytes": size,
        "error": error,
    })
    return status


async def run_user(user: int, http_monitor_id: int | None, records: list, rng: random.Random):
    async with httpx.AsyncClient(base_url=KUVASZ_URL, timeout=60) as ui, \
            httpx.AsyncClient(base_url=KUVASZ_URL, timeout=60, headers={"X-API-KEY": API_KEY}) as api:
        while True:
            if not ui.cookies:
                try:
                    await ui.post("/auth/login", data=ADMIN_CREDENTIALS)
                except httpx.HTTPError:
                    await asyncio.sleep(5)
                    continue
            for client, paths in ((ui, ui_paths(http_monitor_id)), (api, api_paths())):
                for name, path in paths:
                    status = await request(client, user, name, path, records)
                    if client is ui and status in (302, 303, 401):
                        ui.cookies.clear()  # logged out: log in again in the next round
                    await asyncio.sleep(rng.uniform(*THINK_TIME))


async def run(users: int, http_monitor_id: int | None, out: Path, seed: int):
    records: list[dict] = []
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(sig, stop.set)
    tasks = [
        asyncio.create_task(run_user(user, http_monitor_id, records, random.Random(f"{seed}-{user}")))
        for user in range(users)
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
    parser.add_argument("--users", type=int, default=5)
    parser.add_argument("--http-monitor-id", type=int)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    asyncio.run(run(args.users, args.http_monitor_id, args.out, args.seed))


if __name__ == "__main__":
    main()
