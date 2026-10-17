"""Runs the benchmark: a ramp (growing monitor sets on one profile until Kuvasz can't keep up), a single step at a given
scale (knee), an outage storm, or the whole campaign of config/profiles.yml. Results go to results/<name>/."""

import argparse
import dataclasses
import json
import os
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

from kbench import analyze, generate
from kbench.analyze import Window
from kbench.collect import Collector
from kbench.stack import (KUVASZ_CONTAINER, ROOT, Stack, StackSettings, container_running, container_state, db,
                          load_profiles)

RESULTS_DIR = ROOT / "results"
GENERATED_DIR = ROOT / "generated"


@dataclasses.dataclass(frozen=True)
class Block:
    name: str
    kind: str  # ramp | knee | storm | uiload
    profile: str
    mix: str
    heap: str
    virtual_threads: bool
    scales: tuple[int, ...]
    ui_users: int = 0  # emulated UI/API users during the measurement (uiload blocks)

    @classmethod
    def from_config(cls, config: dict, defaults: dict, scales: tuple[int, ...]) -> "Block":
        return cls(
            name=config["name"],
            kind=config["kind"],
            profile=config["profile"],
            mix=config.get("mix", defaults["mix"]),
            heap=config.get("heap", defaults["heap"]),
            virtual_threads=config.get("virtual_threads", defaults["virtual_threads"]),
            scales=scales,
            ui_users=config.get("users", 0),
        )


@dataclasses.dataclass(frozen=True)
class Timeline:
    warmup: int
    measure: int
    storm: dict


def keep_awake():
    """A sleeping Mac pauses the Docker VM, which would ruin the measurements, so caffeinate runs as long as we do."""
    if sys.platform == "darwin" and shutil.which("caffeinate"):
        subprocess.Popen(["caffeinate", "-i", "-s", "-w", str(os.getpid())])


def log(message: str):
    print(f"[{datetime.now():%H:%M:%S}] {message}", flush=True)


def wait_until(deadline: float) -> str | None:
    """Sleeps until the deadline, but returns early with the reason if Kuvasz dies in the meantime."""
    while (remaining := deadline - time.time()) > 0:
        if not container_running(KUVASZ_CONTAINER):
            state = container_state(KUVASZ_CONTAINER)
            return f"exit code {state.get('ExitCode')}, OOM killed: {state.get('OOMKilled')}"
        time.sleep(min(5.0, remaining))
    return None


def first_monitor_id(conn, behavior: str = "healthy") -> int | None:
    row = conn.execute(
        "SELECT id FROM http_monitor WHERE name LIKE %s ORDER BY id LIMIT 1", (f"http-%-{behavior}",)
    ).fetchone()
    return row[0] if row else None


class StepRun:
    """One running Kuvasz with one monitor set, and everything that watches it."""

    def __init__(self, stack: Stack, step_dir: Path, expectations: dict, pushers_file: Path):
        self.stack = stack
        self.step_dir = step_dir
        self.expectations = expectations
        self.pushers_file = pushers_file
        self.collector: Collector | None = None
        self.pusher: subprocess.Popen | None = None
        self.ui_load: subprocess.Popen | None = None
        self.http_monitor_id: int | None = None
        self.step_start = 0.0
        self.startup_seconds: float | None = None
        self.checks_start: float | None = None  # when Kuvasz became healthy
        self.samples: list[dict] = []

    def start(self) -> str | None:
        try:
            self.startup_seconds = self.stack.start_kuvasz()
        except (RuntimeError, TimeoutError) as e:
            return str(e)
        self.checks_start = time.time()
        self.step_start = self.checks_start - self.startup_seconds
        with db() as conn:
            self.http_monitor_id = first_monitor_id(conn)
        self.pusher = subprocess.Popen(
            [sys.executable, "-m", "kbench.pusher", "--pushers", str(self.pushers_file),
             "--out", str(self.step_dir / "push.jsonl")],
            cwd=ROOT,
        )
        self.collector = Collector(self.step_dir, self.http_monitor_id)
        self.collector.start()
        return None

    def start_ui_load(self, users: int):
        command = [sys.executable, "-m", "kbench.uiload", "--users", str(users),
                   "--out", str(self.step_dir / "uiload.jsonl")]
        if self.http_monitor_id is not None:
            command += ["--http-monitor-id", str(self.http_monitor_id)]
        self.ui_load = subprocess.Popen(command, cwd=ROOT)

    def stop(self) -> tuple[list[dict], list[dict]]:
        """Stops the watchers (safe to call more than once) and returns the samples and the heartbeats."""
        if self.ui_load:
            self.ui_load.terminate()
            self.ui_load.wait(timeout=60)
            self.ui_load = None
        if self.collector:
            self.samples = self.collector.stop()
            self.collector = None
        if self.pusher:
            self.pusher.terminate()
            self.pusher.wait(timeout=60)
            self.pusher = None
        log_file = self.step_dir / "kuvasz.log"
        if not log_file.exists():  # also (and especially) when Kuvasz died while starting up
            log_file.write_text(self.stack.kuvasz_logs())
        return self.samples, analyze.load_jsonl(self.step_dir / "push.jsonl")


