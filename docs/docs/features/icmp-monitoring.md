Ping (ICMP) monitoring allows you to check the **reachability and latency** of any host - whether it's a server, a router, a network device, or any other IP-addressable target - by sending ICMP echo requests (pings) to it.

## How does it work?

_Kuvasz_ monitors your hosts by **periodically sending a configurable number of ICMP packets** to them. For each check it measures:

- **Latency** (average round-trip time of the received replies)
- **Packet loss** (percentage of packets that did not receive a reply)

If the measured packet loss meets or exceeds the configured [**packet loss threshold**](../management/icmp-monitors.md#packet-loss-threshold), the monitor is marked as DOWN and you will be **notified** through your configured notification channels.

### What can be configured?

- interval for uptime checks
- hostname or IP address to ping
- number of ICMP packets per check (1-10)
- per-ping timeout (1-30 seconds)
- packet loss threshold that triggers a DOWN event (1-100 %)
- consecutive failure count threshold

## Configuration <!-- md:config ../management/icmp-monitors.md -->

Please refer to the [**Managing ICMP monitors**](../management/icmp-monitors.md) section of the documentation for more information on how to configure ping monitoring.
