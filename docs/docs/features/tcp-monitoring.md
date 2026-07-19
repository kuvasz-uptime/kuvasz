TCP port monitoring allows you to check whether a **TCP service is accepting connections** - a database, an SMTP or SSH server, a message broker, a game server, or any other host:port endpoint - by opening a TCP connection to it.

## How does it work?

_Kuvasz_ monitors your services by **periodically opening a TCP connection** to the configured host and port. For each check it measures:

- **Reachability** (whether the connection could be established within the timeout)
- **Connect latency** (the time it took to establish the connection)

If the connection cannot be established within the configured [**timeout**](../management/tcp-monitors.md#timeout), or - when configured - the connection takes longer than the optional [**latency threshold**](../management/tcp-monitors.md#latency-threshold), the monitor is marked as DOWN and you will be **notified** through your configured notification channels.

These two settings cover **different failure modes** and are meant to complement each other. The timeout is a hard ceiling that decides whether the port is **reachable at all**, while the latency threshold is a lower bar (set well below the timeout) that flags connections which succeed but are **too slow**. For example, with a `5000` ms timeout and a `500` ms latency threshold, a connect that takes 800 ms is still reachable but breaches your latency SLA, so the monitor is marked DOWN. Leave the latency threshold unset if you only care about reachability.

### What can be configured?

- interval for uptime checks
- hostname or IP address to connect to
- TCP port to connect to (1-65535)
- connection timeout (1-30000 milliseconds)
- optional connect-latency threshold that triggers a DOWN event
- consecutive failure count threshold

## Configuration <!-- md:config ../management/tcp-monitors.md -->

Please refer to the [**Managing TCP monitors**](../management/tcp-monitors.md) section of the documentation for more information on how to configure TCP port monitoring.
