"""Emulates a few people using the UI and an integration polling the API at the same time, the way they would in
real life, until it's stopped with SIGTERM/SIGINT:

- Every UI user logs in, then opens one page after the other (mostly the dashboard, the monitor lists and the details
  of a monitor, sometimes the incidents, rarely the status pages, the maintenance windows or the YAML export), reads
  each for a while, and in the meantime the page refreshes itself like it does in the browser: the dashboard and the
  lists every 30 seconds, the details of a monitor every 15 seconds, the public status page every 60 seconds. The
  charts of a monitor are loaded once, with their default period, and the user switches them to a longer one now and
  then.
- The rare pages, which read the most data at once, are opened at least once during a run by one of the UI users, so
  every heavy path of the app is covered, without everyone hammering them all the time.
- The API client polls the v3 lists and the ongoing incidents every minute, and the endpoints without a v3 equivalent
  (the stats of the monitor types, the status page details) every few minutes.

Records every request, with the status it's expected to answer with."""

import argparse
import asyncio
import json
import random
import signal
import time
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from kbench.collect import ADMIN_CREDENTIALS
from kbench.stack import API_KEY, KUVASZ_URL

MONITOR_TYPES = ("http", "push", "icmp", "tcp", "dns", "docker")
# The incidents of a monitor's details page: a fragment of the monitor's own up to 4.5.0, of the incident list since
LEGACY_INCIDENTS_PATH = "/{type}-monitors/fragments/details-uptime-incidents/{id}"
INCIDENTS_PATH = "/incidents/fragments/monitor/{type}/{id}"
# The default period of the charts on the details page, and the longest one the period selector offers
DEFAULT_CHART_PERIOD = "P1D"
LONGEST_CHART_PERIOD = "P30D"
# Above this many monitors, the default status page is not available at all
DEFAULT_STATUS_PAGE_MAX_MONITORS = 100

# How long a user reads a page (seconds), and how long it takes to click to the next one
DWELL_TIME = (20.0, 90.0)
CLICK_TIME = (2.0, 5.0)
# How often the pages refresh themselves in the browser (seconds)
LIST_REFRESH = 30.0
DASHBOARD_REFRESH = 30.0
DETAILS_REFRESH = 15.0
STATUS_PAGE_REFRESH = 60.0
# How often a user switches the charts of a monitor to the longest period, unless the run makes them pick a period
LONGER_CHART_SHARE = 0.2
# How often the API client polls (seconds), and every how many polls it also asks for the heavier figures
API_POLL = 60.0
API_HEAVY_EVERY = 5

# How often the UI users open each kind of page, besides the rare pages every run covers at least once
VISIT_WEIGHTS = {
    "dashboard": 30,
    "monitor_list": 20,
    "monitor_details": 30,
    "incidents": 10,
    "status_pages": 5,
    "maintenance_windows": 3,
    "export": 2,
}
RARE_VISITS = ("incidents", "status_pages", "maintenance_windows", "export")


@dataclass(frozen=True)
class Probe:
    name: str
    path: str
    expected_status: int = 200
    # Made by htmx, which asks for a part of the page only
    htmx: bool = False


@dataclass(frozen=True)
class Refresh:
    interval: float
    probes: tuple[Probe, ...]


@dataclass(frozen=True)
class Visit:
    """A page: what's requested when it's opened, and what it requests again while it stays open."""
    kind: str
    probes: tuple[Probe, ...]
    refreshes: tuple[Refresh, ...] = ()


@dataclass(frozen=True)
class Targets:
    """What the users open besides the lists: some monitors of every type, and a status page."""
    monitor_ids: dict[str, list[int]] = field(default_factory=dict)
    status_page_id: int | None = None
    status_page_slug: str | None = None
    monitors: int = 0

    @property
    def default_status_page_status(self) -> int:
        return 404 if self.monitors > DEFAULT_STATUS_PAGE_MAX_MONITORS else 200


def period_query(period: str | None, prefix: str = "?") -> str:
    return f"{prefix}period={period}" if period else ""


def dashboard_visit(period: str | None) -> Visit:
    query = period_query(period)
    fragment = Probe("ui_dashboard_fragment", f"/fragments/dashboard{query}", htmx=True)
    return Visit("dashboard", (Probe("ui_dashboard", f"/{query}"), fragment),
                 (Refresh(DASHBOARD_REFRESH, (fragment,)),))


