# Hardware requirements

How much CPU, memory and disk does _Kuvasz_ need? It mostly depends on **how many monitors** you have. This page gives
you numbers that we measured, and shows the one setting you need to change if you have a lot of monitors.

!!! tip "Having less than 500 monitors?"

    You don't need to worry about any of these details below, just make sure that Kuvasz has **at least 768 MB of memory**, and you'll be fine! The actual usage will be probably significantly lower unless you really have 500 monitors set up, but that's a safe bet for a stable setup.

## Short answer

| Number of monitors | Memory for _Kuvasz_ | Memory setting                     | CPU for _Kuvasz_ | Database (CPU / memory) | Disk for the database (7 days of history) |
|--------------------|---------------------|------------------------------------|------------------|-------------------------|-----------------------|
| up to 500          | 768 MB              | none, the default is enough        | 0.5 CPU          | 0.5 CPU / 512 MB        | about 0.6 GB          |
| up to 3,000        | 1 GB                | `JAVA_TOOL_OPTIONS="-Xmx512M"`     | 1 CPU            | 1 CPU / 1 GB            | about 3.5 GB          |
| up to 5,000        | 1.5 GB              | `JAVA_TOOL_OPTIONS="-Xmx768M"`     | 1 CPU            | 1 CPU / 1 GB            | about 6 GB            |

