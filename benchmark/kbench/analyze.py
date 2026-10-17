"""Turns the raw data of a step (the database, the samples of the collector, the heartbeats of the pusher) into the
metrics of the step, and decides whether Kuvasz handled the load."""

import json
import math
import re
import statistics
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from kbench import schema
from kbench.pusher import FIRST_HEARTBEAT_SPREAD

ICMP_PACKET_INTERVAL_SECONDS = 1  # ping's default, the checks send packet-count packets this far apart
DEFAULT_ICMP_PACKET_COUNT = 3
# An HTTP check is retried twice (after 0.5s, then 1.5s) when it fails with an HttpException, read timeouts included
HTTP_WORST_CHECK_SECONDS = 3 * 30 + 0.5 + 1.5
OTHER_TIMEOUT_SECONDS = 5
# Kuvasz looks for missed heartbeats this often
HEARTBEAT_CHECK_PERIOD_SECONDS = 5
# Below this, the per-row size of the check log tables is dominated by page and index overhead
MIN_ROWS_FOR_SIZE_PROJECTION = 20_000
EXPECTED_UP = ("healthy", "slow")


@dataclass(frozen=True)
class Window:
    """The measurement window of a step. step_start is when the Kuvasz container (re)started: check pairs and uptime
    events from before it belong to the previous Kuvasz process. checks_start is when Kuvasz became healthy, i.e. when
    its checks (and the pusher) started."""
    step_start: float
    start: float
    end: float
    checks_start: float | None = None

    @property
    def checks_started(self) -> float:
        return self.checks_start if self.checks_start is not None else self.step_start

    @property
    def seconds(self) -> float:
        return self.end - self.start

    def ts(self, value: float) -> datetime:
        return datetime.fromtimestamp(value, tz=timezone.utc)

    def contains(self, ts: float) -> bool:
        return self.start <= ts <= self.end


def percentile(values: list[float], p: float) -> float | None:
    """Nearest-rank percentile: the smallest value that at least p% of the values are less than or equal to."""
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(p / 100 * len(ordered)) - 1)]


def allowed_lag(interval: int) -> float:
    """How late a check may be, before it counts as late: 5 seconds, or 10% of the interval for the long ones."""
    return max(5.0, 0.1 * interval)


def intrinsic_duration(monitor_type: str, latency_ms: float | None) -> float:
    """How long a check itself took, as far as its per-check row tells. Checks run with a fixed delay (the next one is
    scheduled `interval` after the previous one finished), so this is part of the gap between two checks, not lag."""
    seconds = (latency_ms or 0) / 1000
    if monitor_type == "icmp":
        seconds += (DEFAULT_ICMP_PACKET_COUNT - 1) * ICMP_PACKET_INTERVAL_SECONDS
    return seconds


# --- check timing -------------------------------------------------------------------------------------------------

def fetch_check_gaps(conn, window: Window) -> list[tuple[str, str, float, float | None]]:
    """(type, monitor name, seconds since the previous check, latency in ms) of every check that finished within the
    window, and whose previous check was done by the same Kuvasz process."""
    rows = []
    for monitor_type, (table, latency_column) in schema.CHECK_LOG_TABLES.items():
        monitor_table = schema.MONITOR_TABLES[monitor_type]
        rows += [
            (monitor_type, name, gap, latency)
            for name, gap, latency in conn.execute(
                f"""
                WITH checks AS (
                    SELECT monitor_id, created_at, {latency_column} AS latency,
                           lag(created_at) OVER (PARTITION BY monitor_id ORDER BY created_at) AS previous
                    FROM {table}
                    WHERE created_at >= %(step_start)s
                )
                SELECT m.name, extract(epoch FROM c.created_at - c.previous)::float8, c.latency
                FROM checks c JOIN {monitor_table} m ON m.id = c.monitor_id
                WHERE c.previous IS NOT NULL AND c.created_at BETWEEN %(start)s AND %(end)s
                """,
                {"step_start": window.ts(window.step_start), "start": window.ts(window.start),
                 "end": window.ts(window.end)},
            ).fetchall()
        ]
    return rows