def analyze_window(conn, window: Window, step_dir: Path, expectations: dict, samples: list[dict],
                   push_records: list[dict], pushers: list[dict], kuvasz_cpus: float,
                   excluded: set[str] = frozenset()) -> dict:
    graces = {p["name"]: p["grace"] for p in pushers}
    included = {n: e for n, e in expectations.items() if n not in excluded}
    ui_load = analyze.load_jsonl(step_dir / "uiload.jsonl")
    return {
        "window": {"start": window.start, "end": window.end, "seconds": round(window.seconds, 1)},
        "timing": analyze.timing_metrics(
            analyze.fetch_check_gaps(conn, window), included, analyze.count_checks(conn, window), window),
        "correctness": analyze.correctness_metrics(
            analyze.fetch_events(conn, window), expectations, window, graces, excluded),
        "resources": analyze.resource_metrics(samples, window, kuvasz_cpus),
        "jvm": analyze.jvm_metrics(samples, window),
        "probes": analyze.probe_metrics(samples, window),
        "push": analyze.push_metrics(push_records, window),
        "gc": analyze.gc_log_metrics(step_dir / "gc", window),
        "collector_errors": analyze.collector_errors(samples, window),
    } | ({"ui_load": analyze.ui_load_metrics(ui_load, window)} if ui_load else {})


