import json
from pathlib import Path

from kbench.report import build_report


def window_metrics(scale: int, lag: float) -> dict:
    return {
        "timing": {
            "http": {"checks_per_second": 0.8 * scale, "lag_p99_s": lag, "missed_ratio": 0.0},
            "tcp": {"checks_per_second": 0.2 * scale, "lag_p99_s": lag / 2, "missed_ratio": 0.02},
        },
        "correctness": {"false_positives": 0, "false_negatives": 0},
        "resources": {"kuvasz_cpu_cores_avg": 0.1 * scale, "kuvasz_cpu_utilization": 0.05 * scale,
                      "kuvasz_throttled_ratio": 0.0, "kuvasz_mem_max_mb": 300.0 + scale,
                      "postgres_cpu_percent_avg": 5.0 * scale},
        "jvm": {"heap_live_after_gc_max_mb": 40.0 + scale, "heap_max_mb": 768.0, "gc_overhead_ratio": 0.001,
                "hikari_pending_max": 0, "hikari_acquire_avg_ms": 0.1},
        "push": {"p99_ms": 12.0},
        "probes": {"health": {"p95_ms": 5.0}, "ui_dashboard": {"p95_ms": 100.0 * scale}},
    }


def step_metrics(scale: int, passed: bool) -> dict:
    return window_metrics(scale, lag=0.5 * scale) | {
        "scale": scale,
        "summary": {"stub_monitors": 100 * scale, "real_slice_monitors": 18, "checks_per_second": 1.5 * scale},
        "startup_seconds": 12.0,
        "db": {"check_log_projected_mb": 10.0 * scale},
        "top_statements": [{"query": "INSERT INTO http_latency_log ...", "calls": 100, "total_ms": 50.0,
                            "mean_ms": 0.5, "rows": 100}],
        "passed": passed,
        "failures": [] if passed else ["ui_dashboard p95 2500.0ms"],
        "warnings": [],
    }


def write_block(campaign: Path, name: str, kind: str, profile: str, steps: list[dict], last_pass, first_fail):
    block_dir = campaign / name
    for m in steps:
        (block_dir / f"n{m['scale']:03d}").mkdir(parents=True)
        (block_dir / f"n{m['scale']:03d}" / "metrics.json").write_text(json.dumps(m))
    block = {"name": name, "kind": kind, "profile": profile, "mix": "balanced", "heap": "ram75",
             "virtual_threads": kind == "storm", "scales": [m["scale"] for m in steps]}
    (block_dir / "block.json").write_text(json.dumps(
        {"block": block, "steps": [], "last_pass": last_pass, "first_fail": first_fail}))


def test_build_report(tmp_path: Path):
    campaign = tmp_path / "20261016-1200"
    campaign.mkdir()
    (campaign / "environment.json").write_text(json.dumps(
        {"kuvasz_tag": "4.5.0", "docker_cpus": 8, "docker_memory_gb": 11.9, "docker_os": "Docker Desktop",
         "host": "Apple M1 Pro"}))
    write_block(campaign, "ramp-S", "ramp", "S", [step_metrics(1, True), step_metrics(5, True),
                                                  step_metrics(15, False), step_metrics(10, True)], 10, 15)
    crashed = {"scale": 40, "summary": {"stub_monitors": 4000, "real_slice_monitors": 18, "checks_per_second": 60},
               "kuvasz_crashed": "exit code 137, OOM killed: True", "passed": False, "failures": ["Kuvasz died"]}
    write_block(campaign, "ramp-M", "ramp", "M", [step_metrics(15, True), crashed], 15, 40)
    storm = step_metrics(15, True) | {phase: window_metrics(15, lag) for phase, lag in
                                      (("steady", 1.0), ("outage", 4.0), ("recovery", 1.5))}
    storm["recovery_of_group_b"] = {"recovered": 300, "never_down": 50, "not_recovered": 10,
                                    "recovery_p50_s": 30.0, "recovery_p95_s": 70.0, "recovery_max_s": 90.0}
    write_block(campaign, "storm-M", "storm", "M", [storm], 15, None)
    uiload = step_metrics(15, True) | {
        "gc": {"full_gcs": 12, "pause_max_ms": 585.7, "heap_after_gc_max_mb": 150, "heap_capacity_max_mb": 185},
        "ui_load": {"users": 5, "requests_per_second": 3.2, "endpoints": {
            "ui_http_monitors_list": {"requests": 50, "p50_ms": 120.0, "p95_ms": 300.0, "max_ms": 450.0,
                                      "errors": 0, "response_kb": 900.0}}}}
    write_block(campaign, "uiload-XS-stock", "uiload", "XS", [uiload], 15, None)

    report = build_report(campaign)

    assert "# Kuvasz capacity benchmark: 20261016-1200" in report
    assert "`kuvaszmonitoring/kuvasz:4.5.0`" in report
    assert "| ramp-S | profile **S**" in report
    assert "N=10 (1,018 monitors) | N=15 (1,518 monitors) | ui_dashboard p95 2500.0ms |" in report
    assert "❌ exit code 137, OOM killed: True" in report
    assert "| outage | 4.0s (http)" in report
    assert "300 monitors back UP" in report
    assert "### ramp-S at N=10" in report
    assert "## UI load" in report
    assert "12 full collections (longest pause 586ms)" in report
    assert "| ui_http_monitors_list | 50 | 120ms | 300ms | 450ms | 0 | 900 KB |" in report
    for chart in ("cpu", "heap", "rss", "lag", "probe", "postgres"):
        assert (campaign / "charts" / f"{chart}.png").stat().st_size > 0
        assert f"(charts/{chart}.png)" in report


def test_crashed_step_row_has_every_column():
    from kbench.report import STEP_COLUMNS, STEP_HEADER, step_row
    crashed = {"scale": 40, "summary": {"stub_monitors": 4000, "real_slice_monitors": 18, "checks_per_second": 60},
               "kuvasz_crashed": "exit code 137, OOM killed: True"}
    cells = step_row(crashed).strip().strip("|").split("|")
    header_cells = STEP_HEADER.splitlines()[0].strip().strip("|").split("|")
    assert len(cells) == len(header_cells) == STEP_COLUMNS
    assert len(step_row(step_metrics(5, True)).strip().strip("|").split("|")) == STEP_COLUMNS


def test_chart_series_cover_every_ramp_with_unique_labels():
    from kbench.report import SERIES_COLORS, chart_series
    names = ["ramp-L", "ramp-M", "ramp-S", "ramp-S-ram60", "ramp-XS", "ramp-XS-stock"]
    ramps = [{"block": {"name": name}} for name in names]
    series = chart_series(ramps)
    assert [label for label, _, _ in series] == ["L", "M", "S", "S-ram60", "XS", "XS-stock"]
    assert [color for _, color, _ in series] == SERIES_COLORS[:6]
