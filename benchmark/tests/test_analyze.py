import pytest

from kbench import analyze as a
from kbench.analyze import Window
from kbench.generate import EXPECTED_STATUS

GATES = {
    "lag_p99_normalized": 1.0,
    "missed_ratio": 0.01,
    "false_positives": 0,
    "false_negatives": 0,
    "push_p99_ms": 1000,
    "push_errors": 0,
    "probe_p95_ms": 2000,
    "probe_max_ms": 10000,
    "hikari_timeouts": 0,
}
WINDOW = Window(step_start=1000.0, start=1200.0, end=1500.0)


def exp(monitor_type="http", behavior="healthy", interval=60, threshold=1, group="a"):
    return {"type": monitor_type, "behavior": behavior, "expected": EXPECTED_STATUS.get(behavior),
            "interval": interval, "threshold": threshold, "group": group}


def test_percentile():
    assert a.percentile([], 50) is None
    assert a.percentile([3, 1, 2], 50) == 2
    assert a.percentile(list(range(101)), 99) == 99
    assert a.percentile([5], 99) == 5


def test_allowed_lag_and_intrinsic_duration():
    assert a.allowed_lag(30) == 5
    assert a.allowed_lag(300) == 30
    assert a.intrinsic_duration("http", 250) == 0.25
    assert a.intrinsic_duration("tcp", None) == 0
    # a ping check sends 3 packets a second apart
    assert a.intrinsic_duration("icmp", 1) == pytest.approx(2.001)


def test_timing_metrics():
    expectations = {
        "http-on-time": exp(interval=60),
        "http-late": exp(interval=60),
        "http-down": exp(behavior="down"),
        "tcp-starved": exp("tcp", interval=30),
        "docker-unlogged": exp("docker", interval=30),
        "real-x": exp(behavior="real"),
    }
    gaps = [
        ("http", "http-on-time", 60.1, 100.0),  # lag 0
        ("http", "http-on-time", 60.2, 200.0),  # lag 0
        ("http", "http-late", 72.0, 0.0),  # lag 12 > 5
        ("http", "http-late", 125.0, 0.0),  # lag 65: a whole interval missed
        ("http", "http-down", 500.0, 0.0),  # failing monitors are not measured
        ("http", "real-x", 500.0, 0.0),
    ]
    result = a.timing_metrics(gaps, expectations, {"http": 30, "tcp": 0}, WINDOW)
    http = result["http"]
    assert http["measured_gaps"] == 4
    assert http["checks"] == 30
    assert http["checks_per_second"] == 0.1
    assert http["lag_p50_s"] == pytest.approx(0.0, abs=0.01)
    assert http["lag_max_s"] == 65.0
    assert http["missed"] == 1
    assert http["missed_ratio"] == 0.25
    assert http["late_ratio"] == 0.5
    assert http["lag_p99_normalized"] == round(65 / 6, 2)  # allowed lag of a 60s interval: 6s
    assert result["tcp"]["starved_monitors"] == 1
    assert result["tcp"]["lag_p99_s"] is None
    # docker monitors write no per-check rows without their metrics history, so they are never "starved"
    assert result["docker"]["starved_monitors"] == 0


def test_correctness_metrics():
    expectations = {
        "up-ok": exp(),
        "up-flipped": exp(),
        "down-ok": exp(behavior="down"),
        "down-missed": exp(behavior="down"),
        "down-not-due": exp(behavior="down", interval=3600),
        "flapping": exp(behavior="flapping"),
        "excluded": exp(),
        "real-x": exp(behavior="real"),
    }
    # (status, started, ended, updated); the window's Kuvasz process started at 1000
    events = {
        "up-ok": [("UP", 900.0, None, 1400.0)],
        "up-flipped": [("UP", 900.0, 1300.0, 1300.0), ("DOWN", 1300.0, 1320.0, 1320.0), ("UP", 1320.0, None, 1450.0)],
        "down-ok": [("UP", 900.0, 1100.0, 1100.0), ("DOWN", 1100.0, None, 1450.0)],
        "down-missed": [("UP", 900.0, None, 1450.0)],
        "flapping": [("DOWN", 1250.0, None, 1450.0)],
        "excluded": [("DOWN", 1250.0, None, 1450.0)],
        "real-x": [("DOWN", 1250.0, None, 1450.0)],
    }
    result = a.correctness_metrics(events, expectations, WINDOW, {}, excluded={"excluded"})
    assert result["false_positives"] == 1
    assert result["false_positive_examples"] == ["up-flipped"]
    assert result["false_negatives"] == 1
    assert result["false_negative_examples"] == ["down-missed"]