class Runner:
    def __init__(self, campaign_dir: Path, profiles: dict, timeline: Timeline, bisect_steps: int):
        self.campaign_dir = campaign_dir
        self.profiles = profiles
        self.timeline = timeline
        self.bisect_steps = bisect_steps

    def settings(self, block: Block, gc_dir: Path) -> StackSettings:
        return StackSettings(block.profile, block.heap, block.virtual_threads,
                             self.profiles["defaults"]["kuvasz_tag"], gc_dir)

    def prepare_step(self, block: Block, block_dir: Path, scale: int) -> tuple[Path, dict, list, dict]:
        step_dir = block_dir / f"n{scale:03d}"
        if step_dir.exists():
            shutil.rmtree(step_dir)  # a re-run step must not keep the files of the previous run
        step_dir.mkdir(parents=True)
        summary = generate.write_step(GENERATED_DIR, block.mix, scale)
        for name in ("summary.json", "expectations.json"):
            shutil.copy(GENERATED_DIR / name, step_dir / name)
        expectations = json.loads((GENERATED_DIR / "expectations.json").read_text())
        pushers = json.loads((GENERATED_DIR / "pushers.json").read_text())
        return step_dir, expectations, pushers, summary

    def start_block(self, block: Block, block_dir: Path) -> Stack:
        subprocess.run(["bash", str(ROOT / "stack" / "pki.sh")], check=True)
        stack = Stack(self.settings(block, block_dir / "gc"), self.profiles)
        stack.reset()
        stack.up_dependencies()
        return stack

    def finish_step(self, step_dir: Path, metrics: dict) -> dict:
        metrics["failures"] = analyze.evaluate_gates(metrics, self.profiles["gates"])
        metrics["warnings"] = analyze.validity_warnings(metrics, self.profiles["max_target_cpu_percent"])
        metrics["passed"] = not metrics["failures"]
        (step_dir / "metrics.json").write_text(json.dumps(metrics, indent=1, default=str))
        verdict = "PASS" if metrics["passed"] else "FAIL: " + "; ".join(metrics["failures"])
        log(f"  {verdict}")
        for warning in metrics["warnings"]:
            log(f"  warning: {warning}")
        return metrics

    def run_step(self, block: Block, block_dir: Path, stack: Stack, scale: int) -> dict:
        step_dir, expectations, pushers, summary = self.prepare_step(block, block_dir, scale)
        stack = Stack(dataclasses.replace(stack.settings, gc_log_dir=step_dir / "gc"), self.profiles)
        log(f"{block.name}: N={scale} ({summary['stub_monitors']} monitors, {summary['checks_per_second']} checks/s)")
        run = StepRun(stack, step_dir, expectations, GENERATED_DIR / "pushers.json")
        metrics = {"block": block.name, "scale": scale, "summary": summary,
                   "settings": dataclasses.asdict(block) | {"scales": None}}

        try:
            crash = run.start()
            if crash is None:
                log(f"  healthy after {run.startup_seconds:.1f}s, warming up")
                metrics["startup_seconds"] = round(run.startup_seconds, 1)
                crash = wait_until(run.step_start + run.startup_seconds + self.timeline.warmup)
            if crash is None:
                with db() as conn:
                    before = analyze.db_snapshot(conn)
                    # After the snapshot, so that its own queries don't show up among the slowest ones
                    analyze.reset_statement_stats(conn)
                if block.ui_users:
                    run.start_ui_load(block.ui_users)
                    log(f"  {block.ui_users} UI/API users started")
                window_start = time.time()
                log("  measuring")
                crash = wait_until(window_start + self.timeline.measure)
                window = Window(run.step_start, window_start, time.time(), run.checks_start)
        finally:
            samples, push_records = run.stop()
        if crash is not None:
            metrics["kuvasz_crashed"] = crash
            return self.finish_step(step_dir, metrics)

        with db() as conn:
            # First, before the analysis below adds its own queries to the statistics
            metrics["top_statements"] = analyze.top_statements(conn)
            after = analyze.db_snapshot(conn)
            metrics |= analyze_window(conn, window, step_dir, expectations, samples, push_records, pushers,
                                      self.kuvasz_cpus(block))
            metrics["db"] = analyze.db_metrics(before, after, window)
        return self.finish_step(step_dir, metrics)

    def kuvasz_cpus(self, block: Block) -> float:
        return float(self.profiles["profiles"][block.profile]["kuvasz"]["cpus"])

    def run_ramp(self, block: Block) -> dict:
        block_dir = self.campaign_dir / block.name
        stack = self.start_block(block, block_dir)
        steps = []
        for scale in block.scales:
            metrics = self.run_step(block, block_dir, stack, scale)
            steps.append({"scale": scale, "passed": metrics["passed"], "failures": metrics["failures"]})
            if not metrics["passed"]:
                break
        for _ in range(self.bisect_steps):
            scale = midpoint(steps)
            if scale is None:
                break
            # The monitor sets are prefixes of each other, so a smaller set just drops the monitors at the end
            metrics = self.run_step(block, block_dir, stack, scale)
            steps.append({"scale": scale, "passed": metrics["passed"], "failures": metrics["failures"]})
        stack.stop_kuvasz()
        return self.block_result(block, steps)

    def run_knee(self, block: Block) -> dict:
        block_dir = self.campaign_dir / block.name
        stack = self.start_block(block, block_dir)
        metrics = self.run_step(block, block_dir, stack, block.scales[0])
        stack.stop_kuvasz()
        return self.block_result(block, [{"scale": block.scales[0], "passed": metrics["passed"],
                                          "failures": metrics["failures"]}])

    def run_storm(self, block: Block) -> dict:
        """Steady state, then group b of the targets goes offline, then it comes back. The healthy monitors of group
        a must stay on time and UP throughout, and group b must recover."""
        block_dir = self.campaign_dir / block.name
        stack = self.start_block(block, block_dir)
        scale = block.scales[0]
        step_dir, expectations, pushers, summary = self.prepare_step(block, block_dir, scale)
        stack = Stack(dataclasses.replace(stack.settings, gc_log_dir=step_dir / "gc"), self.profiles)
        log(f"{block.name}: N={scale} ({summary['stub_monitors']} monitors)")
        run = StepRun(stack, step_dir, expectations, GENERATED_DIR / "pushers.json")
        metrics = {"block": block.name, "scale": scale, "summary": summary, "kind": "storm",
                   "settings": dataclasses.asdict(block) | {"scales": None}}
        group_b = {n for n, e in expectations.items() if e["group"] == "b"}

        phases = {}
        try:
            crash = self.storm_phases(run, stack, phases)
        finally:
            samples, push_records = run.stop()
        if run.startup_seconds is not None:
            metrics["startup_seconds"] = round(run.startup_seconds, 1)
        if crash is not None:
            metrics["kuvasz_crashed"] = crash
            self.finish_step(step_dir, metrics)
            return self.block_result(block, [{"scale": scale, "passed": False, "failures": [crash]}])

        with db() as conn:
            for phase, window in phases.items():
                excluded = group_b if phase in ("outage", "recovery") else frozenset()
                metrics[phase] = analyze_window(conn, window, step_dir, expectations, samples, push_records,
                                                pushers, self.kuvasz_cpus(block), excluded)
            metrics["recovery_of_group_b"] = analyze.recovery_metrics(
                analyze.fetch_events(conn, phases["recovery"]), expectations, group_b, phases["recovery"].start)
        # The gate is about what happens to the rest of the monitors while group b is down
        gated = {k: metrics["outage"][k] for k in ("timing", "correctness", "probes", "push", "jvm", "resources")}
        metrics |= {k: v for k, v in gated.items()}
        self.finish_step(step_dir, metrics)
        return self.block_result(block, [{"scale": scale, "passed": metrics["passed"],
                                          "failures": metrics["failures"]}])

    def storm_phases(self, run: StepRun, stack: Stack, phases: dict) -> str | None:
        """Runs the phases of the storm, collecting their windows into phases. Returns why Kuvasz died, if it did."""
        storm = self.timeline.storm
        crash = run.start()
        if crash is None:
            crash = wait_until(time.time() + self.timeline.warmup)
        if crash is None:
            log("  steady state")
            start = time.time()
            crash = wait_until(start + storm["steady_seconds"])
            phases["steady"] = Window(run.step_start, start, time.time(), run.checks_start)
        if crash is None:
            log("  outage: group b disconnected")
            stack.disconnect_group_b()
            start = time.time()
            crash = wait_until(start + storm["outage_seconds"])
            phases["outage"] = Window(run.step_start, start, time.time(), run.checks_start)
            stack.reconnect_group_b()
        if crash is None:
            log("  recovery: group b reconnected")
            start = time.time()
            crash = wait_until(start + storm["recovery_seconds"])
            phases["recovery"] = Window(run.step_start, start, time.time(), run.checks_start)
        return crash

    def block_result(self, block: Block, steps: list[dict]) -> dict:
        passed = [s["scale"] for s in steps if s["passed"]]
        failed = [s["scale"] for s in steps if not s["passed"]]
        result = {
            "block": dataclasses.asdict(block),
            "steps": steps,
            "last_pass": max(passed) if passed else None,
            "first_fail": min(failed) if failed else None,
            "completed_at": datetime.now().isoformat(),
        }
        (self.campaign_dir / block.name / "block.json").write_text(json.dumps(result, indent=1))
        return result

    def run_block(self, block: Block) -> dict:
        (self.campaign_dir / block.name).mkdir(parents=True, exist_ok=True)
        runners = {"ramp": self.run_ramp, "knee": self.run_knee, "uiload": self.run_knee, "storm": self.run_storm}
        return runners[block.kind](block)


