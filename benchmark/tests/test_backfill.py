import subprocess

import pytest

from kbench import backfill
from kbench.backfill import (FLAP_EPISODE_EVERY_SECONDS, FLAP_EPISODE_SECONDS, NO_OUTAGE_BEFORE_END_SECONDS,
                             OUTAGE_EVERY_SECONDS, OUTAGE_SECONDS, MonitorRow, behavior_of, chunks, events_of, is_down,
                             log_insert_sql)

DAY = 86400


def monitor(behavior: str, interval: int = 60, monitor_id: int = 1) -> MonitorRow:
    return MonitorRow("http", monitor_id, interval, behavior, True)


def status_at(events, t: int) -> str:
    return next(status for start, end, status in events if start <= t and (end is None or t < end))


def test_a_flapping_monitor_flaps_until_now_like_the_stub():
    # 60s interval -> 480s phases, UP while floor(epoch / 480) is even
    end = 100 * 480
    events = events_of(monitor("flapping"), start=end - 1500, end=end)
    assert events[-1] == (end - 480, None, "DOWN")
    assert events[-2] == (end - 960, end - 480, "UP")


def test_a_flapping_monitor_flaps_only_in_episodes():
    row = monitor("flapping")
    end = 30 * DAY
    events = events_of(row, 0, end)
    down = [(s, e) for s, e, status in events if status == "DOWN"]
    # Twice a week for 2 hours, half of the time DOWN in 480s phases, plus the episode that lasts until now
    episodes = 30 * DAY // FLAP_EPISODE_EVERY_SECONDS + 1
    assert episodes * (FLAP_EPISODE_SECONDS // 960 - 1) <= len(down) <= (episodes + 1) * (FLAP_EPISODE_SECONDS // 960 + 1)
    # UP for days between two episodes
    assert max((e or end) - s for s, e, status in events if status == "UP") > 3 * DAY


def test_healthy_monitors_have_a_short_outage_every_two_weeks():
    end = 30 * DAY
    events = events_of(monitor("healthy", monitor_id=3), 0, end)
    down = [(s, e) for s, e, status in events if status == "DOWN"]
    assert len(down) in (2, 3)
    assert all(e - s <= OUTAGE_SECONDS for s, e in down)
    assert all(e < end - NO_OUTAGE_BEFORE_END_SECONDS for _, e in down)
    assert events[-1][1:] == (None, "UP")
    assert down[1][0] - down[0][0] == OUTAGE_EVERY_SECONDS


def test_events_match_the_status_at_every_second():
    end = 20 * DAY + 1234
    for row in (monitor("flapping", 10, 7), monitor("flapping", 300, 8), monitor("slow", 60, 9),
                monitor("healthy", 60, 10)):
        events = events_of(row, 1000, end)
        assert all(a[1] == b[0] and a[2] != b[2] for a, b in zip(events, events[1:]))
        for t in range(1000, end, 37):
            assert status_at(events, t) == ("DOWN" if is_down(row, t, end) else "UP"), (row, t)


def test_failing_monitors_have_one_open_event():
    assert events_of(monitor("down"), 0, 100 * DAY) == [(0, None, "DOWN")]
    assert events_of(monitor("timeout"), 0, 100 * DAY) == [(0, None, "DOWN")]


def test_unknown_behaviors_are_healthy():
    assert behavior_of({"behavior": "real"}) == "healthy"
    assert behavior_of(None) == "healthy"
    assert behavior_of({"behavior": "flapping"}) == "flapping"


def test_chunks_cover_the_range_without_gaps():
    assert chunks(0, 250, size=100) == [(0, 100), (100, 200), (200, 250)]


def test_only_successful_http_checks_write_a_row():
    assert "WHERE (m.behavior NOT IN ('down', 'timeout')" in log_insert_sql("http", "http_latency_log")
    tcp = log_insert_sql("tcp", "tcp_metrics_log")
    assert "WHERE (m.behavior" not in tcp
    assert "INSERT INTO tcp_metrics_log (monitor_id, created_at, latency_ms)" in tcp


class RecordingStack:
    def __init__(self):
        self.calls = []

    def start_kuvasz(self):
        self.calls.append("start")

    def stop_kuvasz(self):
        self.calls.append("stop")

    def resize_postgres(self, cpus, mem):
        self.calls.append(("resize", cpus, mem))


def docker_with_cpus(cpus: int):
    return lambda *args, **kwargs: subprocess.CompletedProcess(args, 0, stdout=f"{cpus}\n")


def test_load_into_imports_the_monitors_then_loads_with_more_resources(monkeypatch):
    stack = RecordingStack()
    monkeypatch.setattr(backfill.subprocess, "run", docker_with_cpus(8))
    monkeypatch.setattr(backfill, "run", lambda expectations, days, log: stack.calls.append(("load", days)) or {})

    backfill.load_into(stack, {}, 7, {"cpus": 1, "mem": "1g"})

    assert stack.calls == ["start", "stop", ("resize", 6, "3g"), ("load", 7), ("resize", 1, "1g")]


def test_load_into_gives_the_database_its_resources_back_when_the_load_fails(monkeypatch):
    stack = RecordingStack()
    monkeypatch.setattr(backfill.subprocess, "run", docker_with_cpus(4))

    def failing_load(expectations, days, log):
        raise RuntimeError("load failed")

    monkeypatch.setattr(backfill, "run", failing_load)

    with pytest.raises(RuntimeError):
        backfill.load_into(stack, {}, 7, {"cpus": 0.5, "mem": "512m"})

    assert stack.calls[-1] == ("resize", 0.5, "512m")
    assert ("resize", 4, "3g") in stack.calls