def count_checks(conn, window: Window) -> dict[str, int]:
    counts = {}
    for monitor_type, (table, _) in schema.CHECK_LOG_TABLES.items():
        counts[monitor_type] = conn.execute(
            f"SELECT count(*) FROM {table} WHERE created_at BETWEEN %s AND %s",
            (window.ts(window.start), window.ts(window.end)),
        ).fetchone()[0]
    return counts


def timing_metrics(gaps: list[tuple], expectations: dict, check_counts: dict, window: Window) -> dict:
    """Lag of the checks per type, measured on the monitors whose checks succeed in a predictable time (healthy and
    slow targets). A check is "missed" if it's late by more than a whole interval, i.e. a check slot was skipped."""
    by_type: dict[str, dict] = {}
    checked = set()
    for monitor_type, name, gap, latency in gaps:
        e = expectations.get(name)
        if e is None or e["behavior"] not in EXPECTED_UP:
            continue
        checked.add(name)
        interval = e["interval"]
        lag = gap - interval - intrinsic_duration(monitor_type, latency)
        t = by_type.setdefault(monitor_type, {"lags": [], "normalized": [], "missed": 0})
        t["lags"].append(lag)
        t["normalized"].append(lag / allowed_lag(interval))
        if lag > interval:
            t["missed"] += 1

    # Monitors that should have been checked several times within the window, but weren't checked at all
    starved = [
        name for name, e in expectations.items()
        if e["behavior"] in EXPECTED_UP and e["type"] in schema.CHECK_LOG_TABLES and name not in checked
        and e["interval"] * 3 <= window.seconds and has_check_log(e)
    ]

    result = {}
    for monitor_type in schema.CHECK_LOG_TABLES:
        t = by_type.get(monitor_type, {"lags": [], "normalized": [], "missed": 0})
        n = len(t["lags"])
        result[monitor_type] = {
            "checks": check_counts.get(monitor_type, 0),
            "checks_per_second": round(check_counts.get(monitor_type, 0) / window.seconds, 3),
            "measured_gaps": n,
            "lag_p50_s": rounded(percentile(t["lags"], 50)),
            "lag_p95_s": rounded(percentile(t["lags"], 95)),
            "lag_p99_s": rounded(percentile(t["lags"], 99)),
            "lag_max_s": rounded(max(t["lags"]) if t["lags"] else None),
            "lag_p99_normalized": rounded(percentile(t["normalized"], 99)),
            "late_ratio": rounded(sum(v > 1 for v in t["normalized"]) / n if n else None, 4),
            "missed": t["missed"],
            "missed_ratio": rounded(t["missed"] / n if n else None, 4),
            "starved_monitors": sum(1 for name in starved if expectations[name]["type"] == monitor_type),
        }
    return result


def has_check_log(e: dict) -> bool:
    # Docker monitors write a per-check row only with their metrics history on, which isn't in the expectations
    return e["type"] != "docker"


# --- correctness --------------------------------------------------------------------------------------------------

def fetch_events(conn, window: Window) -> dict[str, list[tuple[str, float, float | None, float]]]:
    """(status, started, ended, updated) of every uptime event that overlaps the window, by monitor name. Every check
    that finds the same status again moves `updated` forward, so it tells whether the current Kuvasz process has
    confirmed an event that was opened by a previous one."""
    events: dict[str, list] = {}
    for monitor_type, monitor_table in schema.MONITOR_TABLES.items():
        for name, status, started, ended, updated in conn.execute(
            f"""
            SELECT m.name, e.status::text, e.started_at, e.ended_at, e.updated_at
            FROM {monitor_table} m JOIN {schema.UPTIME_EVENT_TABLES[monitor_type]} e ON e.monitor_id = m.id
            WHERE e.started_at <= %(end)s AND (e.ended_at IS NULL OR e.ended_at >= %(start)s)
            """,
            {"start": window.ts(window.start), "end": window.ts(window.end)},
        ).fetchall():
            events.setdefault(name, []).append(
                (status, started.timestamp(), ended.timestamp() if ended else None, updated.timestamp())
            )
    return events