def midpoint(steps: list[dict]) -> int | None:
    """The scale halfway between the biggest passing and the smallest failing one, if there's any room between them."""
    passed = [s["scale"] for s in steps if s["passed"]]
    failed = [s["scale"] for s in steps if not s["passed"]]
    if not passed or not failed:
        return None
    low, high = max(passed), min(failed)
    middle = (low + high) // 2
    return middle if low < middle < high else None


def write_environment(campaign_dir: Path, kuvasz_tag: str):
    info = subprocess.run(
        ["docker", "info", "--format", "{{.NCPU}}|{{.MemTotal}}|{{.OperatingSystem}}|{{.ServerVersion}}"],
        capture_output=True, text=True, check=True,
    ).stdout.strip().split("|")
    host = subprocess.run(["uname", "-mrs"], capture_output=True, text=True).stdout.strip()
    if sys.platform == "darwin":
        cpu = subprocess.run(["sysctl", "-n", "machdep.cpu.brand_string"], capture_output=True, text=True)
        host = f"{cpu.stdout.strip()}, {host}"
    environment = {
        "kuvasz_tag": kuvasz_tag,
        "docker_cpus": int(info[0]),
        "docker_memory_gb": round(int(info[1]) / 2**30, 1),
        "docker_os": info[2],
        "docker_version": info[3],
        "host": host,
        "started_at": datetime.now().isoformat(),
    }
    (campaign_dir / "environment.json").write_text(json.dumps(environment, indent=1))


