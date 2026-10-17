"""Generates the monitor set of a benchmark step: a mix scaled by N, written as Kuvasz's startup YAML, plus the
expectations (what each monitor is supposed to report) and the push clients the pusher has to emulate."""

import argparse
import json
import math
import uuid
from dataclasses import dataclass, field
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
CONFIG_DIR = ROOT / "config"

BASE_SET_SIZE = 100
TYPES = ("http", "push", "tcp", "icmp", "dns", "docker")
YAML_KEYS = {t: f"{t}-monitors" for t in TYPES}

GROUP_HOSTS = {
    "a": {"http": "172.31.89.10", "dns": "172.31.89.20"},
    "b": {"http": "172.31.89.11", "dns": "172.31.89.21"},
}
PING_TARGETS = {
    "a": ["172.31.89.10", "172.31.89.20", "172.31.89.30", "172.31.89.31", "172.31.89.32"],
    "b": ["172.31.89.11", "172.31.89.21"],
}
# Nothing lives on these addresses of the bench subnet, so connections and pings to them never get an answer
UNUSED_IPS = [f"172.31.89.{i}" for i in range(200, 210)]
DOCKER_HOST_NAME = "local"
WEBHOOK_INTEGRATION = "webhook:bench-sink"
KEYWORD = "kuvasz-bench-ok"
PUSH_SECRET_NAMESPACE = uuid.UUID("5d6c3f0e-6f0b-4b8e-9a51-6b6e0c1d2a7f")

EXPECTED_STATUS = {"healthy": "UP", "slow": "UP", "flapping": None, "down": "DOWN", "timeout": "DOWN"}

# Every attribute of a monitor is drawn from its own low-discrepancy stream, frac(k * sqrt(p)) with a distinct prime p
# per attribute. Unlike random draws, these hit every share closely even for a handful of monitors, and since the k-th
# monitor of a type doesn't depend on the scale, the set of N is always a prefix of the set of any bigger N, which keeps
# the names stable while a ramp grows the set.
_STREAM_PRIMES = {
    "interval": 2,
    "behavior": 3,
    "threshold": 5,
    "group": 7,
    "integration": 11,
    "feature_1": 13,
    "feature_2": 17,
    "feature_3": 19,
    "feature_4": 23,
    "feature_5": 29,
    "feature_6": 31,
    "grace": 37,
}


def stream(name: str, k: int) -> float:
    return ((k + 1) * math.sqrt(_STREAM_PRIMES[name])) % 1.0


def pick(weights: dict, u: float):
    """Maps u in [0, 1) onto the keys of weights, proportionally to their weights."""
    total = sum(weights.values())
    acc = 0.0
    for key, weight in weights.items():
        acc += weight / total
        if u < acc:
            return key
    return key


@dataclass
class Monitor:
    name: str
    type: str
    behavior: str
    interval: int
    threshold: int
    group: str | None
    config: dict
    push: dict | None = field(default=None)

    @property
    def expected(self) -> str | None:
        return EXPECTED_STATUS[self.behavior]


def load_mix(mix: str) -> dict:
    distributions = yaml.safe_load((CONFIG_DIR / "distributions.yml").read_text())
    mix_config = yaml.safe_load((CONFIG_DIR / "mixes" / f"{mix}.yml").read_text())
    merged = {**distributions, **mix_config}
    if sum(merged["types"].values()) != BASE_SET_SIZE:
        raise ValueError(f"The type shares of mix '{mix}' must add up to {BASE_SET_SIZE}")
    unknown = set(merged["types"]) - set(TYPES)
    if unknown:
        raise ValueError(f"Unknown monitor types in mix '{mix}': {unknown}")
    return merged


def block_layout(type_shares: dict) -> list[str]:
    """The order of the types within a block of BASE_SET_SIZE monitors, each type spread evenly over the block."""
    slots = [
        ((j + 0.5) / count, monitor_type)
        for monitor_type, count in type_shares.items()
        for j in range(count)
    ]
    return [monitor_type for _, monitor_type in sorted(slots)]


def generate(mix: dict, scale: int) -> list[Monitor]:
    layout = block_layout(mix["types"])
    counters = dict.fromkeys(TYPES, 0)
    monitors = []
    for _ in range(scale):
        for monitor_type in layout:
            k = counters[monitor_type]
            counters[monitor_type] += 1
            monitors.append(build_monitor(monitor_type, k, mix))
    return monitors


def build_monitor(monitor_type: str, k: int, mix: dict) -> Monitor:
    features = mix["features"]
    behavior = pick(mix["behaviors"][monitor_type], stream("behavior", k))
    interval = int(pick(mix["intervals"][monitor_type], stream("interval", k)))
    threshold = int(pick(mix["failure_count_threshold"], stream("threshold", k)))
    group = "b" if stream("group", k) < mix["group_b_share"] / 100 else "a"
    name = f"{monitor_type}-{k:05d}-{behavior}"

    builder = _BUILDERS[monitor_type]
    config, push = builder(k, behavior, interval, group, features)
    config = {"name": name, **config, "failure-count-threshold": threshold}
    if stream("integration", k) < features["integration"] / 100:
        config["integrations"] = [WEBHOOK_INTEGRATION]

    if monitor_type in ("push", "docker") or behavior == "timeout":
        group = None  # these targets are not part of a target group
    if push is not None:
        push["name"] = name
        push["behavior"] = behavior
    return Monitor(name, monitor_type, behavior, interval, threshold, group, config, push)


