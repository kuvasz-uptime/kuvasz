"""Turns the results of a campaign (results/<name>) into a Markdown report with charts."""

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.ticker import FixedLocator, FuncFormatter, NullFormatter  # noqa: E402

from kbench.run import RESULTS_DIR  # noqa: E402

# The categorical slots of the dataviz reference palette in their fixed order (validated for neighbouring lines), on
# its light surface. A campaign with more ramps than colors charts the first 8 only.
SERIES_COLORS = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"]
# Up to this many series, every line also gets a label at its end (besides the legend)
MAX_DIRECT_LABELS = 4
SURFACE = "#fcfcfb"
TEXT_PRIMARY = "#0b0b0b"
TEXT_SECONDARY = "#52514e"
GRID = "#e4e3df"

CHARTS = [
    ("cpu", "Kuvasz CPU usage (cores)", lambda m: m["resources"]["kuvasz_cpu_cores_avg"]),
    ("heap", "Live heap after GC (MB)", lambda m: m["jvm"].get("heap_live_after_gc_max_mb")),
    ("rss", "Kuvasz container memory (MB)", lambda m: m["resources"]["kuvasz_mem_max_mb"]),
    ("lag", "Worst p99 check lag (s)", lambda m: worst(m, "lag_p99_s")[0]),
    ("probe", "Slowest UI/API request, p95 (ms)", lambda m: worst_probe(m)[1]),
    ("postgres", "Postgres CPU usage (% of a core)", lambda m: m["resources"]["postgres_cpu_percent_avg"]),
]


def load_campaign(campaign_dir: Path) -> list[dict]:
    blocks = []
    for block_file in sorted(campaign_dir.glob("*/block.json")):
        block = json.loads(block_file.read_text())
        steps = []
        for metrics_file in sorted(block_file.parent.glob("n*/metrics.json")):
            steps.append(json.loads(metrics_file.read_text()))
        block["metrics"] = sorted(steps, key=lambda m: m["scale"])
        blocks.append(block)
    order = {"ramp": 0, "knee": 1, "uiload": 2, "storm": 3}
    return sorted(blocks, key=lambda b: (order[b["block"]["kind"]], b["block"]["name"]))


def worst(metrics: dict, key: str) -> tuple[float | None, str | None]:
    values = [(t[key], monitor_type) for monitor_type, t in metrics.get("timing", {}).items() if t[key] is not None]
    return max(values) if values else (None, None)


def worst_probe(metrics: dict) -> tuple[str | None, float | None]:
    probes = [(p["p95_ms"], name) for name, p in metrics.get("probes", {}).items() if p["p95_ms"] is not None]
    if not probes:
        return None, None
    value, name = max(probes)
    return name, value


def fmt(value, digits: int = 1, suffix: str = "") -> str:
    if value is None:
        return "–"
    if isinstance(value, float):
        return f"{value:,.{digits}f}{suffix}"
    return f"{value:,}{suffix}"


def pct(value) -> str:
    return "–" if value is None else f"{value * 100:.1f}%"


def monitors(m: dict) -> int:
    return m["summary"]["stub_monitors"] + m["summary"]["real_slice_monitors"]


