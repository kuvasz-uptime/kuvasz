import json

from kbench.collect import parse_cgroup, parse_docker_stats, parse_prometheus, parse_size

PROMETHEUS_TEXT = """\
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1.0E7
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 2.0E7
jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 5.0E7
jvm_memory_max_bytes{area="heap",id="G1 Eden Space"} -1.0
jvm_memory_max_bytes{area="heap",id="G1 Old Gen"} 8.0E8
jvm_gc_pause_seconds_sum{action="end of minor GC",cause="G1 Evacuation Pause",gc="G1 Young Generation"} 0.5
jvm_gc_pause_seconds_sum{action="end of major GC",cause="G1 Compaction Pause",gc="G1 Old Generation"} 0.25
hikaricp_connections_pending{pool="default"} 3.0
executor_queued_tasks{name="scheduled"} 7.0
executor_queued_tasks{name="io"} 1.0
logback_events_total{level="error"} 2.0
process_cpu_usage NaN
some_other_metric 42.0
"""


def test_parse_prometheus():
    metrics = parse_prometheus(PROMETHEUS_TEXT)
    assert metrics["jvm_memory_used_bytes"] == {"heap": 3.0e7, "nonheap": 5.0e7}
    # -1 means "undefined", it must not pull the sum down
    assert metrics["jvm_memory_max_bytes"] == {"heap": 8.0e8}
    assert metrics["jvm_gc_pause_seconds_sum"] == 0.75
    assert metrics["hikaricp_connections_pending"] == 3.0
    assert metrics["executor_queued_tasks"] == {"scheduled": 7.0, "io": 1.0}
    assert metrics["logback_events_total"] == {"error": 2.0}
    assert "process_cpu_usage" not in metrics
    assert "some_other_metric" not in metrics


def test_parse_size():
    assert parse_size("485.1MiB") == 485.1 * 2**20
    assert parse_size(" 1GiB") == 2**30
    assert parse_size("12kB") == 12_000
    assert parse_size("0B") == 0
    assert parse_size("garbage") == 0


def test_parse_docker_stats():
    lines = "\n".join(json.dumps(row) for row in [
        {"Name": "kb-kuvasz", "CPUPerc": "40.86%", "MemUsage": "485.1MiB / 1GiB"},
        {"Name": "kb-postgres", "CPUPerc": "7.46%", "MemUsage": "83.19MiB / 1GiB"},
    ]) + "\n"
    stats = parse_docker_stats(lines)
    assert stats["kb-kuvasz"] == {"cpu_percent": 40.86, "mem_bytes": 485.1 * 2**20, "mem_limit_bytes": 2**30}
    assert stats["kb-postgres"]["cpu_percent"] == 7.46


def test_parse_cgroup():
    text = """\
usage_usec 123456789
user_usec 100000000
system_usec 23456789
nr_periods 5000
nr_throttled 250
throttled_usec 9876543
536870912
anon 314572800
file 104857600
kernel 1000
"""
    assert parse_cgroup(text) == {
        "usage_usec": 123456789,
        "nr_periods": 5000,
        "nr_throttled": 250,
        "throttled_usec": 9876543,
        "memory_current": 536870912,
        "anon": 314572800,
        "file": 104857600,
    }


def test_parse_prometheus_keeps_memory_pools_buffers_and_netty():
    text = """\
jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 1.0E8
jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'profiled nmethods'"} 3.0E7
jvm_memory_committed_bytes{area="nonheap",id="Metaspace"} 1.1E8
jvm_buffer_memory_used_bytes{id="direct"} 4194304.0
jvm_buffer_memory_used_bytes{id="mapped"} 0.0
netty_allocator_memory_used{allocator_type="PooledByteBufAllocator",memory_type="direct"} 1.6777216E7
"""
    metrics = parse_prometheus(text)
    assert metrics["jvm_memory_used_by_pool"] == {"Metaspace": 1.0e8, "CodeHeap 'profiled nmethods'": 3.0e7}
    assert metrics["jvm_memory_committed_by_pool"] == {"Metaspace": 1.1e8}
    assert metrics["jvm_memory_used_bytes"] == {"nonheap": 1.3e8}
    assert metrics["jvm_buffer_memory_used_bytes"] == {"direct": 4194304.0, "mapped": 0.0}
    assert metrics["netty"] == {
        'netty_allocator_memory_used{allocator_type="PooledByteBufAllocator",memory_type="direct"}': 1.6777216e7}