- **Memory setting** is an environment variable of the _Kuvasz_ container, see [the memory setting](#the-memory-setting)
  below for what it means and where to set it.
- **Memory** means RAM. Swap doesn't count: if _Kuvasz_ starts to use swap, it becomes very slow. Give it more memory
  instead.
- **Disk** is what the database needs to keep the history of 7 days, which is the default. If you keep the history for
  longer, you need more disk, see [disk space](#disk-space) below.
- With **more than 100 monitors**, we recommend turning on
  [virtual thread scheduling](configuration.md#virtual-thread-scheduling). All
  the numbers on this page were measured with it.

!!! tip "Memory, not CPU, is what matters"

    Even with 5,000 monitors, _Kuvasz_ used only about a third of one CPU in our tests. When _Kuvasz_ runs out of
    resources, it's almost always the memory.

## What we measured

In every test below, all the monitors were checked on time, no monitor was reported as down by mistake, and **5 virtual users used the web interface and the API at the same time**.

| Monitors | Memory limit | Memory setting  | Highest memory use | Result                                                           |
|---------:|-------------:|-----------------|-------------------:|------------------------------------------------------------------|
|      500 |       768 MB | default         |             513 MB | :white_check_mark: worked well                                   |
|    1,500 |         1 GB | `-Xmx512M`      |             719 MB | :white_check_mark: worked well                                   |
|    3,000 |         1 GB | `-Xmx512M`      |             857 MB | :white_check_mark: worked well                                   |
|    5,000 |       1.5 GB | `-Xmx768M`      |           1,144 MB | :white_check_mark: worked well                                   |

### What "worked well" means

Each test ran for 2 minutes to warm up, then we measured for 5 minutes. A test worked well only if all of these were
true during the measurement. Some limits use a percentile: "p99 is at most 5 seconds" means that 99 out of 100 values
were at most 5 seconds.

| What we checked                                                                    | Limit                                                     | Worst result in the tests above        |
|------------------------------------------------------------------------------------|-----------------------------------------------------------|----------------------------------------|
| How late the checks ran (compared to their interval), for every monitor type       | p99 at most 5 seconds (10% of the interval above 50 s)    | 2.1 seconds (Docker monitors), all other types below 1 second |
| Skipped checks                                                                     | at most 1 out of 100                                      | none                                   |
| Monitors that were not checked at all                                              | none                                                      | none                                   |
| Healthy targets reported as down                                                   | none                                                      | none                                   |
| Failing targets not reported as down in time                                       | none                                                      | none                                   |
| Pages of the web interface and API requests                                        | p95 at most 2 seconds, none above 10 seconds, no errors   | p95 0.92 seconds, slowest 0.97 seconds |
| Heartbeats of push monitors                                                        | p99 at most 1 second, no errors                           | 0.16 seconds                           |
| Database connection timeouts                                                       | none                                                      | none                                   |
| _Kuvasz_ stopped or restarted                                                      | never                                                     | never                                  |

The slowest page was always the list of HTTP monitors, which gets big with thousands of monitors (see
[long monitor lists](#good-to-know)).

We also tested 3,000 monitors with three other kinds of setups: mostly HTTP monitors, mostly Docker monitors, and
mostly ping, TCP and DNS monitors. All of them worked well with 1 GB of memory. The setup with many ping monitors used
the most memory (946 MB), see [ping monitors](#good-to-know) below.

The database needed little: even with 5,000 monitors, it used less than one fifth of a CPU and about 150 MB of memory.

## Disk space

Most of the disk space goes to the **history of the checks** (the response times and other metrics of every check).
_Kuvasz_ keeps it for 7 days by default, but you can change this with the
[latency data retention](configuration.md#latency-data-retention) setting. The longer you keep it, the more disk you need:

| Number of monitors | 7 days (default) | 30 days      | 90 days      |
|--------------------|------------------|--------------|--------------|
| 500                | about 0.6 GB     | about 2.3 GB | about 7 GB   |
| 3,000              | about 3.5 GB     | about 14 GB  | about 40 GB  |
| 5,000              | about 6 GB       | about 23 GB  | about 67 GB  |

As a rule of thumb: about **150 MB for every 1,000 monitors and every day** of history. Add one more day to the
retention when you calculate, because the old history is deleted only once a day. If most of your monitors are HTTP
monitors, count with about 170 MB instead.

The uptime events (when a monitor went up or down) need very little space, even if you keep them for a year.

The web interface always shows the last 7 days, so keeping a longer history mostly needs more disk, not more memory or
CPU.

## The memory setting

_Kuvasz_ is a Java application. Java keeps most of its data in a part of the memory called the **heap**. By default,
_Kuvasz_ uses a heap of **at most 192 MB**, and this doesn't change when you give the machine more memory. 192 MB is
enough for **about 500 monitors**.

On top of the heap, _Kuvasz_ needs about **300 MB** for other things (its code, the libraries it uses, etc.). That's
why it needs 768 MB of memory even with the default heap.

If you have more than 500 monitors, make the heap bigger, and give the container enough memory:

- **The heap** (the `-Xmx` value): see the [table above](#short-answer).
- **The memory of the container**: at least the heap **plus 500 MB**.

### What `JAVA_TOOL_OPTIONS` is, and where to set it

`JAVA_TOOL_OPTIONS` is an **environment variable** that Java reads when it starts. It contains settings for Java
itself, and `-Xmx` is the one that sets the maximum size of the heap: `-Xmx512M` means 512 MB.

- Set it on the **_Kuvasz_ container**, in the same place where you set its other environment variables (e.g.
  `DATABASE_HOST`), like in the examples below. It's not needed on the database container.
- It **can't** go into the `kuvasz.yml` configuration file, because Java needs it before _Kuvasz_ starts and reads
  that file.
- It takes effect when the container **starts**, so recreate or restart the container after you change it (for
  example with `docker compose up -d`).

=== "Docker Compose"

    ```yaml
    services:
      kuvasz:
        image: kuvaszmonitoring/kuvasz:latest
        mem_limit: 1g
        environment:
          JAVA_TOOL_OPTIONS: "-Xmx512M"
          USE_VIRTUAL_THREAD_SCHEDULING: "true"
          # ... the rest of your settings
    ```

=== "Docker"

    ```bash
    docker run --memory 1g \
      -e JAVA_TOOL_OPTIONS="-Xmx512M" \
      -e USE_VIRTUAL_THREAD_SCHEDULING=true \
      kuvaszmonitoring/kuvasz:latest
    ```

=== "Helm"

    ```yaml
    resources:
      limits:
        cpu: 1000m
        memory: 1Gi
      requests:
        cpu: 100m
        memory: 1Gi

    extraEnv:
      - name: JAVA_TOOL_OPTIONS
        value: "-Xmx512M"
      - name: USE_VIRTUAL_THREAD_SCHEDULING
        value: "true"
    ```

!!! note

    `JAVA_TOOL_OPTIONS` replaces the default value of the image (`-Xms64M -Xmx192M`). That's fine: you only need to set
    the maximum size of the heap (`-Xmx`), Java takes care of the rest.

!!! tip "Checking that the setting is used"

    When the container starts, Java writes the value it found into the log of the container, in its very first line,
    for example: `Picked up JAVA_TOOL_OPTIONS: -Xmx512M`. If you don't see your value there, the environment variable
    didn't reach the container.

### Signs that _Kuvasz_ needs more memory

- The container **stops and restarts by itself**, and Docker or Kubernetes reports it as `OOMKilled` (exit code 137).
- _Kuvasz_ **doesn't start**, or it takes many minutes to start.
- The checks **run late**, and the web interface becomes **slow**.

If you see any of these, give _Kuvasz_ more memory, and make the heap bigger, as shown above.

### More than 5,000 monitors

We tested up to 5,000 monitors. _Kuvasz_ can handle more, but you need to give it more memory: the heap needs about
**60 MB more for every 1,000 monitors**. Raise the heap and the memory of the container together, and watch the memory
use for a few days.

## Good to know

- **Ping monitors**: every ping check starts a small separate program, which needs some extra memory. If you have
  hundreds of ping (ICMP) monitors, add about 256 MB to the memory in the [table above](#short-answer).
- **Restarts on small machines**: with 0.5 CPU, the first checks after a restart can be slow for a minute or so (we saw
  one take 5.5 seconds). If you set a [response time threshold](../management/http-monitors.md#response-time-threshold)
  on your HTTP monitors, such a monitor can be reported as down right after a restart.
- **Very short intervals**: our tests used a realistic mix of check intervals (from 10 seconds to 1 day, most of them
  1 or 5 minutes). If most of your monitors are checked every 10–30 seconds, give _Kuvasz_ and the database more CPU.
- **Long monitor lists**: with thousands of monitors, the monitor list pages of the web interface get big. To keep them
  short and fast, group your monitors with [categories](../management/http-monitors.md#category), and filter the list
  by category.

## How we tested

We ran the official Docker image of _Kuvasz_ 4.5.0 with test sets of 100 to 5,000 monitors. The sets mixed every
monitor type (about half of them HTTP monitors, the rest push, ping, TCP, DNS and Docker monitors), and we repeated the
3,000-monitor test with three other mixes. We measured the disk space separately, with the tables of _Kuvasz_ filled
with the history of 1,000 monitors. Some of the
monitored targets were slow, some were down, and some changed between up and down, like in real life. During the
tests, 5 virtual users used the web interface and the API at the same time.

### The test machine

| What                 | Details                                                                                      |
|----------------------|----------------------------------------------------------------------------------------------|
| Processor            | Apple M1 Pro (64-bit ARM), 10 cores: 8 performance cores and 2 efficiency cores               |
| Memory               | 16 GB                                                                                        |
| Disk                 | SSD                                                                                          |
| Operating system     | macOS 26                                                                                     |
| Docker               | Docker Desktop 29.8, with 8 cores and 12 GB of memory given to its Linux virtual machine      |
| _Kuvasz_             | the official `kuvaszmonitoring/kuvasz:4.5.0` image (`linux/arm64`), Java 25                  |
| Database             | PostgreSQL 18 (`postgres:18-alpine`)                                                         |

_Kuvasz_, the database and the monitored test targets all ran on this computer, each in its own container, with its
own CPU and memory limits (the numbers in the tables above). The containers couldn't use swap.

!!! note "One CPU means one core of the test machine"

    The CPU numbers on this page are cores of the processor above, which is a fast, modern processor. On a slower one
    (for example a Raspberry Pi, or an older server), _Kuvasz_ might need more CPU for the same number of monitors.

You can find the test tools and all the details in the
[`benchmark` folder of the repository](https://github.com/kuvasz-uptime/kuvasz/tree/main/benchmark){target="blank"}.