def test_correctness_ignores_what_the_previous_step_left_behind():
    expectations = {
        "inherited-down-closed": exp(),
        "inherited-down-confirmed": exp(),
        "inherited-down-not-checked-yet": exp(interval=300),
        "failing-not-checked-by-this-process": exp(behavior="down"),
        "failing-confirmed-by-this-process": exp(behavior="down"),
    }
    events = {
        # a false DOWN of the previous step, closed by this process: not this step's mistake
        "inherited-down-closed": [("DOWN", 800.0, 1250.0, 1250.0), ("UP", 1250.0, None, 1400.0)],
        # ...but if this process found it DOWN again, it is
        "inherited-down-confirmed": [("DOWN", 800.0, None, 1300.0)],
        "inherited-down-not-checked-yet": [("DOWN", 800.0, None, 900.0)],
        # a DOWN status inherited from the previous step doesn't prove that this process detected anything
        "failing-not-checked-by-this-process": [("DOWN", 800.0, None, 900.0)],
        "failing-confirmed-by-this-process": [("DOWN", 800.0, None, 1450.0)],
    }
    result = a.correctness_metrics(events, expectations, WINDOW, {})
    assert result["false_positive_examples"] == ["inherited-down-confirmed"]
    assert result["false_negative_examples"] == ["failing-not-checked-by-this-process"]


def test_correctness_counts_the_deadline_from_when_the_checks_started():
    expectations = {"failing": exp(behavior="down", interval=60, threshold=2)}  # due 334s after the checks started
    events = {"failing": [("UP", 1100.0, None, 1450.0)]}
    started_early = Window(step_start=1000.0, start=1200.0, end=1500.0, checks_start=1100.0)
    started_late = Window(step_start=1000.0, start=1200.0, end=1500.0, checks_start=1300.0)
    assert a.correctness_metrics(events, expectations, started_early, {})["false_negatives"] == 1
    assert a.correctness_metrics(events, expectations, started_late, {})["false_negatives"] == 0


def test_window_checks_started():
    assert Window(1000.0, 1200.0, 1500.0).checks_started == 1000.0
    assert Window(1000.0, 1200.0, 1500.0, checks_start=1030.0).checks_started == 1030.0


def test_due_for_down():
    assert a.due_for_down(exp(behavior="down", interval=60, threshold=2), 0) == (60 + 92) * 2 + 30
    assert a.due_for_down(exp("dns", behavior="timeout", interval=60), 0) == 95
    # the first heartbeat (spread over 30s) + 2 missed intervals + grace + the period of the heartbeat check + margin
    assert a.due_for_down(exp("push", behavior="down", interval=300, threshold=2), 56) == 30 + 600 + 56 + 5 + 30


def test_recovery_metrics():
    expectations = {name: exp(group="b") for name in ("fast", "slow", "never-down", "stuck", "no-events")}
    expectations["failing"] = exp(behavior="down", group="b")
    events = {
        "fast": [("DOWN", 100.0, 205.0, 205.0), ("UP", 205.0, None, 290.0)],
        "slow": [("DOWN", 100.0, 260.0, 260.0), ("UP", 260.0, None, 290.0)],
        "never-down": [("UP", 0.0, None, 290.0)],
        "stuck": [("DOWN", 100.0, None, 290.0)],
    }
    result = a.recovery_metrics(events, expectations, set(expectations), reconnected_at=200.0)
    assert result["recovered"] == 2
    assert result["never_down"] == 1
    assert result["not_recovered"] == 2
    assert result["not_recovered_examples"] == ["no-events", "stuck"]
    assert result["recovery_max_s"] == 60.0


def docker_sample(ts, kuvasz_cpu, kuvasz_mem, pg_cpu=10.0, target_cpu=5.0):
    return {"ts": ts, "kind": "docker", "containers": {
        "kb-kuvasz": {"cpu_percent": kuvasz_cpu, "mem_bytes": kuvasz_mem, "mem_limit_bytes": 2**30},
        "kb-postgres": {"cpu_percent": pg_cpu, "mem_bytes": 2**27, "mem_limit_bytes": 2**30},
        "kb-openresty-a": {"cpu_percent": target_cpu, "mem_bytes": 1, "mem_limit_bytes": 1},
    }}