def due_for_down(e: dict, push_grace: int) -> float:
    """Seconds after the checks (and the pusher) started, by which a failing monitor must be DOWN."""
    interval, threshold = e["interval"], e["threshold"]
    if e["type"] == "push":
        # The first heartbeat is needed before Kuvasz evaluates a push monitor at all
        return FIRST_HEARTBEAT_SPREAD + interval * threshold + push_grace + HEARTBEAT_CHECK_PERIOD_SECONDS + 30
    worst_check = HTTP_WORST_CHECK_SECONDS if e["type"] == "http" else OTHER_TIMEOUT_SECONDS
    return (interval + worst_check) * threshold + 30


def _false_down(event: tuple, window: Window) -> bool:
    """Whether a DOWN event of a healthy target is this process's mistake: it opened the event, or it inherited one from
    the previous step and confirmed it with its own check, so it's still open. An inherited DOWN that this process
    closed (by finding the target UP) is the previous step's mistake."""
    status, started, ended, updated = event
    if status != "DOWN":
        return False
    return started >= window.step_start or (ended is None and updated >= window.step_start)


def _seen_by_this_process(event: tuple, window: Window) -> bool:
    """Whether the current Kuvasz process opened the event, or confirmed it with a check of its own. An event that was
    only inherited from the previous step (e.g. a DOWN left open by a failing step) says nothing about this one."""
    _, started, _, updated = event
    return started >= window.step_start or updated >= window.step_start


def correctness_metrics(events: dict, expectations: dict, window: Window, push_graces: dict,
                        excluded: set[str] = frozenset()) -> dict:
    false_positives, false_negatives = [], []
    for name, e in expectations.items():
        if e["behavior"] == "real" or name in excluded:
            continue
        monitor_events = events.get(name, [])
        if e["expected"] == "UP":
            if any(_false_down(ev, window) for ev in monitor_events):
                false_positives.append(name)
        elif e["expected"] == "DOWN":
            if window.end - window.checks_started < due_for_down(e, push_graces.get(name, 0)):
                continue
            # The event that is open at the end of the window, and that this process has confirmed
            open_at_end = [ev[0] for ev in monitor_events if ev[1] <= window.end
                           and (ev[2] is None or ev[2] >= window.end) and _seen_by_this_process(ev, window)]
            if open_at_end != ["DOWN"]:
                false_negatives.append(name)
    return {
        "false_positives": len(false_positives),
        "false_negatives": len(false_negatives),
        "false_positive_examples": sorted(false_positives)[:20],
        "false_negative_examples": sorted(false_negatives)[:20],
    }


def recovery_metrics(events: dict, expectations: dict, group: set[str], reconnected_at: float) -> dict:
    """How fast the healthy monitors of the reconnected targets got back UP. Monitors that weren't checked during the
    outage at all (long intervals) never went DOWN, and are counted apart."""
    recovery_seconds, never_down, not_recovered = [], 0, []
    for name in group:
        if expectations[name]["behavior"] not in EXPECTED_UP:
            continue
        monitor_events = sorted(events.get(name, []), key=lambda event: event[1])
        if all(ev[0] == "UP" for ev in monitor_events) and monitor_events:
            never_down += 1
            continue
        ups = [ev[1] for ev in monitor_events if ev[0] == "UP" and ev[1] >= reconnected_at]
        if ups:
            recovery_seconds.append(min(ups) - reconnected_at)
        else:
            not_recovered.append(name)
    return {
        "recovered": len(recovery_seconds),
        "never_down": never_down,
        "not_recovered": len(not_recovered),
        "not_recovered_examples": sorted(not_recovered)[:20],
        "recovery_p50_s": rounded(percentile(recovery_seconds, 50)),
        "recovery_p95_s": rounded(percentile(recovery_seconds, 95)),
        "recovery_max_s": rounded(max(recovery_seconds) if recovery_seconds else None),
    }


