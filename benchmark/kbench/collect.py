"""Samples the stack while a step runs: container resources, cgroup CPU throttling and memory, JVM/Hikari/executor
metrics from Kuvasz's Prometheus endpoint, and the latency of the UI and the API, as a user would see it."""

import json
import re
import subprocess
import threading
import time
from pathlib import Path

import httpx

from kbench.stack import API_KEY, KUVASZ_CONTAINER, KUVASZ_URL, POSTGRES_CONTAINER

STATS_CONTAINERS = [
    KUVASZ_CONTAINER,
    POSTGRES_CONTAINER,
    "kb-openresty-a",
    "kb-openresty-b",
    "kb-coredns-a",
    "kb-coredns-b",
]
CGROUP_CONTAINERS = [KUVASZ_CONTAINER, POSTGRES_CONTAINER]
ADMIN_CREDENTIALS = {"username": "bench", "password": "bench-password"}

# Prometheus metrics kept from every scrape: name -> label that tells the series apart (None: summed up)
PROMETHEUS_METRICS = {
    "jvm_memory_used_bytes": "area",
    "jvm_memory_committed_bytes": "area",
    "jvm_memory_max_bytes": "area",
    "jvm_gc_live_data_size_bytes": None,
    "jvm_gc_max_data_size_bytes": None,
    "jvm_gc_pause_seconds_sum": None,
    "jvm_gc_pause_seconds_count": None,
    "jvm_gc_memory_allocated_bytes_total": None,
    "jvm_threads_live_threads": None,
    "process_cpu_usage": None,
    "hikaricp_connections_active": None,
    "hikaricp_connections_idle": None,
    "hikaricp_connections_pending": None,
    "hikaricp_connections_timeout_total": None,
    "hikaricp_connections_acquire_seconds_sum": None,
    "hikaricp_connections_acquire_seconds_count": None,
    "hikaricp_connections_usage_seconds_sum": None,
    "hikaricp_connections_usage_seconds_count": None,
    "executor_queued_tasks": "name",
    "executor_active_threads": "name",
    "logback_events_total": "level",
    "jvm_buffer_memory_used_bytes": "id",  # the JDK's direct and mapped buffers
}
# Metrics that are also kept per memory pool (e.g. Metaspace, CodeHeap 'profiled nmethods'), under these names
PER_POOL_METRICS = {
    "jvm_memory_used_bytes": "jvm_memory_used_by_pool",
    "jvm_memory_committed_bytes": "jvm_memory_committed_by_pool",
}
# Netty allocates most of its buffers outside the JDK's direct buffer accounting, so its own metrics are kept too
NETTY_METRIC_PREFIX = "netty_"
_SAMPLE_LINE = re.compile(r"^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+(\S+)")
_LABEL = re.compile(r'(\w+)="([^"]*)"')


def parse_prometheus(text: str) -> dict:
    """Extracts PROMETHEUS_METRICS from a Prometheus text exposition. Series of a metric are summed up, or kept apart
    by the label configured for the metric (e.g. heap vs nonheap)."""
    result: dict = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        match = _SAMPLE_LINE.match(line)
        if not match:
            continue
        name, labels, raw_value = match.groups()
        value = float(raw_value)
        if value != value or value < 0:  # NaN, or -1 for "undefined" (e.g. the max of some memory pools)
            continue
        if name.startswith(NETTY_METRIC_PREFIX):
            result.setdefault("netty", {})[name + (labels or "")] = value
            continue
        if name in PER_POOL_METRICS:
            pool = dict(_LABEL.findall(labels or "")).get("id", "")
            result.setdefault(PER_POOL_METRICS[name], {})[pool] = value
        if name not in PROMETHEUS_METRICS:
            continue
        split_by = PROMETHEUS_METRICS[name]
        if split_by is None:
            result[name] = result.get(name, 0.0) + value
        else:
            key = dict(_LABEL.findall(labels or "")).get(split_by, "")
            series = result.setdefault(name, {})
            series[key] = series.get(key, 0.0) + value
    return result


def parse_size(text: str) -> float:
    """'485.1MiB' -> bytes"""
    match = re.match(r"([\d.]+)\s*([kKMGT]?i?B)", text.strip())
    if not match:
        return 0.0
    units = {"B": 1, "KiB": 2**10, "MiB": 2**20, "GiB": 2**30, "TiB": 2**40, "kB": 1e3, "KB": 1e3, "MB": 1e6,
             "GB": 1e9, "TB": 1e12}
    return float(match.group(1)) * units.get(match.group(2), 1)


def parse_docker_stats(lines: str) -> dict:
    stats = {}
    for line in lines.splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        used, _, limit = row["MemUsage"].partition("/")
        stats[row["Name"]] = {
            "cpu_percent": float(row["CPUPerc"].rstrip("%") or 0),
            "mem_bytes": parse_size(used),
            "mem_limit_bytes": parse_size(limit),
        }
    return stats