def test_resource_metrics():
    samples = [
        docker_sample(1100, 99.0, 9 * 2**30),  # before the window
        docker_sample(1210, 40.0, 300 * 2**20),
        docker_sample(1300, 60.0, 400 * 2**20, target_cpu=90.0),
        {"ts": 1210, "kind": "cgroup", "containers": {"kb-kuvasz": {
            "usage_usec": 0, "nr_periods": 100, "nr_throttled": 0, "anon": 200 * 2**20}}},
        {"ts": 1310, "kind": "cgroup", "containers": {"kb-kuvasz": {
            "usage_usec": 50_000_000, "nr_periods": 1100, "nr_throttled": 100, "anon": 250 * 2**20}}},
    ]
    result = a.resource_metrics(samples, WINDOW, kuvasz_cpus=1.0)
    assert result["kuvasz_cpu_cores_avg"] == 0.5
    assert result["kuvasz_cpu_utilization"] == 0.5
    assert result["kuvasz_throttled_ratio"] == 0.1
    assert result["kuvasz_mem_max_mb"] == 400.0
    assert result["kuvasz_anon_mem_max_mb"] == 250.0
    assert result["postgres_cpu_percent_avg"] == 10.0
    assert result["target_cpu_percent_max"] == {"kb-openresty-a": 90.0}


def prometheus_sample(ts, **metrics):
    return {"ts": ts, "kind": "prometheus", "metrics": metrics}


def test_jvm_metrics():
    base = {
        "jvm_memory_used_bytes": {"heap": 100 * 2**20, "nonheap": 80 * 2**20},
        "jvm_memory_max_bytes": {"heap": 768 * 2**20},
        "jvm_gc_live_data_size_bytes": 50 * 2**20,
        "jvm_threads_live_threads": 90,
        "hikaricp_connections_active": 2,
        "hikaricp_connections_pending": 0,
        "executor_queued_tasks": {"scheduled": 1},
        "logback_events_total": {"error": 1, "warn": 5},
    }
    samples = [
        prometheus_sample(1200, **base, jvm_gc_pause_seconds_sum=1.0, jvm_gc_pause_seconds_count=10,
                          hikaricp_connections_acquire_seconds_sum=0.5, hikaricp_connections_acquire_seconds_count=100,
                          hikaricp_connections_timeout_total=0, jvm_gc_memory_allocated_bytes_total=0),
        prometheus_sample(1500, **(base | {"jvm_gc_live_data_size_bytes": 70 * 2**20,
                                           "hikaricp_connections_pending": 4,
                                           "executor_queued_tasks": {"scheduled": 7},
                                           "logback_events_total": {"error": 3, "warn": 5}}),
                          jvm_gc_pause_seconds_sum=4.0, jvm_gc_pause_seconds_count=40,
                          hikaricp_connections_acquire_seconds_sum=1.5, hikaricp_connections_acquire_seconds_count=600,
                          hikaricp_connections_timeout_total=2, jvm_gc_memory_allocated_bytes_total=300 * 2**20),
    ]
    result = a.jvm_metrics(samples, WINDOW)
    assert result["heap_used_max_mb"] == 100.0
    assert result["heap_max_mb"] == 768.0
    assert result["heap_live_after_gc_max_mb"] == 70.0
    assert result["gc_overhead_ratio"] == 0.01
    assert result["gc_pauses"] == 30
    assert result["allocation_mb_per_s"] == 1.0
    assert result["hikari_pending_max"] == 4
    assert result["hikari_acquire_avg_ms"] == 2.0
    assert result["hikari_queries_per_s"] == pytest.approx(1.7, abs=0.1)
    assert result["hikari_timeouts"] == 2
    assert result["executor_queued_max"] == {"scheduled": 7}
    assert result["log_errors"] == 2
    assert result["log_warnings"] == 0
    assert a.jvm_metrics([], WINDOW) == {}


def test_probe_and_push_metrics():
    samples = [
        {"ts": 1210 + i, "kind": "probe", "probes": {
            "health": {"latency_ms": 10.0 + i, "status": 200, "bytes": 20, "error": None},
            "ui_dashboard": {"latency_ms": 100.0, "status": 500 if i == 0 else 200, "bytes": 4096, "error": None},
        }}
        for i in range(10)
    ]
    probes = a.probe_metrics(samples, WINDOW)
    assert probes["health"]["p50_ms"] in (14.0, 15.0)
    assert probes["health"]["max_ms"] == 19.0
    assert probes["ui_dashboard"]["errors"] == 1
    assert probes["ui_dashboard"]["response_kb"] == 4.0

    records = [{"ts": 1250, "latency_ms": float(i), "status": 200, "error": None, "name": "p"} for i in range(100)]
    records += [{"ts": 1250, "latency_ms": 5000.0, "status": None, "error": "ConnectError", "name": "p"},
                {"ts": 900, "latency_ms": 1.0, "status": 503, "error": None, "name": "p"}]
    push = a.push_metrics(records, WINDOW)
    assert push["heartbeats"] == 101
    assert push["errors"] == 1
    assert push["max_ms"] == 5000.0