def monitor_list_visit(monitor_type: str) -> Visit:
    fragment = Probe(f"ui_{monitor_type}_monitors_list", f"/{monitor_type}-monitors/fragments/list", htmx=True)
    return Visit("monitor_list", (Probe(f"ui_{monitor_type}_monitors", f"/{monitor_type}-monitors"), fragment),
                 (Refresh(LIST_REFRESH, (fragment,)),))


def chart_probes(monitor_type: str, monitor_id: int, period: str) -> tuple[Probe, ...]:
    """What the charts of the details page fetch from the browser (authenticated by the session, like the UI). Their
    incidents still come from the v2 API."""
    return (
        Probe(f"ui_{monitor_type}_monitor_stats_{period}",
              f"/api/v2/{monitor_type}-monitors/{monitor_id}/stats?period={period}"),
        Probe(f"ui_{monitor_type}_monitor_chart_incidents_{period}",
              f"/api/v2/incidents?monitorId={monitor_id}&period={period}&includeResolved=true"),
    )


def monitor_details_visit(monitor_type: str, monitor_id: int, chart_period: str | None,
                          incidents_path: str = INCIDENTS_PATH) -> Visit:
    """The details of a monitor. Push monitors have no charts. With a chart period, the user switches the charts to
    it after they are loaded with the default one."""
    heading = Probe(f"ui_{monitor_type}_monitor_heading",
                    f"/{monitor_type}-monitors/fragments/details-heading/{monitor_id}", htmx=True)
    incidents = Probe(f"ui_{monitor_type}_monitor_incidents",
                      incidents_path.format(type=monitor_type, id=monitor_id), htmx=True)
    probes = [Probe(f"ui_{monitor_type}_monitor_details", f"/{monitor_type}-monitors/{monitor_id}"), heading,
              incidents]
    refreshed = [heading, incidents]
    if monitor_type == "dns":
        snapshot = Probe("ui_dns_monitor_snapshot", f"/dns-monitors/fragments/snapshot/{monitor_id}", htmx=True)
        probes.append(snapshot)
        refreshed.append(snapshot)
    if monitor_type != "push":
        probes += chart_probes(monitor_type, monitor_id, DEFAULT_CHART_PERIOD)
        if chart_period and chart_period != DEFAULT_CHART_PERIOD:
            probes += chart_probes(monitor_type, monitor_id, chart_period)
    return Visit("monitor_details", tuple(probes), (Refresh(DETAILS_REFRESH, tuple(refreshed)),))


def incidents_visit(period: str | None) -> Visit:
    query = period_query(period)
    fragment = Probe("ui_incidents_list", f"/incidents/fragments/list{query}", htmx=True)
    return Visit("incidents", (Probe("ui_incidents", f"/incidents{query}"), fragment),
                 (Refresh(LIST_REFRESH, (fragment,)),))


def status_pages_visit(targets: Targets) -> Visit:
    """The list of the status pages (its create modal lists every monitor), the default status page, which is not
    available above the limit of its monitors, then the status page of the run: its uncached admin page, and its
    public page, which is cached and refreshes itself."""
    probes = [
        Probe("ui_status_pages", "/status-pages"),
        Probe("ui_status_pages_list", "/status-pages/fragments/list", htmx=True),
        Probe("ui_default_status_page", "/status", targets.default_status_page_status),
    ]
    refreshes = ()
    if targets.status_page_id is not None:
        public = Probe("ui_status_page_public", f"/status/{targets.status_page_slug}")
        probes += [Probe("ui_status_page_details", f"/status-pages/{targets.status_page_id}"), public]
        refreshes = (Refresh(STATUS_PAGE_REFRESH, (Probe(public.name, public.path, htmx=True),)),)
    return Visit("status_pages", tuple(probes), refreshes)


def maintenance_windows_visit() -> Visit:
    # Its create modal lists every monitor
    return Visit("maintenance_windows", (Probe("ui_maintenance_windows", "/maintenance-windows"),))


def export_visit() -> Visit:
    return Visit("export", (Probe("ui_export_yaml", "/api/v2/monitors/export/yaml"),))


