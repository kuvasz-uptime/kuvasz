When the **host running _Kuvasz_ itself** loses its outbound network access — an ISP blip, a router reboot, a restarted _Docker_ network, a flapping VPN, a _Kubernetes_ node losing egress — every single HTTP, ICMP, TCP and DNS monitor fails at the very same moment. The result is an **incident storm** across all of your integrations, a matching **recovery storm** a few minutes later, and **uptime percentages that stay polluted forever**, all of it caused by a problem that has nothing to do with the services you actually monitor.

The **connectivity check** is the answer to that. _Kuvasz_ periodically dials a few well-known endpoints, and while **none of them** can be reached, it **suspends the checks it initiates on its own**, exactly the way a [**maintenance window**](maintenance-windows.md) does — no checks, no events, no alerts.

!!!warning "It's disabled by default, and that's on purpose"

    _Kuvasz_ is often run on **egress-filtered or LAN-only networks**, where the default targets can never be reached. Enabling the feature there would make _Kuvasz_ **silently stop monitoring everything**, which is far worse than the alert storm it prevents. Turn it on deliberately, and if your network filters outbound traffic, [**point it at targets you know are reachable**](../setup/configuration.md#connectivity-check-targets).

## How does it work? <!-- md:config ../setup/configuration.md#connectivity-check -->

Every [**interval**](../setup/configuration.md#connectivity-check-interval) _Kuvasz_ opens a plain **TCP connection** to each [**configured target**](../setup/configuration.md#connectivity-check-targets), and **the first one that answers wins** — a single reachable target is enough to consider the connectivity healthy. No data is sent, and nothing is read: the handshake alone is the whole probe.

The two directions are deliberately **asymmetric**:

- **Losing** the connectivity has to be **confirmed**. When a round fails, it is retried **two more times, one second apart**, and only if **all three rounds** fail is the state flipped to `DOWN`. A single dropped packet or a momentary interface flap is not enough to suspend your entire monitoring.
- **Regaining** it is **immediate**. One successful round restores the state to `UP` and the suspended checks resume on their next tick, without a restart.

The state is also **primed at startup**, before the first check of any monitor is scheduled, so an instance that **boots during an outage** doesn't emit a burst of false events in its first interval. That initial probe skips the confirmation retries to keep the startup fast, and it corrects itself at the next probe anyway.

!!!tip "Be aware of how long a probe can take"

    While the connectivity is lost, a confirmed probe takes up to `3 × targets × timeout + 2s` — about **32 seconds** with the default settings. The probes can never pile up (a tick is skipped while the previous probe is still running), so this only means that during an outage the **effective interval stretches** a bit, and the recovery is detected within `interval + one successful round` instead of exactly `interval`.

## What happens while the connectivity is lost?

For every monitor that **hasn't opted out**:

- **Uptime checks are skipped entirely.** HTTP, ICMP, TCP and DNS checks don't run at all, which means **no uptime record, no latency measurement, no events and no notifications** — just like during a maintenance window, the history simply has a gap for that period.
- **SSL checks are postponed**, not skipped. Since they run once a day, skipping one could defer a certificate check by a whole day, so instead they are pushed out by 30 minutes and retried until the connectivity is back.
- **The missed heartbeat detection of push monitors is skipped**, so a cron job that couldn't reach _Kuvasz_ during the outage isn't reported as DOWN.

!!!info "Incoming push signals are never blocked"

    The connectivity check only suspends the checks _Kuvasz_ runs **on its own**. If a [**push (heartbeat) monitor**](push-monitoring.md) actively reports its status — a heartbeat ping or an explicit failure signal — that signal is **still recorded and processed as usual**, even while the connectivity is considered lost. A client that is actively talking to _Kuvasz_ is reachable by definition.

!!!note "There is no automatic disarm"

    A "resume everything after N hours of continuous outage" valve sounds like a safety net, but it would produce **exactly the alert storm** this feature exists to prevent, long after you stopped watching. The suspended state therefore stays in place until the connectivity is actually restored, and stays **visible** for as long as it lasts.

## Opting out per monitor

"Does the host have internet access?" is simply **the wrong question** for a monitor that targets `192.168.1.10` or an internal service in the same cluster. Those keep working during an internet outage, and suspending them would **hide a real, local failure**.

That's what [**`ignoreConnectivityCheck`**](../management/http-monitors.md#ignore-connectivity-check) is for. It's available on **every monitor type**, and a monitor with it enabled **keeps being checked** no matter what the global connectivity state is.

## How do I know that the checks are suspended?

The suspended state is **never silent**:

- A **yellow warning indicator** appears in the header of the Web UI, next to the version badge. Clicking it takes you straight to the **Settings** page, which shows the current state, the configured targets, when the connectivity was last probed, since when it is considered lost, and the error of the last failed dial.
- The very same information is exposed on the [**API**](api.md), in the `connectivityCheck` field of `GET /api/v2/settings` (the field is `null` when the feature is disabled).
- A **`WARN` log entry** is written when the connectivity is lost, and an **`INFO`** one — including the length of the outage — when it is regained.

!!!info "The health endpoint stays UP"

    The [**health endpoint**](../setup/installation.md#readinesshealth-probes) intentionally **does not** report the connectivity state. It backs the shipped _Docker_ healthcheck and the _Helm_ liveness probe, so reporting `DOWN` there would restart your container exactly when the instance has to stay alive. Use the **Settings API** above instead.