def snapshot(rows: int, bytes_: int, commits: int):
    tables = {t: {"rows": 0, "bytes": 0} for t in a.LOG_AND_EVENT_TABLES}
    tables["http_latency_log"] = {"rows": rows, "bytes": bytes_}
    return {"xact_commit": commits, "xact_rollback": 0, "tup_inserted": rows, "tup_updated": 0, "tup_deleted": 0,
            "blks_read": 10, "blks_hit": 990, "temp_bytes": 0, "deadlocks": 0, "database_bytes": 50 * 2**20,
            "tables": tables}


def test_db_metrics():
    before, after = snapshot(100_000, 10_000_000, 0), snapshot(100_300, 10_030_000, 3000)
    after["blks_hit"], after["blks_read"] = 1890, 20
    result = a.db_metrics(before, after, WINDOW)
    assert result["transactions_per_s"] == 10.0
    assert result["check_log_rows_per_s"] == 1.0
    assert result["check_log_bytes_per_row"] == 100.0
    assert result["cache_hit_ratio"] == pytest.approx(900 / 910, abs=1e-4)
    # 1 row/s for 7 days, 100 bytes each
    assert result["check_log_projected_mb"] == round(86400 * 7 * 100 / 2**20, 1)


def test_db_metrics_does_not_project_from_tiny_tables():
    result = a.db_metrics(snapshot(100, 81920, 0), snapshot(400, 163840, 10), WINDOW)
    assert result["check_log_bytes_per_row"] is None
    assert result["check_log_projected_mb"] is None


def passing_metrics() -> dict:
    return {
        "timing": {"http": {"lag_p99_normalized": 0.5, "lag_p99_s": 2.5, "missed_ratio": 0.0, "starved_monitors": 0}},
        "correctness": {"false_positives": 0, "false_negatives": 0},
        "push": {"p99_ms": 20.0, "errors": 0},
        "probes": {"health": {"p95_ms": 10.0, "max_ms": 20.0, "errors": 0}},
        "jvm": {"hikari_timeouts": 0},
        "resources": {"target_cpu_percent_max": {"kb-openresty-a": 10.0}},
    }


def test_evaluate_gates_passes():
    assert a.evaluate_gates(passing_metrics(), GATES) == []


def test_evaluate_gates_reports_every_failure():
    m = passing_metrics()
    m["timing"]["http"] |= {"lag_p99_normalized": 3.0, "lag_p99_s": 15.0, "missed_ratio": 0.05,
                            "starved_monitors": 2}
    m["correctness"] = {"false_positives": 3, "false_negatives": 1}
    m["push"] = {"p99_ms": 1500.0, "errors": 2}
    m["probes"]["health"] = {"p95_ms": 2500.0, "max_ms": 12000.0, "errors": 1}
    m["jvm"]["hikari_timeouts"] = 4
    m["kuvasz_crashed"] = "exit code 137, OOM killed: True"
    reasons = a.evaluate_gates(m, GATES)
    assert len(reasons) == 12
    assert reasons[0].startswith("Kuvasz died")
    assert "http checks late: p99 lag 15.0s" in reasons


def test_validity_warnings():
    m = passing_metrics()
    m["resources"]["target_cpu_percent_max"]["kb-coredns-a"] = 95.0
    m["collector_errors"] = 2
    warnings = a.validity_warnings(m, 80)
    assert len(warnings) == 2
    assert "kb-coredns-a" in warnings[0]


def test_load_jsonl(tmp_path):
    assert a.load_jsonl(tmp_path / "missing.jsonl") == []
    (tmp_path / "x.jsonl").write_text('{"a": 1}\n\n{"a": 2}\n')
    assert a.load_jsonl(tmp_path / "x.jsonl") == [{"a": 1}, {"a": 2}]