@dataclass
class Browser:
    """Picks the pages a UI user opens: the rare ones it's responsible for first, at random points of its first
    visits, then weighted random ones."""
    targets: Targets
    period: str | None
    rng: random.Random
    rare_visits: list[str]
    incidents_path: str = INCIDENTS_PATH

    def __post_init__(self):
        slots = sorted(self.rng.sample(range(len(self.rare_visits) * 3), len(self.rare_visits)))
        self.planned = dict(zip(slots, self.rare_visits, strict=True))
        self.visits = 0

    def next_visit(self) -> Visit:
        kind = self.planned.pop(self.visits, None) or self.rng.choices(
            list(VISIT_WEIGHTS), weights=list(VISIT_WEIGHTS.values()))[0]
        self.visits += 1
        return self.visit(kind)

    def visit(self, kind: str) -> Visit:
        if kind == "monitor_list":
            return monitor_list_visit(self.rng.choice(MONITOR_TYPES))
        if kind == "monitor_details" and self.targets.monitor_ids:
            monitor_type = self.rng.choice(sorted(self.targets.monitor_ids))
            longer = self.period or (LONGEST_CHART_PERIOD if self.rng.random() < LONGER_CHART_SHARE else None)
            return monitor_details_visit(monitor_type, self.rng.choice(self.targets.monitor_ids[monitor_type]),
                                         longer, self.incidents_path)
        if kind == "incidents":
            return incidents_visit(self.period)
        if kind == "status_pages":
            return status_pages_visit(self.targets)
        if kind == "maintenance_windows":
            return maintenance_windows_visit()
        if kind == "export":
            return export_visit()
        return dashboard_visit(self.period)


def rare_visits_of(ui_user: int, ui_users: int) -> list[str]:
    """The rare pages the UI user opens for sure: each of them is opened by one user of the group."""
    return [kind for index, kind in enumerate(RARE_VISITS) if index % max(1, ui_users) == ui_user]