def parse_cgroup(text: str) -> dict:
    """The output of `cat cpu.stat memory.current memory.stat` (cgroup v2), flattened into one dict."""
    values = {}
    lines = text.splitlines()
    for line in lines:
        parts = line.split()
        if len(parts) == 2 and parts[1].lstrip("-").isdigit():
            values[parts[0]] = int(parts[1])
        elif len(parts) == 1 and parts[0].isdigit():
            values["memory_current"] = int(parts[0])
    return {
        key: values[key]
        for key in ("usage_usec", "nr_periods", "nr_throttled", "throttled_usec", "memory_current", "anon", "file")
        if key in values
    }


class Collector:
    def __init__(self, out_dir: Path, http_monitor_id: int | None):
        self.out_dir = out_dir
        self.http_monitor_id = http_monitor_id
        self.samples: list[dict] = []
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._threads = [
            threading.Thread(target=self._loop, args=(5, self._sample_docker_stats), daemon=True),
            threading.Thread(target=self._loop, args=(10, self._sample_cgroups), daemon=True),
            threading.Thread(target=self._loop, args=(10, self._sample_prometheus), daemon=True),
            threading.Thread(target=self._loop, args=(15, self._probe), daemon=True),
        ]
        self._ui = httpx.Client(base_url=KUVASZ_URL, timeout=30, follow_redirects=False)
        self._api = httpx.Client(base_url=KUVASZ_URL, timeout=30, headers={"X-API-KEY": API_KEY})

    def start(self):
        for thread in self._threads:
            thread.start()

    def stop(self) -> list[dict]:
        self._stop.set()
        for thread in self._threads:
            thread.join(timeout=60)
        self._ui.close()
        self._api.close()
        with (self.out_dir / "samples.jsonl").open("w") as f:
            for sample in self.samples:
                f.write(json.dumps(sample) + "\n")
        return self.samples

    def _record(self, kind: str, data: dict):
        with self._lock:
            self.samples.append({"ts": time.time(), "kind": kind, **data})

    def _loop(self, period: float, sample):
        while not self._stop.is_set():
            started = time.monotonic()
            try:
                sample()
            except Exception as e:  # a failed sample must not kill the collection
                self._record("collector_error", {"sampler": sample.__name__, "error": repr(e)[:300]})
            self._stop.wait(max(period - (time.monotonic() - started), 0.1))

    def _sample_docker_stats(self):
        result = subprocess.run(
            ["docker", "stats", "--no-stream", "--format", "{{json .}}", *STATS_CONTAINERS],
            capture_output=True, text=True, timeout=30,
        )
        self._record("docker", {"containers": parse_docker_stats(result.stdout)})

    def _sample_cgroups(self):
        cgroups = {}
        for container in CGROUP_CONTAINERS:
            result = subprocess.run(
                ["docker", "exec", container, "cat", "/sys/fs/cgroup/cpu.stat", "/sys/fs/cgroup/memory.current",
                 "/sys/fs/cgroup/memory.stat"],
                capture_output=True, text=True, timeout=30,
            )
            if result.returncode == 0:
                cgroups[container] = parse_cgroup(result.stdout)
        self._record("cgroup", {"containers": cgroups})

    def _sample_prometheus(self):
        response = self._api.get("/api/v2/prometheus")
        response.raise_for_status()
        self._record("prometheus", {"metrics": parse_prometheus(response.text)})

    def _probe(self):
        if not self._ui.cookies:
            self._ui.post("/auth/login", data=ADMIN_CREDENTIALS)
        probes = [
            ("health", self._api, "/api/v2/health"),
            ("api_http_monitors", self._api, "/api/v2/http-monitors"),
            ("ui_dashboard", self._ui, "/"),
            ("ui_dashboard_fragment", self._ui, "/fragments/dashboard"),
            ("ui_http_monitors", self._ui, "/http-monitors"),
        ]
        if self.http_monitor_id is not None:
            probes.append(("ui_http_monitor_details", self._ui, f"/http-monitors/{self.http_monitor_id}"))
        results = {}
        for name, client, path in probes:
            started = time.perf_counter()
            try:
                response = client.get(path)
                status, size, error = response.status_code, len(response.content), None
            except httpx.HTTPError as e:
                status, size, error = None, 0, type(e).__name__
            results[name] = {
                "latency_ms": round((time.perf_counter() - started) * 1000, 1),
                "status": status,
                "bytes": size,
                "error": error,
            }
        if results["ui_dashboard"]["status"] != 200:
            self._ui.cookies.clear()  # logged out (e.g. Kuvasz restarted with a new signing secret)
        self._record("probe", {"probes": results})