# --- samples ------------------------------------------------------------------------------------------------------

def _in_window(samples: list[dict], kind: str, window: Window) -> list[dict]:
    return [s for s in samples if s["kind"] == kind and window.contains(s["ts"])]


def _delta(first: dict | None, last: dict | None, key: str) -> float | None:
    if not first or not last or key not in first or key not in last:
        return None
    return last[key] - first[key]


def resource_metrics(samples: list[dict], window: Window, kuvasz_cpus: float) -> dict:
    docker = _in_window(samples, "docker", window)
    kuvasz_cpu = [s["containers"]["kb-kuvasz"]["cpu_percent"] for s in docker if "kb-kuvasz" in s["containers"]]
    kuvasz_mem = [s["containers"]["kb-kuvasz"]["mem_bytes"] for s in docker if "kb-kuvasz" in s["containers"]]
    pg_cpu = [s["containers"]["kb-postgres"]["cpu_percent"] for s in docker if "kb-postgres" in s["containers"]]
    pg_mem = [s["containers"]["kb-postgres"]["mem_bytes"] for s in docker if "kb-postgres" in s["containers"]]
    target_cpu = {}
    for s in docker:
        for name, stats in s["containers"].items():
            if name.startswith(("kb-openresty", "kb-coredns")):
                target_cpu[name] = max(target_cpu.get(name, 0), stats["cpu_percent"])

    cgroups = _in_window(samples, "cgroup", window)
    kuvasz_cg = [s["containers"]["kb-kuvasz"] for s in cgroups if "kb-kuvasz" in s["containers"]]
    first, last = (kuvasz_cg[0], kuvasz_cg[-1]) if len(kuvasz_cg) >= 2 else (None, None)
    elapsed = (cgroups[-1]["ts"] - cgroups[0]["ts"]) if len(cgroups) >= 2 else None
    usage = _delta(first, last, "usage_usec")
    periods, throttled = _delta(first, last, "nr_periods"), _delta(first, last, "nr_throttled")

    return {
        "kuvasz_cpu_cores_avg": rounded(usage / 1e6 / elapsed if usage is not None and elapsed else None, 3),
        "kuvasz_cpu_utilization": rounded(
            usage / 1e6 / elapsed / kuvasz_cpus if usage is not None and elapsed else None, 3),
        "kuvasz_cpu_percent_p95": rounded(percentile(kuvasz_cpu, 95)),
        "kuvasz_throttled_ratio": rounded(throttled / periods if periods else None, 3),
        "kuvasz_mem_max_mb": mb(max(kuvasz_mem) if kuvasz_mem else None),
        "kuvasz_anon_mem_max_mb": mb(max((c.get("anon", 0) for c in kuvasz_cg), default=None)),
        "postgres_cpu_percent_avg": rounded(statistics.fmean(pg_cpu) if pg_cpu else None),
        "postgres_cpu_percent_p95": rounded(percentile(pg_cpu, 95)),
        "postgres_mem_max_mb": mb(max(pg_mem) if pg_mem else None),
        "target_cpu_percent_max": {k: rounded(v) for k, v in sorted(target_cpu.items())},
    }