def test_ui_load_metrics():
    records = [{"ts": 1250, "user": i % 3, "name": "ui_http_monitors_list", "latency_ms": float(10 + i),
                "status": 200, "bytes": 2048, "error": None} for i in range(20)]
    records += [{"ts": 1260, "user": 0, "name": "api_http_monitors", "latency_ms": 900.0, "status": 500,
                 "bytes": 10, "error": None},
                {"ts": 100, "user": 0, "name": "api_http_monitors", "latency_ms": 1.0, "status": 200,
                 "bytes": 10, "error": None}]
    result = a.ui_load_metrics(records, WINDOW)
    assert result["requests"] == 21
    assert result["users"] == 3
    assert result["requests_per_second"] == 0.07
    assert result["endpoints"]["ui_http_monitors_list"] == {
        "requests": 20, "p50_ms": 19.0, "p95_ms": 28.0, "max_ms": 29.0, "errors": 0, "response_kb": 2.0}
    assert result["endpoints"]["api_http_monitors"]["errors"] == 1


GC_LOG = """\
[2026-10-16T12:00:00.000+0000][1.000s][info][gc,start    ] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
[2026-10-16T12:00:00.010+0000][1.010s][info][gc          ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 100M->40M(192M) 10.500ms
[2026-10-16T12:00:05.000+0000][6.000s][info][gc          ] GC(2) Pause Young (Allocation Failure) 120M->90M(185M) 4.000ms
[2026-10-16T12:00:06.000+0000][7.000s][info][gc          ] GC(3) Pause Full (Allocation Failure) 180M->137M(185M) 585.672ms
[2026-10-16T12:00:07.000+0000][8.000s][info][gc          ] GC(4) Pause Remark 140M->140M(192M) 1.200ms
[2026-10-16T12:00:07.500+0000][8.500s][info][gc          ] GC(5) Concurrent Mark Cycle 40.123ms
[2026-10-16T12:00:08.000+0000][9.000s][info][gc          ] GC Overhead Limit exceeded too often (5).
"""


def test_parse_gc_log():
    pauses = a.parse_gc_log(GC_LOG.splitlines())
    assert [p["kind"] for p in pauses] == ["Young", "Young", "Full", "Remark"]
    assert pauses[0] == {"ts": 1792152000.01, "kind": "Young", "before_mb": 100, "after_mb": 40, "capacity_mb": 192,
                         "ms": 10.5}


def test_gc_log_metrics(tmp_path):
    (tmp_path / "gc.log").write_text(GC_LOG)
    window = Window(step_start=1792151000.0, start=1792152004.0, end=1792152100.0)
    result = a.gc_log_metrics(tmp_path, window)
    assert result == {
        "pauses": 3,
        "full_gcs": 1,
        "pause_max_ms": 585.7,
        "pause_total_ms": 590.9,
        "heap_before_gc_max_mb": 180,
        "heap_after_gc_max_mb": 140,
        "heap_after_full_gc_max_mb": 137,
        "heap_capacity_max_mb": 192,
        "overhead_limit_exceeded": 1,
    }
    assert a.gc_log_metrics(tmp_path / "missing", window) == {}


def test_evaluate_gates_checks_the_ui_load():
    m = passing_metrics()
    m["ui_load"] = {"endpoints": {
        "ui_http_monitors_list": {"p95_ms": 2500.0, "max_ms": 11000.0, "errors": 2},
        "ui_dashboard": {"p95_ms": 10.0, "max_ms": 20.0, "errors": 0},
    }}
    assert a.evaluate_gates(m, GATES) == [
        "UI load: ui_http_monitors_list p95 2500.0ms",
        "UI load: ui_http_monitors_list max 11000.0ms",
        "UI load: ui_http_monitors_list: 2 failed requests",
    ]


def test_jvm_metrics_memory_breakdown():
    netty_key = 'netty_alloc_memory_used{alloc="pooled",memory="direct"}'
    samples = [
        prometheus_sample(1300, jvm_memory_used_by_pool={"Metaspace": 90 * 2**20, "CodeHeap": 20 * 2**20},
                          jvm_buffer_memory_used_bytes={"direct": 2 * 2**20}, netty={netty_key: 8 * 2**20}),
        prometheus_sample(1400, jvm_memory_used_by_pool={"Metaspace": 95 * 2**20, "CodeHeap": 35 * 2**20},
                          jvm_buffer_memory_used_bytes={"direct": 1 * 2**20},
                          netty={netty_key: 24 * 2**20,
                                 # a percentage, not memory: must not be counted
                                 'netty_alloc_arena_chunklist_usage_max{memory="direct"}': 100.0}),
    ]
    result = a.jvm_metrics(samples, WINDOW)
    assert result["memory_by_pool_max_mb"] == {"CodeHeap": 35.0, "Metaspace": 95.0}
    assert result["direct_buffers_max_mb"] == 2.0
    assert result["netty_direct_max_mb"] == 24.0