def api_poll(targets: Targets, period: str | None, poll: int) -> list[Probe]:
    """The calls of the API client's poll: a v3 list (a different type every time) and the ongoing incidents, plus
    every few polls the stats of a monitor type and the status pages."""
    monitor_type = MONITOR_TYPES[poll % len(MONITOR_TYPES)]
    probes = [Probe(f"api_{monitor_type}_monitors", f"/api/v3/{monitor_type}-monitors"),
              Probe("api_incidents", "/api/v3/incidents")]
    if poll % API_HEAVY_EVERY == 0:
        stats_type = MONITOR_TYPES[(poll // API_HEAVY_EVERY) % len(MONITOR_TYPES)]
        probes.append(Probe(f"api_{stats_type}_monitors_stats",
                            f"/api/v2/{stats_type}-monitors/stats{period_query(period)}"))
        probes.append(Probe("api_default_status_page", "/api/v2/status-pages/0/details",
                            targets.default_status_page_status))
        if targets.status_page_id is not None:
            probes.append(Probe("api_status_page", f"/api/v2/status-pages/{targets.status_page_id}/details"))
    return probes


async def request(client: httpx.AsyncClient, user: int, probe: Probe, records: list) -> int | None:
    sent_at = time.time()
    started = time.perf_counter()
    try:
        response = await client.get(probe.path, headers={"HX-Request": "true"} if probe.htmx else None)
        status, size, error = response.status_code, len(response.content), None
    except httpx.HTTPError as e:
        status, size, error = None, 0, type(e).__name__
    records.append({
        "ts": sent_at,
        "user": user,
        "name": probe.name,
        "expected_status": probe.expected_status,
        "latency_ms": round((time.perf_counter() - started) * 1000, 1),
        "status": status,
        "bytes": size,
        "error": error,
    })
    return status


async def incidents_path_of(client: httpx.AsyncClient, targets: Targets) -> str:
    """The route of the image under test: the newer one, unless it doesn't exist yet."""
    http_ids = targets.monitor_ids.get("http")
    if not http_ids:
        return INCIDENTS_PATH
    response = await client.get(INCIDENTS_PATH.format(type="http", id=http_ids[0]))
    return LEGACY_INCIDENTS_PATH if response.status_code == 404 else INCIDENTS_PATH


def refresh_schedule(visit: Visit, dwell: float) -> list[tuple[float, Refresh]]:
    """When the refreshes of the page fire while the user reads it, in order."""
    return sorted(
        ((refresh.interval * n, refresh) for refresh in visit.refreshes
         for n in range(1, int(dwell // refresh.interval) + 1)),
        key=lambda item: item[0],
    )


async def run_ui_user(user: int, browser: Browser, records: list, rng: random.Random):
    async with httpx.AsyncClient(base_url=KUVASZ_URL, timeout=60) as ui:
        while True:
            if not ui.cookies:
                try:
                    await ui.post("/auth/login", data=ADMIN_CREDENTIALS)
                    browser.incidents_path = await incidents_path_of(ui, browser.targets)
                except httpx.HTTPError:
                    await asyncio.sleep(5)
                    continue
            visit = browser.next_visit()
            logged_out = False
            for probe in visit.probes:
                logged_out |= await request(ui, user, probe, records) in (302, 303, 401)
            dwell = rng.uniform(*DWELL_TIME)
            opened = time.monotonic()
            for at, refresh in refresh_schedule(visit, dwell):
                await asyncio.sleep(max(0.0, opened + at - time.monotonic()))
                for probe in refresh.probes:
                    logged_out |= await request(ui, user, probe, records) in (302, 303, 401)
            await asyncio.sleep(max(0.0, opened + dwell - time.monotonic()) + rng.uniform(*CLICK_TIME))
            if logged_out:
                ui.cookies.clear()  # log in again before the next page


async def run_api_client(user: int, targets: Targets, period: str | None, records: list, rng: random.Random):
    async with httpx.AsyncClient(base_url=KUVASZ_URL, timeout=60, headers={"X-API-KEY": API_KEY}) as api:
        # The clients don't poll in lockstep
        await asyncio.sleep(rng.uniform(0, API_POLL))
        poll = 0
        while True:
            started = time.monotonic()
            for probe in api_poll(targets, period, poll):
                await request(api, user, probe, records)
            poll += 1
            await asyncio.sleep(max(0.0, started + API_POLL - time.monotonic()))


async def run(users: int, api_clients: int, targets: Targets, period: str | None, out: Path, seed: int):
    records: list[dict] = []
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(sig, stop.set)
    ui_users = max(0, users - api_clients)
    tasks = []
    for user in range(users):
        rng = random.Random(f"{seed}-{user}")
        if user < ui_users:
            browser = Browser(targets, period, rng, rare_visits_of(user, ui_users))
            tasks.append(asyncio.create_task(run_ui_user(user, browser, records, rng)))
        else:
            tasks.append(asyncio.create_task(run_api_client(user, targets, period, records, rng)))
    await stop.wait()
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)
    with out.open("w") as f:
        for record in records:
            f.write(json.dumps(record) + "\n")


def parse_monitor_ids(value: str) -> dict[str, list[int]]:
    """e.g. "http=12:13,tcp=40" """
    pairs = (pair.split("=", 1) for pair in value.split(",") if pair)
    return {monitor_type: [int(i) for i in ids.split(":")] for monitor_type, ids in pairs}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--users", type=int, default=5, help="everyone using Kuvasz, the API clients included")
    parser.add_argument("--api-clients", type=int, default=1, help="how many of the users are API clients")
    parser.add_argument("--monitor-ids", type=parse_monitor_ids, default={},
                        help="the monitors the users open of each type, e.g. http=12:13,tcp=40")
    parser.add_argument("--status-page-id", type=int)
    parser.add_argument("--status-page-slug")
    parser.add_argument("--monitors", type=int, default=0, help="the number of monitors, which decides whether the "
                                                                "default status page is expected to be available")
    parser.add_argument("--period", help="the period (ISO-8601, e.g. P30D) every user picks on the dashboard, the "
                                         "incident list, the charts and the stats, instead of the defaults")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    targets = Targets(args.monitor_ids, args.status_page_id, args.status_page_slug, args.monitors)
    asyncio.run(run(args.users, args.api_clients, targets, args.period, args.out, args.seed))


if __name__ == "__main__":
    main()