def flap_period(interval: int) -> int:
    # Long enough for every failure count threshold to be reached within a DOWN phase
    return max(60, interval * 8)


def _http(k, behavior, interval, group, features):
    ssl = stream("feature_1", k) < features["http_ssl"] / 100
    https = ssl or stream("feature_2", k) < features["http_https_without_ssl_check"] / 100
    host = GROUP_HOSTS[group]["http"]
    base = f"https://{host}" if https else f"http://{host}" + (":8081" if k % 2 else "")
    path = {
        "healthy": "/ok",
        "slow": "/slow",
        "flapping": f"/flap/{flap_period(interval)}",
        "down": "/down",
        "timeout": "/hang",
    }[behavior]
    config = {"url": base + path, "uptime-check-interval": interval, "category": f"web-{group}"}
    if ssl:
        config["ssl-check-enabled"] = True
    if stream("feature_3", k) < features["http_keyword"] / 100:
        config["expected-keyword"] = KEYWORD
    elif stream("feature_4", k) < features["http_head_method"] / 100:
        config["request-method"] = "HEAD"
    if stream("feature_5", k) < features["http_expected_headers"] / 100:
        config["expected-headers"] = {"X-Bench": "ok"}
    if stream("feature_6", k) < features["http_response_time_threshold"] / 100:
        config["response-time-threshold-millis"] = 5000
    return config, None


def _push(k, behavior, interval, group, features):
    grace = round(interval * (0.1 + 0.1 * stream("grace", k)))
    secret = str(uuid.uuid5(PUSH_SECRET_NAMESPACE, f"push-{k}"))
    config = {
        "client-secret": secret,
        "heartbeat-interval": interval,
        "grace-period": grace,
        "category": "jobs",
    }
    return config, {"secret": secret, "interval": interval, "grace": grace}


def _tcp(k, behavior, interval, group, features):
    http_host = GROUP_HOSTS[group]["http"]
    if behavior == "healthy":
        host, port = [(http_host, 80), (http_host, 443), (http_host, 8081), (GROUP_HOSTS[group]["dns"], 53)][k % 4]
    elif behavior == "down":
        host, port = http_host, 9  # nothing listens there: connection refused
    else:
        host, port = UNUSED_IPS[k % len(UNUSED_IPS)], 80
    return {"host": host, "port": port, "uptime-check-interval": interval, "category": f"infra-{group}"}, None


def _icmp(k, behavior, interval, group, features):
    if behavior == "healthy":
        targets = PING_TARGETS[group]
        host = targets[k % len(targets)]
    else:
        host = UNUSED_IPS[k % len(UNUSED_IPS)]
    return {"host": host, "uptime-check-interval": interval, "category": f"infra-{group}"}, None


def _dns(k, behavior, interval, group, features):
    resolver = GROUP_HOSTS[group]["dns"]
    config = {"resolver-host": resolver, "uptime-check-interval": interval, "category": f"dns-{group}"}
    if behavior == "healthy":
        variant = k % 10
        if variant <= 6:
            config["host"] = f"h{k}.svc.bench.test"
            config["record-matchers"] = [{"record-type": "A", "match-type": "EXACT", "value": "172.31.89.10"}]
            if stream("feature_1", k) < features["dns_drift_detection"] / 100:
                config["drift-detection-enabled"] = True
                config["drift-record-types"] = ["A"]
        elif variant == 7:
            config["host"] = f"h{k}.svc.bench.test"
            config["record-matchers"] = [{"record-type": "TXT", "match-type": "CONTAINS", "value": "kuvasz-bench"}]
        elif variant == 8:
            config["host"] = "app.bench.test"
            config["record-matchers"] = [{"record-type": "CNAME", "match-type": "CONTAINS", "value": "www.bench.test"}]
        else:
            config["host"] = "bench.test"
            config["record-matchers"] = [{"record-type": "MX", "match-type": "CONTAINS", "value": "mail.bench.test"}]
    elif behavior == "slow":
        config["host"] = f"h{k}.svc.bench.test"
        config["resolver-port"] = 1053
    elif behavior == "down":
        config["host"] = f"x{k}.missing.bench.test"  # NXDOMAIN, while NOERROR is expected
    else:
        config["host"] = f"h{k}.svc.bench.test"
        config["resolver-port"] = 2053
    return config, None


def _docker(k, behavior, interval, group, features):
    container = {
        "healthy": f"kb-work-healthy-{1 + k % 3}",
        "flapping": "kb-work-flapping",
        "down": "kb-work-stopped" if k % 2 == 0 else "kb-work-unhealthy",
    }[behavior]
    config = {
        "docker-host": DOCKER_HOST_NAME,
        "container": container,
        "uptime-check-interval": interval,
        "category": "workloads",
    }
    if stream("feature_1", k) < features["docker_metrics_history"] / 100:
        config["metrics-history-enabled"] = True
    return config, None


