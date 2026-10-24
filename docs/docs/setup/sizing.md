# Hardware requirements

How much CPU, memory and disk does _Kuvasz_ need? It mostly depends on **how many monitors** you have. This page gives
you numbers that we measured, and shows the one setting you need to change if you have a lot of monitors.

!!! tip "Having up to 1,000 monitors?"

    You don't need to worry about any of these details below, just make sure that Kuvasz has **at least 768 MB of memory** and **1 CPU**, and you'll be fine! The actual usage will be probably significantly lower unless you really have that many monitors set up, but that's a safe bet for a stable setup.

## Short answer

| Number of monitors | Memory for _Kuvasz_ | Memory setting                     | Memory for the database |
|--------------------|---------------------|------------------------------------|-------------------------|
| up to 1,000        | 768 MB              | none, the default is enough        | 512 MB                  |
| up to 1,500        | 1 GB                | `JAVA_TOOL_OPTIONS="-Xmx384M"`     | 512 MB                  |
| up to 3,000        | 1 GB                | `JAVA_TOOL_OPTIONS="-Xmx512M"`     | 1 GB                    |
| up to 5,000        | 1.5 GB              | `JAVA_TOOL_OPTIONS="-Xmx768M"`     | 1 GB                    |

- **Memory setting** is an environment variable of the _Kuvasz_ container, see [the memory setting](#the-memory-setting)
  below for what it means and where to set it.
- **Memory** means RAM. Swap doesn't count: if _Kuvasz_ starts to use swap, it becomes very slow. Give it more memory
  instead.
- **CPU**: _Kuvasz_ needs little CPU on average, and the database even less. We recommend giving _Kuvasz_ at least
  1 CPU anyway, because it needs more for short times: when it starts, and when someone opens the dashboard or a status
  page. On a slow machine, like a Raspberry Pi or a NAS, give _Kuvasz_ and the database more CPU if they start slowly,
  or if the pages of long periods load slowly.
- **Memory for the database** is what the database had in our tests, with 30 days of history in it, while the users
  looked at the last 30 days on the dashboard, the incidents and the status pages, see
  [keeping the history for longer](#keeping-the-history-for-longer).

!!! tip "Memory is what matters"

    When _Kuvasz_ runs out of resources, it's almost always the memory.

## What we measured

Before every test, we filled the database with 30 days of history of the monitors, as if they had been running for a
month (see [how we tested](#how-we-tested)). The CPU numbers below are cores of our test machine (an Apple M1 Pro,
a fast processor), so they show how little CPU _Kuvasz_ needed, not how much your machine needs. In every test below,
all the monitors were checked on time, no monitor was reported as down by mistake, and **5 virtual users used the web
interface and the API at the same time**, looking at the last 30 days on the dashboard, the incidents, the status pages
and in the API, and at the last 24 hours on the charts of the monitors.

| Monitors | Memory limit | Memory setting | CPU     | Database         | Highest memory use | Result                         |
|---------:|-------------:|----------------|---------|------------------|-------------------:|--------------------------------|
|      500 |       768 MB | default        | 0.5 CPU | 0.5 CPU / 512 MB |             522 MB | :white_check_mark: worked well |
|    1,000 |       768 MB | default        | 1 CPU   | 0.5 CPU / 512 MB |             524 MB | :white_check_mark: worked well |
|    1,500 |         1 GB | `-Xmx384M`     | 1 CPU   | 0.5 CPU / 512 MB |             713 MB | :white_check_mark: worked well |
|    3,000 |         1 GB | `-Xmx512M`     | 1 CPU   | 1 CPU / 1 GB     |             801 MB | :white_check_mark: worked well |
|    5,000 |       1.5 GB | `-Xmx768M`     | 1 CPU   | 1 CPU / 1 GB     |           1,138 MB | :white_check_mark: worked well |

### What "worked well" means

Each test ran for 2 minutes to warm up, then we measured for 5 minutes. A test worked well only if all of these were
true during the measurement. Some limits use a percentile: "p99 is at most 5 seconds" means that 99 out of 100 values
were at most 5 seconds.

| What we checked                                                                    | Limit                                                     | Worst result in the tests above        |
|------------------------------------------------------------------------------------|-----------------------------------------------------------|----------------------------------------|
| How late the checks ran (compared to their interval), for every monitor type       | p99 at most 5 seconds (10% of the interval above 50 s)    | 2.4 seconds (Docker monitors), all other types below 1.5 seconds |
| Skipped checks                                                                     | at most 1 out of 100                                      | none                                   |
| Monitors that were not checked at all                                              | none                                                      | none                                   |
| Healthy targets reported as down                                                   | none                                                      | none                                   |
| Failing targets not reported as down in time                                       | none                                                      | none                                   |
| Pages of the web interface and API requests                                        | p95 at most 2 seconds, none above 10 seconds, no errors   | less than 1.6 seconds                  |
| The dashboard, the status pages and the statistics of the API, which add up the history of many monitors | p95 at most 5 seconds, none above 15 seconds, no errors | 2.0 seconds |
| Heartbeats of push monitors                                                        | p99 at most 1 second, no errors                           | 0.2 seconds                            |
| Database connection timeouts                                                       | none                                                      | none                                   |
| _Kuvasz_ stopped or restarted                                                      | never                                                     | never                                  |

We also tested 3,000 monitors with three other kinds of setups: mostly HTTP monitors, mostly Docker monitors, and
mostly ping, TCP and DNS monitors. All of them worked well with 1 GB of memory, and used about the same amount of it
(857 to 870 MB).

CPU was never the problem: even with 5,000 monitors, _Kuvasz_ used about a third of its CPU on average. The database
needed even less: less than a fifth of its CPU on average (with short peaks to about 60%), and less than 400 MB of
memory.

## Disk space for the DB

Most of the disk space goes to the **history of the checks** (the response time and the other metrics of every
check). _Kuvasz_ keeps it for 7 days by default, but you can change this with the
[latency data retention](configuration.md#latency-data-retention) setting.

How much space it needs depends on **how often your monitors are checked**, not only on how many monitors you have:
every check takes about **200 bytes** on the disk, and a check of a Docker monitor about **330 bytes**.

The uptime events (when a monitor went up or down) need very little space, even if you keep them for a year.

### How to calculate it

A day has 86,400 seconds, so a monitor that is checked every $t$ seconds is checked $\frac{86{,}400}{t}$ times a day.
If you keep the history for $d$ days, and a check takes $b$ bytes, one monitor needs:

$$
\text{disk space} = \frac{86{,}400}{t} \times (d + 1) \times b
$$

We count **one more day** than $d$, because the old history is deleted only once a day: right before this cleanup, one
more day is stored.

For example, a monitor that is checked every minute ($t = 60$), with the default 7 days ($d = 7$) and 200 bytes per
check ($b = 200$), needs:

$$
\frac{86{,}400}{60} \times (7 + 1) \times 200 = 1{,}440 \times 8 \times 200 = 2{,}304{,}000 \text{ bytes} \approx 2.3
\text{ MB}
$$

So 1,000 monitors checked every minute need about **2.3 GB**. If your monitors are checked at different intervals,
calculate each group, then add them up. For example, 200 monitors checked every 30 seconds, 700 every minute and 100
every 5 minutes, with the default 7 days:

$$
\begin{aligned}
200 \times \frac{86{,}400}{30} \times 8 \times 200 &\approx 922 \text{ MB} \\[6pt]
700 \times \frac{86{,}400}{60} \times 8 \times 200 &\approx 1{,}613 \text{ MB} \\[6pt]
100 \times \frac{86{,}400}{300} \times 8 \times 200 &\approx 46 \text{ MB} \\[6pt]
\text{total} &\approx 2.6 \text{ GB}
\end{aligned}
$$

Here, 1 MB is 1,000,000 bytes, and 1 GB is 1,000 MB. The same calculation for one monitor (with 200 bytes per check):

| Check interval | Per day  | 7 days (default) | 30 days | 90 days |
|----------------|----------|------------------|---------|---------|
| 10 seconds     | 1.7 MB   | 14 MB            | 54 MB   | 157 MB  |
| 30 seconds     | 0.58 MB  | 4.6 MB           | 18 MB   | 52 MB   |
| 1 minute       | 0.29 MB  | 2.3 MB           | 8.9 MB  | 26 MB   |
| 5 minutes      | 0.06 MB  | 0.46 MB          | 1.8 MB  | 5.2 MB  |
| 15 minutes     | 0.02 MB  | 0.15 MB          | 0.6 MB  | 1.7 MB  |

- **Push monitors** don't have such a history.
- HTTP monitors don't store anything for a failed check, so a monitor that is down needs less.

### Where the numbers come from

The 200 and 330 bytes come from our tests (see [how we tested](#how-we-tested)): a check took 196 to 211 bytes,
depending on the type of the monitor, and a check of a Docker monitor 314 to 327 bytes. This includes everything that
the database stores for a check, and also the space that stays in the files after the daily cleanup.

In these tests, most monitors were checked every minute, some every 10 to 30 seconds, and some every 5 to 15 minutes.
With this mix, 1,000 monitors needed about **236 MB a day** (about 1.9 GB for 7 days), or about 270 MB a day if most of
them were HTTP monitors. The calculation above gives almost the same: in a day, the 1,000 monitors of the first mix
stored about 1,090,000 checks of other monitors and about 54,000 checks of Docker monitors, and
$1{,}090{,}000 \times 200 \text{ bytes} + 54{,}000 \times 330 \text{ bytes} \approx 236 \text{ MB}$.

### Keeping the history for longer

With a longer history, you can also look at longer periods in the web interface and the API, for example the last 30
days on the dashboard or on the charts of a monitor. These views read more data, and almost all of this extra work is
done by the database.

Every test above was run with **30 days of history**, while the virtual users looked at the last 30 days on the
dashboard, the incidents, the status pages and in the API, so the numbers on this page already cover keeping the
history for 30 days. A chart never has more than
about 1,000 points, however long the period is, but the database still reads every check of the period to make them,
so a long period of a monitor that is checked every few seconds takes longer to load. In these tests, `shared_buffers` (a
PostgreSQL setting for the memory of its cache) was set to a quarter of the memory of the database, for example `256MB`
for 1 GB. With the official PostgreSQL image, you can set it in the command of the container, for example
`command: postgres -c shared_buffers=256MB`.

If you keep the history for longer than 30 days, or a lot of people look at long periods at the same time, watch the
CPU use of the database: if it's often at its limit, give it more.

## The memory setting

_Kuvasz_ is a Java application. Java keeps most of its data in a part of the memory called the **heap**. By default,
_Kuvasz_ uses a heap of **at most 192 MB**, and this doesn't change when you give the machine more memory. 192 MB is
enough for **about 1,000 monitors**.

On top of the heap, _Kuvasz_ needs about **330 MB** for other things (its code, the libraries it uses, etc.). That's
why it needs 768 MB of memory even with the default heap.

If you have more than 1,000 monitors, make the heap bigger, and give the container enough memory:

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
          # ... the rest of your settings
    ```

=== "Docker"

    ```bash
    docker run --memory 1g \
      -e JAVA_TOOL_OPTIONS="-Xmx512M" \
      kuvaszmonitoring/kuvasz:latest
    ```

=== "Helm"

    ```yaml
    resources:
      limits:
        cpu: 2000m
        memory: 1Gi
      requests:
        cpu: 100m
        memory: 1Gi

    extraEnv:
      - name: JAVA_TOOL_OPTIONS
        value: "-Xmx512M"
    ```

!!! note

    `JAVA_TOOL_OPTIONS` replaces the default value of the image (`-Xms64M -Xmx192M`). That's fine: you only need to set
    the maximum size of the heap (`-Xmx`), Java takes care of the rest.

!!! tip "Checking that the setting is used"

    When the container starts, Java writes the value it found into the log of the container, in one of its first lines,
    for example: `Picked up JAVA_TOOL_OPTIONS: -Xmx512M`. If you don't see your value there, the environment variable
    didn't reach the container.

### Signs that _Kuvasz_ needs more memory

- The container **stops and restarts by itself**, and Docker or Kubernetes reports it as `OOMKilled` (exit code 137).
- _Kuvasz_ **doesn't start**, or it takes many minutes to start.
- The checks **run late**, and the web interface becomes **slow**.

If you see any of these, give _Kuvasz_ more memory, and make the heap bigger, as shown above.

### More than 5,000 monitors

We tested up to 5,000 monitors. _Kuvasz_ can handle more, but you need to give it more memory: the heap needs about
**65 MB more for every 1,000 monitors**. Raise the heap and the memory of the container together, and watch the memory
use for a few days.

## Good to know

- **Restarts on small machines**: on our test machine, _Kuvasz_ needed about 50 seconds to start with 0.5 CPU and 500
  monitors, and 20 to 35 seconds with 1 CPU and more monitors. On a slower machine, it takes longer. Its CPU is busy
  while it starts, so if you set a
  [response time threshold](../management/http-monitors.md#response-time-threshold) on your HTTP monitors, such a
  monitor might be reported as down right after a restart.
- **Very short intervals**: our tests used a realistic mix of check intervals (from 10 seconds to 1 day, most of them
  1 or 5 minutes). If most of your monitors are checked every 10–30 seconds, give _Kuvasz_ and the database more CPU.
- **Status pages with a lot of monitors**: the [default status page](../management/status-pages.md#the-default-status-page)
  shows every enabled monitor, so it's only available up to 100 monitors. With more, create
  [custom status pages](../management/status-pages.md#custom-status-pages) with selected monitors or categories. Every
  status page lists only the latest 20 incidents of each monitor, so a monitor that goes up and down all the time
  doesn't make the page too big.

## How we tested

We ran _Kuvasz_ 4.6.0 with test sets of 500 to 5,000 monitors. The sets mixed every monitor type (about half of them
HTTP monitors, the rest push, ping, TCP, DNS and Docker monitors), and we repeated the 3,000-monitor test with three
other mixes. Some of the monitored targets were slow, some were down, and some changed between up and down, like in
real life. There was one status page with 50 monitors. During the tests, 5 virtual users used the web interface and
the API at the same time.

Before every test, we filled the database with 30 days of history of the monitors, as if they had been running for a
month, and the virtual users looked at the last 30 days on the dashboard, the incidents, the status pages and in the
API, and at the last 24 hours on the charts of the monitors. The history was like in real life too: most monitors were up all the
time, with a 10-minute outage every two weeks, a few were down, and a few went up and down for 2 hours twice a week.
With 5,000 monitors and 30 days, the database was about 23 GB.

We measured the disk space separately, with the tables of _Kuvasz_ filled with the same kind of history of 1,000
monitors.

### The test machine

| What                 | Details                                                                                      |
|----------------------|----------------------------------------------------------------------------------------------|
| Processor            | Apple M1 Pro (64-bit ARM), 10 cores: 8 performance cores and 2 efficiency cores               |
| Memory               | 16 GB                                                                                        |
| Disk                 | SSD                                                                                          |
| Operating system     | macOS 26                                                                                     |
| Docker               | Docker Desktop 29.8, with 8 CPUs and about 10 GB of memory                                   |
| _Kuvasz_             | 4.6.0 (`linux/arm64`), Java 25                                                               |
| Database             | PostgreSQL 18 (`postgres:18-alpine`), `shared_buffers` set to a quarter of its memory         |

_Kuvasz_, the database and the monitored test targets all ran on this computer, each in its own container, with its
own CPU and memory limits (the numbers in the tables above). The containers couldn't use swap.

You can find the test tools and all the details in the
[`benchmark` folder of the repository](https://github.com/kuvasz-uptime/kuvasz/tree/main/benchmark){target="blank"}.