def step_row(m: dict) -> str:
    if m.get("kuvasz_crashed"):
        # N, monitors, checks/s, then a dash for every column but the verdict
        dashes = STEP_COLUMNS - 4
        return (f"| {m['scale']} | {fmt(monitors(m))} | {m['summary']['checks_per_second']} | "
                + " | ".join(["–"] * dashes) + f" | ❌ {m['kuvasz_crashed']} |")
    res, jvm, db = m["resources"], m["jvm"], m.get("db", {})
    lag, lag_type = worst(m, "lag_p99_s")
    missed, _ = worst(m, "missed_ratio")
    probe_name, probe_p95 = worst_probe(m)
    correctness = m["correctness"]
    actual_cps = sum(t["checks_per_second"] for t in m["timing"].values())
    verdict = "✅" if m["passed"] else "❌ " + "; ".join(m["failures"])
    if m.get("warnings"):
        verdict += " ⚠️ " + "; ".join(m["warnings"])
    return " | ".join([
        f"| {m['scale']}",
        fmt(monitors(m)),
        f"{m['summary']['checks_per_second']} / {actual_cps:.1f}",
        fmt(m.get("startup_seconds"), 0, "s"),
        f"{fmt(res['kuvasz_cpu_cores_avg'], 2)} ({pct(res['kuvasz_cpu_utilization'])})",
        pct(res["kuvasz_throttled_ratio"]),
        fmt(res["kuvasz_mem_max_mb"], 0),
        f"{fmt(jvm.get('heap_live_after_gc_max_mb'), 0)} / {fmt(jvm.get('heap_max_mb'), 0)}",
        pct(jvm.get("gc_overhead_ratio")),
        fmt(m.get("gc", {}).get("full_gcs"), 0),
        f"{fmt(jvm.get('hikari_pending_max'), 0)} / {fmt(jvm.get('hikari_acquire_avg_ms'), 1)}ms",
        f"{fmt(lag, 1, 's')} ({lag_type or '–'})",
        pct(missed),
        f"{correctness['false_positives']} / {correctness['false_negatives']}",
        fmt(m["push"]["p99_ms"], 0, "ms"),
        f"{fmt(probe_p95, 0, 'ms')} ({probe_name or '–'})",
        fmt(res["postgres_cpu_percent_avg"], 0, "%"),
        fmt(db.get("check_log_projected_mb"), 0),
        f"{verdict} |",
    ])


STEP_HEADER = (
    "| N | Monitors | Checks/s (planned / done) | Startup | Kuvasz CPU (cores, of limit) | Throttled | Kuvasz mem MB "
    "| Heap live / max MB | GC | Full GCs | Hikari pending max / acquire | Worst p99 lag | Worst missed | False DOWN / missed "
    "DOWN | Heartbeat p99 | Slowest UI/API p95 | Postgres CPU | 7-day check log MB | Verdict |\n"
    "|---:|---:|---|---:|---|---:|---:|---|---:|---:|---|---|---:|---|---:|---|---:|---:|---|"
)


STEP_COLUMNS = STEP_HEADER.splitlines()[0].count("|") - 1


def describe(block: dict) -> str:
    b = block["block"]
    return f"profile **{b['profile']}**, mix `{b['mix']}`, heap `{b['heap']}`"


def summary_table(blocks: list[dict], profiles_of: dict) -> str:
    lines = ["| Block | Setup | Last pass | First fail | Why it failed |", "|---|---|---|---|---|"]
    for block in blocks:
        by_scale = {m["scale"]: m for m in block["metrics"]}
        last, first = block["last_pass"], block["first_fail"]
        last_text = f"N={last} ({fmt(monitors(by_scale[last]))} monitors)" if last in by_scale else "–"
        first_text = f"N={first} ({fmt(monitors(by_scale[first]))} monitors)" if first in by_scale else "–"
        reasons = "; ".join(by_scale[first]["failures"]) if first in by_scale else ""
        lines.append(f"| {block['block']['name']} | {describe(block)} | {last_text} | {first_text} | {reasons} |")
    return "\n".join(lines)


def storm_section(block: dict) -> str:
    m = block["metrics"][0]
    lines = [f"### {block['block']['name']}", "", f"N={m['scale']}, {describe(block)}", ""]
    if m.get("kuvasz_crashed"):
        return "\n".join(lines + [f"Kuvasz died: {m['kuvasz_crashed']}", ""])
    lines += [
        "| Phase | Worst p99 lag (group a) | Worst missed | False DOWN | Kuvasz CPU | Hikari pending max "
        "| Slowest UI/API p95 |",
        "|---|---|---:|---:|---:|---:|---|",
    ]
    for phase in ("steady", "outage", "recovery"):
        p = m[phase]
        lag, lag_type = worst(p, "lag_p99_s")
        missed, _ = worst(p, "missed_ratio")
        probe_name, probe_p95 = worst_probe(p)
        lines.append(
            f"| {phase} | {fmt(lag, 1, 's')} ({lag_type or '–'}) | {pct(missed)} | "
            f"{p['correctness']['false_positives']} | {fmt(p['resources']['kuvasz_cpu_cores_avg'], 2)} | "
            f"{fmt(p['jvm'].get('hikari_pending_max'), 0)} | {fmt(probe_p95, 0, 'ms')} ({probe_name or '–'}) |"
        )
    r = m["recovery_of_group_b"]
    lines += [
        "",
        f"Group b after reconnecting: {r['recovered']} monitors back UP (p50 {fmt(r['recovery_p50_s'], 0, 's')}, "
        f"p95 {fmt(r['recovery_p95_s'], 0, 's')}, max {fmt(r['recovery_max_s'], 0, 's')}), "
        f"{r['never_down']} never went DOWN, {r['not_recovered']} not back UP by the end of the window "
        "(long check intervals or failure thresholds).",
        "",
        f"Verdict: {'✅' if m['passed'] else '❌ ' + '; '.join(m['failures'])}",
        "",
    ]
    return "\n".join(lines)