def jvm_metrics(samples: list[dict], window: Window) -> dict:
    scrapes = [s["metrics"] for s in _in_window(samples, "prometheus", window)]
    if not scrapes:
        return {}
    first, last = scrapes[0], scrapes[-1]

    def series(name: str, key: str | None = None) -> list[float]:
        values = [m.get(name) for m in scrapes]
        if key is not None:
            values = [v.get(key) if isinstance(v, dict) else None for v in values]
        return [v for v in values if v is not None]

    pause = _delta(first, last, "jvm_gc_pause_seconds_sum")
    acquire_sum = _delta(first, last, "hikaricp_connections_acquire_seconds_sum")
    acquire_count = _delta(first, last, "hikaricp_connections_acquire_seconds_count")
    usage_sum = _delta(first, last, "hikaricp_connections_usage_seconds_sum")
    usage_count = _delta(first, last, "hikaricp_connections_usage_seconds_count")
    allocated = _delta(first, last, "jvm_gc_memory_allocated_bytes_total")
    logback_first, logback_last = first.get("logback_events_total", {}), last.get("logback_events_total", {})
    # Note: the queue of the "scheduled" executor holds the timer of every monitor's next check, it's not a backlog
    queued = {}
    for m in scrapes:
        for name, value in (m.get("executor_queued_tasks") or {}).items():
            queued[name] = max(queued.get(name, 0), value)

    pools = {}
    for m in scrapes:
        for pool, value in (m.get("jvm_memory_used_by_pool") or {}).items():
            pools[pool] = max(pools.get(pool, 0), value)
    netty_direct = [
        sum(v for k, v in (m.get("netty") or {}).items()
            if k.startswith("netty_alloc_memory_used") and 'memory="direct"' in k)
        for m in scrapes
    ]
    return {
        "heap_used_max_mb": mb(max(series("jvm_memory_used_bytes", "heap"), default=None)),
        "memory_by_pool_max_mb": {pool: mb(value) for pool, value in sorted(pools.items())},
        "direct_buffers_max_mb": mb(max(series("jvm_buffer_memory_used_bytes", "direct"), default=None)),
        "netty_direct_max_mb": mb(max(netty_direct)) if any(netty_direct) else None,
        "heap_max_mb": mb(max(series("jvm_memory_max_bytes", "heap"), default=None)),
        "heap_live_after_gc_max_mb": mb(max(series("jvm_gc_live_data_size_bytes"), default=None)),
        "nonheap_used_max_mb": mb(max(series("jvm_memory_used_bytes", "nonheap"), default=None)),
        "gc_overhead_ratio": rounded(pause / window.seconds if pause is not None else None, 4),
        "gc_pauses": _delta(first, last, "jvm_gc_pause_seconds_count"),
        "allocation_mb_per_s": rounded(allocated / window.seconds / 2**20 if allocated is not None else None, 2),
        "threads_max": max(series("jvm_threads_live_threads"), default=None),
        "hikari_active_p95": rounded(percentile(series("hikaricp_connections_active"), 95)),
        "hikari_pending_max": max(series("hikaricp_connections_pending"), default=None),
        "hikari_pending_p95": rounded(percentile(series("hikaricp_connections_pending"), 95)),
        "hikari_acquire_avg_ms": rounded(1000 * acquire_sum / acquire_count if acquire_count else None, 3),
        "hikari_usage_avg_ms": rounded(1000 * usage_sum / usage_count if usage_count else None, 3),
        "hikari_queries_per_s": rounded(acquire_count / window.seconds if acquire_count is not None else None, 1),
        "hikari_timeouts": _delta(first, last, "hikaricp_connections_timeout_total"),
        "executor_queued_max": queued,
        "log_errors": logback_last.get("error", 0) - logback_first.get("error", 0),
        "log_warnings": logback_last.get("warn", 0) - logback_first.get("warn", 0),
    }


def probe_metrics(samples: list[dict], window: Window) -> dict:
    by_probe: dict[str, dict] = {}
    for s in _in_window(samples, "probe", window):
        for name, result in s["probes"].items():
            p = by_probe.setdefault(name, {"latencies": [], "errors": 0, "bytes": 0})
            p["latencies"].append(result["latency_ms"])
            p["bytes"] = max(p["bytes"], result["bytes"])
            if result["status"] != 200:
                p["errors"] += 1
    return {
        name: {
            "p50_ms": rounded(percentile(p["latencies"], 50)),
            "p95_ms": rounded(percentile(p["latencies"], 95)),
            "max_ms": rounded(max(p["latencies"])),
            "errors": p["errors"],
            "response_kb": round(p["bytes"] / 1024, 1),
        }
        for name, p in sorted(by_probe.items())
    }