_BUILDERS = {"http": _http, "push": _push, "tcp": _tcp, "icmp": _icmp, "dns": _dns, "docker": _docker}


def load_real_slice() -> dict:
    return yaml.safe_load((CONFIG_DIR / "real-slice.yml").read_text())


def build_kuvasz_config(monitors: list[Monitor], real_slice: dict | None) -> dict:
    config = {
        "micronaut": {
            "metrics": {
                "enabled": True,
                "export": {"prometheus": {"enabled": True, "descriptions": False}},
                # Turned off in the image's application.yml, re-enabled for the measurements
                "binders": {
                    "jvm": {"enabled": True},
                    "executor": {"enabled": True},
                    "jdbc": {"enabled": True},
                    "processor": {"enabled": True},
                    "logback": {"enabled": True},
                    "web": {"enabled": True},
                    "netty": {"bytebuf-allocators": {"enabled": True}},
                },
            }
        },
        "docker-hosts": [{"name": DOCKER_HOST_NAME, "url": "unix:///var/run/docker.sock"}],
        "integrations": {
            "webhook": [{"name": "bench-sink", "url": "http://172.31.89.10/hook", "http-method": "POST"}]
        },
    }
    for monitor_type in TYPES:
        key = YAML_KEYS[monitor_type]
        entries = [m.config for m in monitors if m.type == monitor_type]
        entries += (real_slice or {}).get(key) or []
        # An explicit list (even an empty one) makes the YAML the source of truth of that type
        config[key] = entries
    return config


def build_expectations(monitors: list[Monitor], real_slice: dict | None) -> dict:
    expectations = {
        m.name: {
            "type": m.type,
            "behavior": m.behavior,
            "expected": m.expected,
            "interval": m.interval,
            "threshold": m.threshold,
            "group": m.group,
        }
        for m in monitors
    }
    for monitor_type in TYPES:
        for entry in (real_slice or {}).get(YAML_KEYS[monitor_type]) or []:
            interval = entry.get("uptime-check-interval") or entry.get("heartbeat-interval")
            expectations[entry["name"]] = {
                "type": monitor_type,
                "behavior": "real",
                "expected": None,
                "interval": interval,
                "threshold": entry.get("failure-count-threshold", 1),
                "group": None,
            }
    return expectations


def write_step(out_dir: Path, mix_name: str, scale: int, with_real_slice: bool = True) -> dict:
    mix = load_mix(mix_name)
    monitors = generate(mix, scale)
    real_slice = load_real_slice() if with_real_slice else None
    names = [m.name for m in monitors] + [
        e["name"] for key in YAML_KEYS.values() for e in ((real_slice or {}).get(key) or [])
    ]
    if len(names) != len(set(names)):
        raise ValueError("Monitor names must be unique")

    out_dir.mkdir(parents=True, exist_ok=True)
    kuvasz_config = build_kuvasz_config(monitors, real_slice)
    (out_dir / "kuvasz.yml").write_text(yaml.safe_dump(kuvasz_config, sort_keys=False, width=200))
    expectations = build_expectations(monitors, real_slice)
    (out_dir / "expectations.json").write_text(json.dumps(expectations, indent=1))
    pushers = [m.push for m in monitors if m.push is not None]
    (out_dir / "pushers.json").write_text(json.dumps(pushers, indent=1))

    summary = summarize(monitors, mix_name, scale, len(names) - len(monitors))
    (out_dir / "summary.json").write_text(json.dumps(summary, indent=1))
    return summary


def summarize(monitors: list[Monitor], mix_name: str, scale: int, real_slice_size: int) -> dict:
    by_type = {}
    for monitor_type in TYPES:
        of_type = [m for m in monitors if m.type == monitor_type]
        by_type[monitor_type] = {
            "count": len(of_type),
            # Scheduled checks per second; push monitors are evaluated by a single periodic job instead
            "checks_per_second": round(sum(1 / m.interval for m in of_type), 3),
        }
    return {
        "mix": mix_name,
        "scale": scale,
        "stub_monitors": len(monitors),
        "real_slice_monitors": real_slice_size,
        "by_type": by_type,
        "checks_per_second": round(sum(v["checks_per_second"] for t, v in by_type.items() if t != "push"), 3),
        "heartbeats_per_second": by_type["push"]["checks_per_second"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mix", default="balanced")
    parser.add_argument("--scale", type=int, default=1, help=f"multiplier of the {BASE_SET_SIZE}-monitor base set")
    parser.add_argument("--out", type=Path, default=ROOT / "generated")
    parser.add_argument("--no-real-slice", action="store_true", help="leave out the real internet targets")
    args = parser.parse_args()
    summary = write_step(args.out, args.mix, args.scale, with_real_slice=not args.no_real_slice)
    print(json.dumps(summary, indent=1))


if __name__ == "__main__":
    main()