def completed_block(campaign_dir: Path, name: str) -> dict | None:
    path = campaign_dir / name / "block.json"
    return json.loads(path.read_text()) if path.exists() else None


def run_campaign(runner: Runner, profiles: dict):
    defaults = profiles["defaults"]
    for config in profiles["campaign"]:
        if done := completed_block(runner.campaign_dir, config["name"]):
            log(f"{config['name']}: already done (last pass: N={done['last_pass']}), skipping")
            continue
        if config["kind"] == "ramp":
            scales = tuple(config.get("ladder", defaults["ladder"]))
        elif "scale" in config:
            scales = (config["scale"],)
        else:
            source = completed_block(runner.campaign_dir, config["scale_from"])
            scale = source and source[config["pick"]]
            if scale is None:
                log(f"{config['name']}: {config['scale_from']} has no {config['pick']} scale, skipping")
                continue
            scales = (min(scale, max(defaults["ladder"])),)
        runner.run_block(Block.from_config(config, defaults, scales))


def main():
    profiles = load_profiles()
    defaults = profiles["defaults"]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", default=datetime.now().strftime("%Y%m%d-%H%M"),
                        help="results/<name>; reuse it to resume a campaign")
    parser.add_argument("--warmup", type=int, default=defaults["warmup_seconds"])
    parser.add_argument("--measure", type=int, default=defaults["measure_seconds"])
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("campaign", help="everything in the campaign section of config/profiles.yml")
    for kind in ("ramp", "knee", "storm", "uiload"):
        p = sub.add_parser(kind)
        p.add_argument("--profile", required=True, choices=sorted(profiles["profiles"]))
        p.add_argument("--block-name", help="defaults to <kind>-<profile>[-pool]-<heap>; reuse a block's name to add "
                                            "steps to it (e.g. the missing steps of an interrupted ramp, with --ladder): "
                                            "its other steps are kept, the re-run ones are replaced")
        p.add_argument("--mix", default=defaults["mix"])
        p.add_argument("--heap", default=defaults["heap"], choices=sorted(profiles["heap_modes"]))
        p.add_argument("--virtual-threads", action=argparse.BooleanOptionalAction,
                       default=defaults["virtual_threads"])
        if kind == "ramp":
            p.add_argument("--ladder", default=",".join(map(str, defaults["ladder"])))
        else:
            p.add_argument("--scale", type=int, required=True)
        if kind == "uiload":
            p.add_argument("--users", type=int, default=5, help="emulated UI/API users during the measurement")
    args = parser.parse_args()

    keep_awake()
    campaign_dir = RESULTS_DIR / args.name
    campaign_dir.mkdir(parents=True, exist_ok=True)
    timeline = Timeline(args.warmup, args.measure, profiles["storm"])
    runner = Runner(campaign_dir, profiles, timeline, defaults["bisect_steps"])
    if not (campaign_dir / "environment.json").exists():
        write_environment(campaign_dir, defaults["kuvasz_tag"])
    log(f"Results: {campaign_dir}")
    if args.command == "campaign":
        run_campaign(runner, profiles)
    else:
        scales = tuple(int(s) for s in args.ladder.split(",")) if args.command == "ramp" else (args.scale,)
        name = args.block_name or (
            f"{args.command}-{args.profile}" + ("" if args.virtual_threads else "-pool") + f"-{args.heap}")
        block = Block(name, args.command, args.profile, args.mix, args.heap, args.virtual_threads, scales,
                      getattr(args, "users", 0))
        result = runner.run_block(block)
        log(f"{block.name}: last pass N={result['last_pass']}, first fail N={result['first_fail']}")


if __name__ == "__main__":
    main()