def push_metrics(records: list[dict], window: Window) -> dict:
    in_window = [r for r in records if window.contains(r["ts"])]
    latencies = [r["latency_ms"] for r in in_window]
    errors = [r for r in in_window if r["status"] is None or not 200 <= r["status"] < 300]
    return {
        "heartbeats": len(in_window),
        "heartbeats_per_second": rounded(len(in_window) / window.seconds, 3),
        "p50_ms": rounded(percentile(latencies, 50)),
        "p99_ms": rounded(percentile(latencies, 99)),
        "max_ms": rounded(max(latencies) if latencies else None),
        "errors": len(errors),
    }


def ui_load_metrics(records: list[dict], window: Window) -> dict:
    """Latency of the requests of the emulated UI/API users, per page/endpoint."""
    in_window = [r for r in records if window.contains(r["ts"])]
    by_name: dict[str, list[dict]] = {}
    for r in in_window:
        by_name.setdefault(r["name"], []).append(r)
    return {
        "requests": len(in_window),
        "requests_per_second": rounded(len(in_window) / window.seconds, 2),
        "users": len({r["user"] for r in in_window}),
        "endpoints": {
            name: {
                "requests": len(rs),
                "p50_ms": rounded(percentile([r["latency_ms"] for r in rs], 50)),
                "p95_ms": rounded(percentile([r["latency_ms"] for r in rs], 95)),
                "max_ms": rounded(max(r["latency_ms"] for r in rs)),
                "errors": sum(1 for r in rs if r["status"] != 200),
                "response_kb": round(max(r["bytes"] for r in rs) / 1024, 1),
            }
            for name, rs in sorted(by_name.items())
        },
    }


# e.g. [2026-10-16T11:32:18.946+0000][18.660s][info][gc             ] GC(202) Pause Full (G1 Compaction Pause) 191M->191M(192M) 158.376ms
_GC_PAUSE = re.compile(
    r"^\[([^\]]+)\].*\[gc\s*\] GC\(\d+\) Pause (\w+)(?: \([^)]*\))* (\d+)M->(\d+)M\((\d+)M\) ([\d.]+)ms"
)


def parse_gc_log(lines) -> list[dict]:
    """Every stop-the-world pause of the unified GC log (-Xlog:gc*), with the heap before and after it."""
    pauses = []
    for line in lines:
        match = _GC_PAUSE.match(line)
        if not match:
            continue
        ts, kind, before, after, capacity, millis = match.groups()
        pauses.append({
            "ts": datetime.strptime(ts, "%Y-%m-%dT%H:%M:%S.%f%z").timestamp(),
            "kind": kind,
            "before_mb": int(before),
            "after_mb": int(after),
            "capacity_mb": int(capacity),
            "ms": float(millis),
        })
    return pauses


def gc_log_metrics(gc_dir: Path, window: Window) -> dict:
    """What the GC log tells about the window. Unlike the metric samples (every 10s), it has every single collection,
    so it also shows short spikes of the heap, e.g. the ones caused by big UI/API responses."""
    lines = []
    for path in sorted(gc_dir.glob("gc.log*")):
        lines += path.read_text(errors="replace").splitlines()
    pauses = [p for p in parse_gc_log(lines) if window.contains(p["ts"])]
    if not pauses:
        return {}
    full = [p for p in pauses if p["kind"] == "Full"]
    return {
        "pauses": len(pauses),
        "full_gcs": len(full),
        "pause_max_ms": rounded(max(p["ms"] for p in pauses), 1),
        "pause_total_ms": rounded(sum(p["ms"] for p in pauses), 1),
        "heap_before_gc_max_mb": max(p["before_mb"] for p in pauses),
        "heap_after_gc_max_mb": max(p["after_mb"] for p in pauses),
        "heap_after_full_gc_max_mb": max((p["after_mb"] for p in full), default=None),
        "heap_capacity_max_mb": max(p["capacity_mb"] for p in pauses),
        "overhead_limit_exceeded": sum(1 for line in lines if "GC Overhead Limit exceeded" in line),
    }