def ui_load_section(block: dict) -> str:
    m = block["metrics"][0]
    lines = [f"### {block['block']['name']}", "", describe(block), ""]
    if m.get("kuvasz_crashed"):
        return "\n".join(lines + [f"Kuvasz died: {m['kuvasz_crashed']}", ""])
    ui, gc = m.get("ui_load", {}), m.get("gc", {})
    lines += [
        f"{fmt(monitors(m))} monitors, {ui.get('users', 0)} users, {fmt(ui.get('requests_per_second'), 2)} requests/s. "
        f"Heap: {fmt(gc.get('heap_capacity_max_mb'), 0)} MB, at most {fmt(gc.get('heap_after_gc_max_mb'), 0)} MB in "
        f"use after a collection, {fmt(gc.get('full_gcs'), 0)} full collections (longest pause "
        f"{fmt(gc.get('pause_max_ms'), 0, 'ms')}).",
        "",
        "| Page / endpoint | Requests | p50 | p95 | Max | Errors | Size |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for name, p in ui.get("endpoints", {}).items():
        lines.append(f"| {name} | {p['requests']} | {fmt(p['p50_ms'], 0, 'ms')} | {fmt(p['p95_ms'], 0, 'ms')} | "
                     f"{fmt(p['max_ms'], 0, 'ms')} | {p['errors']} | {fmt(p['response_kb'], 0, ' KB')} |")
    lines += ["", f"Verdict: {'✅' if m['passed'] else '❌ ' + '; '.join(m['failures'])}", ""]
    return "\n".join(lines)


def series_label(block: dict) -> str:
    """ramp-S-ram60 -> S-ram60: the profile alone isn't unique when a profile is ramped with several settings."""
    return block["block"]["name"].removeprefix("ramp-")


def chart_series(ramps: list[dict]) -> list[tuple[str, str, dict]]:
    """(label, color, block) of every ramp that gets a line, colors assigned in the fixed order of the palette."""
    return [(series_label(block), color, block) for color, block in zip(SERIES_COLORS, ramps)]


def chart(ramps: list[dict], key: str, title: str, value_of, charts_dir: Path) -> str | None:
    fig, ax = plt.subplots(figsize=(7, 3.6), dpi=150)
    fig.patch.set_facecolor(SURFACE)
    ax.set_facecolor(SURFACE)
    plotted = []
    all_xs = set()
    series = chart_series(ramps)
    for label, color, block in series:
        points = []
        for m in block["metrics"]:
            if m.get("kuvasz_crashed"):
                continue
            try:
                value = value_of(m)
            except (KeyError, TypeError):
                value = None
            if value is not None:
                points.append((monitors(m), value, m["passed"]))
        if not points:
            continue
        xs, ys, passed = zip(*points)
        all_xs.update(xs)
        plotted.append((label, xs[-1], ys[-1]))
        ax.plot(xs, ys, color=color, linewidth=2, label=label, zorder=2)
        # Filled markers for passing steps, hollow ones for failing steps
        for x, y, ok in points:
            ax.plot(x, y, marker="o", markersize=6, color=color, markerfacecolor=color if ok else SURFACE,
                    markeredgewidth=2, zorder=3)
    if not plotted:
        plt.close(fig)
        return None
    if len(plotted) <= MAX_DIRECT_LABELS:
        for label, x, y in plotted:
            ax.annotate(label, (x, y), xytext=(6, 0), textcoords="offset points", va="center",
                        color=TEXT_PRIMARY, fontsize=9)
    ax.set_title(title, loc="left", color=TEXT_PRIMARY, fontsize=11)
    ax.set_xlabel("Monitors (filled: passed, hollow: failed)", color=TEXT_SECONDARY, fontsize=9)
    ax.set_xscale("log")
    # Ticks at the measured monitor counts, in plain numbers
    ax.xaxis.set_major_locator(FixedLocator(sorted(all_xs)))
    ax.xaxis.set_major_formatter(FuncFormatter(lambda x, _: f"{x:,.0f}"))
    ax.xaxis.set_minor_formatter(NullFormatter())
    # Neighbouring steps can be close on a log scale (e.g. 4,018 and 5,018), tilted labels don't overlap
    plt.setp(ax.get_xticklabels(), rotation=30, ha="right")
    ax.grid(True, color=GRID, linewidth=0.8, zorder=0)
    for spine in ("top", "right", "left"):
        ax.spines[spine].set_visible(False)
    ax.spines["bottom"].set_color(GRID)
    ax.tick_params(which="both", colors=TEXT_SECONDARY, labelsize=8, length=0)
    ax.set_ylim(bottom=0)
    if len(plotted) > 1:  # a single series is named by the title and its direct label already
        ax.legend(frameon=False, fontsize=8, labelcolor=TEXT_SECONDARY, loc="upper left")
    fig.tight_layout()
    path = charts_dir / f"{key}.png"
    fig.savefig(path, facecolor=SURFACE)
    plt.close(fig)
    return f"![{title}](charts/{path.name})"


def build_report(campaign_dir: Path) -> str:
    blocks = load_campaign(campaign_dir)
    environment_file = campaign_dir / "environment.json"
    environment = json.loads(environment_file.read_text()) if environment_file.exists() else {}
    ramps = [b for b in blocks if b["block"]["kind"] == "ramp"]
    lines = [
        f"# Kuvasz capacity benchmark: {campaign_dir.name}",
        "",
        f"Image `kuvaszmonitoring/kuvasz:{environment.get('kuvasz_tag', '?')}`, Docker: "
        f"{environment.get('docker_cpus', '?')} CPUs, {fmt(environment.get('docker_memory_gb'), 1)} GB "
        f"({environment.get('docker_os', '?')}), host: {environment.get('host', '?')}.",
        "",
        "## Summary",
        "",
        summary_table(blocks, {}),
        "",
    ]
    charts_dir = campaign_dir / "charts"
    charts_dir.mkdir(exist_ok=True)
    if ramps:
        lines += ["## Ramps", ""]
        for key, title, value_of in CHARTS:
            if image := chart(ramps, key, title, value_of, charts_dir):
                lines += [image, ""]
    for block in blocks:
        if block["block"]["kind"] == "storm":
            continue
        lines += [f"### {block['block']['name']}", "", describe(block), "", STEP_HEADER]
        lines += [step_row(m) for m in block["metrics"]]
        lines.append("")
    ui_loads = [b for b in blocks if b["block"]["kind"] == "uiload"]
    if ui_loads:
        lines += ["## UI load", ""]
        lines += [ui_load_section(b) for b in ui_loads]
    storms = [b for b in blocks if b["block"]["kind"] == "storm"]
    if storms:
        lines += ["## Outage storms", ""]
        lines += [storm_section(b) for b in storms]
    lines += ["## Slowest queries (last passing step of each ramp)", ""]
    for block in ramps:
        last = next((m for m in block["metrics"] if m["scale"] == block["last_pass"]), None)
        if not last or not last.get("top_statements"):
            continue
        lines += [f"### {block['block']['name']} at N={last['scale']}", "",
                  "| Total ms | Calls | Mean ms | Query |", "|---:|---:|---:|---|"]
        lines += [f"| {s['total_ms']:,.0f} | {s['calls']:,} | {s['mean_ms']} | `{s['query']}` |"
                  for s in last["top_statements"][:5]]
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("campaign", help="the name of the campaign under results/, or a path to it")
    args = parser.parse_args()
    campaign_dir = Path(args.campaign)
    if not campaign_dir.exists():
        campaign_dir = RESULTS_DIR / args.campaign
    report = build_report(campaign_dir)
    (campaign_dir / "report.md").write_text(report)
    print(campaign_dir / "report.md")


if __name__ == "__main__":
    main()
