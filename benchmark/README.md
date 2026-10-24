# Kuvasz capacity benchmark

A realistic benchmark of the published Kuvasz docker image. It finds out how many monitors an instance can handle
with a given amount of CPU and memory, and it measures how Kuvasz behaves as the number grows: memory, CPU, timing of
the checks, database growth, responsiveness of the UI and the API.

The image is used as it is published (`kuvaszmonitoring/kuvasz:<tag>`, pulled, never built), so the same harness can
compare releases against each other.

## Prerequisites

- Docker (Docker Desktop on macOS works) with **at least 8 CPUs and 12 GB of memory** given to it: the biggest profile
  alone takes 6 CPUs and 8 GB (Kuvasz + Postgres), and the stub targets need their share too
- [uv](https://docs.astral.sh/uv/) (it brings the right Python version, 3.14+, with itself)
- `openssl` on the host

The stack uses the `172.31.89.0/24` subnet and the host ports `18080` (Kuvasz) and `55432` (Postgres).

## How it works

```
                 ┌──────────── docker network "kbench" (172.31.89.0/24) ─────────────┐
 host            │                                                                   │
 ─────           │  kb-kuvasz ──checks──► kb-openresty-a/b   HTTP(S), TCP, ICMP      │
 kbench (uv) ────┼─► (limits)            kb-coredns-a/b     DNS (healthy/slow/dead)  │
  generate       │      │               kb-work-*          docker monitors, ICMP    │
  pusher ────────┼──heartbeats          docker.sock        (the local daemon)       │
  collector      │      ▼                                                            │
                 │  kb-postgres (limits)          + a small slice of real internet  │
                 └───────────────────────────────────────────────────────────────────┘
```

- **Kuvasz and Postgres** run with hard CPU and memory limits (no swap), taken from a resource profile.
- **The monitored targets are local stubs**, so thousands of checks don't put load on anybody on the internet, and every
  target behaves in a known way:
  - `openresty` serves HTTP(S) endpoints that are fast, slow, flapping, failing or hanging, and is also a TCP and a
    ping target. Keep-alive is turned off on purpose: real monitored sites are mostly distinct hosts, so a check rarely
    finds an open connection to reuse, and has to do its own TCP and TLS handshake.
  - `coredns` is authoritative for `bench.test` on port 53, answers slowly on port 1053, and drops every query on
    port 2053.
  - A few `busybox` containers are the targets of the docker monitors: healthy ones, an unhealthy one, one that keeps
    restarting, and a stopped one.
  - Every target runs twice, as group **a** (~70% of the monitors) and group **b** (~30%), so the outage storm can take
    a realistic slice of the targets offline at once by disconnecting group b from the network.
- **A small, fixed slice of real internet targets** ([`config/real-slice.yml`](config/real-slice.yml)) is added to every
  run, to keep real DNS, TLS and network latency in the mix. It's never scaled, and it's left out of the pass/fail
  decision, since the internet is not under our control.
- HTTPS targets use a certificate of a local CA, which Kuvasz trusts through a JVM truststore (the JDK's own CA
  certificates + the local CA) passed with `JAVA_TOOL_OPTIONS`.
- The monitors are loaded through the startup YAML (`/config/kuvasz.yml`), which also turns the Micrometer binders (JVM,
  executors, Hikari, HTTP server) back on, since the image ships with all of them disabled.

## Monitor sets

A monitor set is a **mix** scaled by **N**: every mix defines a base set of 100 monitors, and N multiplies it.

| Mix                                              | HTTP | Push | TCP | ICMP | DNS | Docker |
|--------------------------------------------------|-----:|-----:|----:|-----:|----:|-------:|
| [`balanced`](config/mixes/balanced.yml)          |   45 |   15 |  10 |   10 |  10 |     10 |
| [`homelab`](config/mixes/homelab.yml)            |   25 |   10 |  10 |   15 |   5 |     35 |
| [`web-agency`](config/mixes/web-agency.yml)      |   70 |    5 |   5 |    5 |  13 |      2 |
| [`infra`](config/mixes/infra.yml)                |   20 |   10 |  25 |   20 |  20 |      5 |

The check intervals, the target behaviors, the failure count thresholds, and the share of the optional features (SSL
checks, keyword matching, expected headers, DNS drift detection, docker metrics history, notifications to a webhook
sink) are defined in [`config/distributions.yml`](config/distributions.yml). A mix can override any of them.

Each target behavior implies what the monitor must report:

| Behavior   | Example                                                     | Expected |
|------------|-------------------------------------------------------------|----------|
| `healthy`  | fast 200 with the expected keyword, open port, valid answer | UP       |
| `slow`     | 0.3-2s response, delayed DNS answer, late push within grace | UP       |
| `flapping` | UP and DOWN in turns, a restarting container                | either   |
| `down`     | 503, refused port, NXDOMAIN, stopped container              | DOWN     |
| `timeout`  | hanging response, unreachable IP, dropped DNS query         | DOWN     |

The sets are deterministic, and the set of N is always a prefix of the set of a bigger N (the names stay stable), so a
growing set keeps the monitors of the previous step, together with their history. Every attribute is drawn from a
low-discrepancy sequence instead of random numbers, so every share is met closely even in the smallest set.

To look at a set without running anything:

```shell
uv run python -m kbench.generate --mix balanced --scale 5   # writes generated/kuvasz.yml, expectations.json, ...
```

## Resource profiles

Kuvasz and Postgres are scaled together, like on a single box ([`config/profiles.yml`](config/profiles.yml)):

| Profile | Kuvasz CPU / memory | Postgres CPU / memory |
|---------|---------------------|-----------------------|
| XS      | 0.5 / 512 MB        | 0.5 / 512 MB          |
| S       | 1 / 1 GB            | 1 / 1 GB              |
| M       | 2 / 2 GB            | 1 / 2 GB              |
| L       | 4 / 4 GB            | 2 / 4 GB              |

The image limits the heap to 192 MB (`-Xmx192M` in its `JAVA_TOOL_OPTIONS`), no matter how much memory the container
gets. The `ram75` heap mode (`-XX:MaxRAMPercentage=75`) ties the heap to the container's memory limit instead, the
`stock` heap mode keeps what the image ships with. XS uses the `stock` heap: besides the heap, the JVM needs ~190 MB
(classes, compiled code, thread stacks), so 75% of 512 MB for the heap pushes the container to its memory limit, where
the whole process keeps stalling.

The CPUs are the cores of the machine that runs the benchmark: half a core of a modern laptop is a lot more than half a
core of a Raspberry Pi, so the results of a slow machine can be a lot lower.

## Smoke test

Boots the whole stack with a small set, lets the checks run for a while, then verifies that every stub monitor reports
what its target behavior implies, that every monitor type writes its per-check rows, and that Kuvasz logged no errors:

```shell
caffeinate -i uv run python -m kbench.smoke                   # N=1 on the S profile, ~8 minutes
caffeinate -i uv run python -m kbench.smoke --scale 5 --keep  # keep the stack running afterwards
```

> [!IMPORTANT]
> On macOS, always run under `caffeinate -i`: if the machine goes to sleep, the Docker VM is paused with it, and the
> measurements are not valid.

With `--keep`, the UI is available on <http://localhost:18080> (`bench` / `bench-password`). Tear everything down,
including the database, with:

```shell
docker compose down --volumes
```

## Running the benchmark

```shell
uv run python -m kbench.run campaign                 # everything in config/profiles.yml, ~3h15m
uv run python -m kbench.run --name my-run campaign   # rerun with the same name after an interruption
uv run python -m kbench.report my-run                # results/my-run/report.md + charts
```

When a campaign is rerun with the same name, the finished blocks are skipped, and an interrupted block starts over.

The runner keeps the Mac awake (`caffeinate`), but only while it runs: when you chain several runs, wrap the
whole chain in a single `caffeinate -i -s`, otherwise the Mac may fall asleep in the few seconds between two runs, and a
sleeping Mac pauses the Docker VM too. Single blocks can be run too, e.g. while experimenting:

```shell
uv run python -m kbench.run ramp --profile M --ladder 1,5,15
uv run python -m kbench.run knee --profile M --scale 40 --heap stock
uv run python -m kbench.run storm --profile M --scale 40
uv run python -m kbench.run uiload --profile XS --heap stock --scale 15 --users 5
uv run python -m kbench.run --warmup 30 --measure 60 ramp --profile S --ladder 1,2   # a quick dry run
```

### What a campaign does

| Block                    | What it answers                                                                   |
|--------------------------|-----------------------------------------------------------------------------------|
| `ramp-XS`, `-S`, `-M`, `-L` | how many monitors a profile can handle (the upper bound)                    |
| `knee-M-stock-heap`      | does the image's default `-Xmx192M` heap still work at M's upper bound?           |
| `storm-M`                | do the rest of the monitors stay on time while 30% of the targets are unreachable |
| `uiload-XS-stock`        | does the default setup (512 MB, 192 MB heap) still work while 5 people use the UI |

- **A ramp** starts with a fresh database, then grows the monitor set along the ladder (N = 1, 5, 15, 40, 50, i.e. 100
  to 5,000 monitors, which is the cap of the benchmark), until a step fails. Then it runs one more step in the middle
  between the last passing and the first failing N, to make the upper bound more precise. The knee and storm blocks
  run at the last passing N of the M ramp (at most 5,000 monitors).
- **A step** regenerates `kuvasz.yml`, restarts the Kuvasz container (Postgres keeps running, with all the history),
  then warms up for 2 minutes and measures for 5. The startup time with N monitors is recorded too.
- **The UI load test** is a normal step, but during the measurement a few emulated users keep opening the dashboard,
  the monitor lists of every type (including the requests that load the lists), a monitor's details page, and the API
  lists. Big responses create a lot of short-lived data in the memory, so this shows if the heap is still big enough.
- **The storm** watches a steady state for 3 minutes, then disconnects the targets of group b from the network for 4
  minutes, then reconnects them and watches the recovery for 3 more.

### What gets measured

- **The timing of the checks**: every check writes a row with its timestamp, and the gap between two checks of a
  monitor should be its interval plus the duration of the check (the next check is scheduled when the previous one
  finished). Anything above that is lag. A check that's late by a whole interval is a missed one.
- **Correctness**: monitors of healthy targets must never go DOWN, monitors of failing targets must go DOWN in time.
- **The push API**: the latency and the errors of the heartbeats, as the pusher sees them.
- **The UI and the API**, as a user sees them: the dashboard (and its periodically refreshed fragment), the HTTP
  monitor list and details pages, and `GET /api/v2/http-monitors`, every 15 seconds.
- **Resources**: CPU usage and CPU throttling (from the cgroup), memory of the containers, the live heap after GC, GC
  overhead and every single garbage collection (from the GC log, so short memory peaks are visible too), the DB
  connection pool (pending connections, acquire time), the executors' queues, Postgres' load, the
  slowest queries, and the growth of the per-check tables, projected to the default 7-day retention.

### When does a step pass?

All of these must hold (the thresholds are in `config/profiles.yml`):

- Kuvasz doesn't die (e.g. OOM killed).
- For every monitor type, the p99 lag stays within 5 seconds (or 10% of the interval, for intervals above 50s), at
  most 1% of the checks are missed, and every monitor gets checked.
- No false DOWN, no undetected DOWN.
- Heartbeats: p99 below 1s, no errors.
- UI/API requests: p95 below 2s, none above 10s, no errors. Reports, which add up the history of many monitors (the
  dashboard's refreshed part, the status pages and the per-type stats API), get p95 below 5s and none above 15s.
- No DB connection timeouts.

A step also gets a warning if a stub target was busy (>80% CPU), since then the target might have been the bottleneck.

## A filled database

A knee or UI load block can load a history before the step (`--history-days`), as if the monitors had been running
for that long, so the run measures an installation that has been in use for a while, instead of an empty one:

```shell
uv run python -m kbench.run uiload --profile S --heap xmx512 --scale 30 --history-days 7
uv run python -m kbench.run uiload --profile S1536-PG4 --heap xmx768 --scale 50 --history-days 30 --ui-period P30D
```

The history follows the behaviors of the targets, like real monitors would have written it
([`kbench/backfill.py`](kbench/backfill.py)): a flapping monitor flaps in episodes (twice a week, for 2 hours), a
healthy one has a 10-minute outage every 2 weeks, and a failing one stays down.

## Disk space

The disk space of the per-check history is measured separately, without running Kuvasz: a scratch Postgres gets the
per-check tables (same columns and indexes as the migrations), filled with the history of N monitors of a mix for the
retention period (the same history as the one of a filled database), then the daily cleanup is replayed a few times,
so the tables reach the size they settle at in a long-running installation.

```shell
uv run python -m kbench.disksize --monitors 1000 --retention-days 7 --out results/disksize.json
uv run python -m kbench.disksize --mix web-agency --monitors 1000 --retention-days 7
```

## Tests

```shell
uv run pytest
```

## Good to know

- On Docker Desktop, every container shares the same VM, so the numbers are indicative rather than exact. The
  collector records the CPU usage of the targets too, to catch the cases where a target, rather than Kuvasz, becomes
  the bottleneck.
- The docker monitors talk to the very same daemon that runs the benchmark, so at large scales its latency is part of
  what's measured.
- Kuvasz evaluates a push monitor only after its first heartbeat, so a `down` push client sends one, then goes silent,
  like a cron job that died.
- A docker monitor writes a per-check row only if its metrics history is on (off by default, on for 30% of them), so
  the timing of the docker checks is measured on that subset.