def collector_errors(samples: list[dict], window: Window) -> int:
    return len(_in_window(samples, "collector_error", window))


# --- database -----------------------------------------------------------------------------------------------------

LOG_AND_EVENT_TABLES = [t for t, _ in schema.CHECK_LOG_TABLES.values()] + list(schema.UPTIME_EVENT_TABLES.values())


def db_snapshot(conn) -> dict:
    row = conn.execute(
        """
        SELECT xact_commit, xact_rollback, tup_inserted, tup_updated, tup_deleted, blks_read, blks_hit, temp_bytes,
               deadlocks
        FROM pg_stat_database WHERE datname = current_database()
        """
    ).fetchone()
    keys = ["xact_commit", "xact_rollback", "tup_inserted", "tup_updated", "tup_deleted", "blks_read", "blks_hit",
            "temp_bytes", "deadlocks"]
    snapshot = dict(zip(keys, row))
    snapshot["database_bytes"] = conn.execute("SELECT pg_database_size(current_database())").fetchone()[0]
    snapshot["tables"] = {
        table: {
            "bytes": conn.execute("SELECT pg_total_relation_size(%s)", (table,)).fetchone()[0],
            "rows": conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0],
        }
        for table in LOG_AND_EVENT_TABLES
    }
    return snapshot


def reset_statement_stats(conn):
    # The library is preloaded by the server (see compose.yml), but the extension has to exist in the database
    conn.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements SCHEMA public")
    conn.execute("SELECT public.pg_stat_statements_reset()")


def top_statements(conn, limit: int = 10) -> list[dict]:
    rows = conn.execute(
        """
        SELECT left(regexp_replace(query, '\\s+', ' ', 'g'), 160), calls, total_exec_time, mean_exec_time, rows
        FROM public.pg_stat_statements
        WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
          AND query NOT ILIKE '%%pg_stat_statements%%'
        ORDER BY total_exec_time DESC LIMIT %s
        """,
        (limit,),
    ).fetchall()
    return [
        {"query": q, "calls": calls, "total_ms": round(total, 1), "mean_ms": round(mean, 3), "rows": n}
        for q, calls, total, mean, n in rows
    ]


def db_metrics(before: dict, after: dict, window: Window, latency_retention_days: int = 7) -> dict:
    seconds = window.seconds
    log_tables = [t for t, _ in schema.CHECK_LOG_TABLES.values()]
    rows_per_s = sum(after["tables"][t]["rows"] - before["tables"][t]["rows"] for t in log_tables) / seconds
    log_bytes = sum(after["tables"][t]["bytes"] for t in log_tables)
    log_rows = sum(after["tables"][t]["rows"] for t in log_tables)
    bytes_per_row = log_bytes / log_rows if log_rows >= MIN_ROWS_FOR_SIZE_PROJECTION else None
    blks = (after["blks_hit"] - before["blks_hit"]) + (after["blks_read"] - before["blks_read"])
    return {
        "transactions_per_s": rounded((after["xact_commit"] - before["xact_commit"]) / seconds, 1),
        "rollbacks": after["xact_rollback"] - before["xact_rollback"],
        "inserts_per_s": rounded((after["tup_inserted"] - before["tup_inserted"]) / seconds, 1),
        "updates_per_s": rounded((after["tup_updated"] - before["tup_updated"]) / seconds, 1),
        "deletes_per_s": rounded((after["tup_deleted"] - before["tup_deleted"]) / seconds, 1),
        "cache_hit_ratio": rounded((after["blks_hit"] - before["blks_hit"]) / blks if blks else None, 4),
        "deadlocks": after["deadlocks"] - before["deadlocks"],
        "database_mb": mb(after["database_bytes"]),
        "check_log_rows_per_s": rounded(rows_per_s, 2),
        "check_log_bytes_per_row": rounded(bytes_per_row, 1),
        # What the per-check rows would take after the default 7 days of latency/metrics retention, at this rate
        "check_log_projected_mb": mb(rows_per_s * 86400 * latency_retention_days * bytes_per_row
                                     if bytes_per_row else None),
    }


# --- gate ---------------------------------------------------------------------------------------------------------

def evaluate_gates(metrics: dict, gates: dict) -> list[str]:
    """The reasons why the step failed; empty if it passed."""
    reasons = []
    if metrics.get("kuvasz_crashed"):
        reasons.append(f"Kuvasz died: {metrics['kuvasz_crashed']}")
    for monitor_type, t in metrics.get("timing", {}).items():
        if t["lag_p99_normalized"] is not None and t["lag_p99_normalized"] > gates["lag_p99_normalized"]:
            reasons.append(f"{monitor_type} checks late: p99 lag {t['lag_p99_s']}s")
        if t["missed_ratio"] is not None and t["missed_ratio"] > gates["missed_ratio"]:
            reasons.append(f"{monitor_type} checks missed: {t['missed_ratio']:.1%}")
        if t["starved_monitors"] > 0:
            reasons.append(f"{monitor_type}: {t['starved_monitors']} monitors not checked at all")
    correctness = metrics.get("correctness", {})
    if correctness.get("false_positives", 0) > gates["false_positives"]:
        reasons.append(f"{correctness['false_positives']} false DOWN")
    if correctness.get("false_negatives", 0) > gates["false_negatives"]:
        reasons.append(f"{correctness['false_negatives']} undetected DOWN")
    push = metrics.get("push", {})
    if push.get("p99_ms") is not None and push["p99_ms"] > gates["push_p99_ms"]:
        reasons.append(f"heartbeat p99 {push['p99_ms']}ms")
    if push.get("errors", 0) > gates["push_errors"]:
        reasons.append(f"{push['errors']} failed heartbeats")
    for name, p in metrics.get("probes", {}).items():
        if p["p95_ms"] is not None and p["p95_ms"] > gates["probe_p95_ms"]:
            reasons.append(f"{name} p95 {p['p95_ms']}ms")
        if p["max_ms"] is not None and p["max_ms"] > gates["probe_max_ms"]:
            reasons.append(f"{name} max {p['max_ms']}ms")
        if p["errors"] > 0:
            reasons.append(f"{name}: {p['errors']} failed requests")
    for name, p in metrics.get("ui_load", {}).get("endpoints", {}).items():
        if p["p95_ms"] is not None and p["p95_ms"] > gates["probe_p95_ms"]:
            reasons.append(f"UI load: {name} p95 {p['p95_ms']}ms")
        if p["max_ms"] is not None and p["max_ms"] > gates["probe_max_ms"]:
            reasons.append(f"UI load: {name} max {p['max_ms']}ms")
        if p["errors"] > 0:
            reasons.append(f"UI load: {name}: {p['errors']} failed requests")
    if (metrics.get("jvm", {}).get("hikari_timeouts") or 0) > gates["hikari_timeouts"]:
        reasons.append(f"{metrics['jvm']['hikari_timeouts']} DB connection timeouts")
    return reasons


def validity_warnings(metrics: dict, max_target_cpu_percent: float) -> list[str]:
    """Problems of the benchmark itself, rather than of Kuvasz."""
    warnings = []
    for name, cpu in metrics.get("resources", {}).get("target_cpu_percent_max", {}).items():
        if cpu is not None and cpu > max_target_cpu_percent:
            warnings.append(f"target {name} was busy ({cpu}% CPU), it might have been the bottleneck")
    if metrics.get("collector_errors"):
        warnings.append(f"{metrics['collector_errors']} failed samples")
    return warnings


def rounded(value: float | None, digits: int = 2) -> float | None:
    return None if value is None else round(value, digits)


def mb(value: float | None) -> float | None:
    return None if value is None else round(value / 2**20, 1)


def load_jsonl(path) -> list[dict]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
