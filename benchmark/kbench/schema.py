"""Where Kuvasz keeps the state of each monitor type (schema `kuvasz`)."""

MONITOR_TABLES = {
    "http": "http_monitor",
    "push": "push_monitor",
    "tcp": "tcp_monitor",
    "icmp": "icmp_monitor",
    "dns": "dns_monitor",
    "docker": "docker_monitor",
}
UPTIME_EVENT_TABLES = {t: f"{t}_uptime_event" for t in MONITOR_TABLES}
# One row per executed check: written only if the metrics/latency history of the monitor is on (the default for every
# type but docker). HTTP writes a row only for successful checks, the others for every check.
CHECK_LOG_TABLES = {
    "http": ("http_latency_log", "latency"),
    "tcp": ("tcp_metrics_log", "latency_ms"),
    "icmp": ("icmp_metrics_log", "latency_ms"),
    "dns": ("dns_metrics_log", "latency_ms"),
    "docker": ("docker_metrics_log", "latency_ms"),
}


def current_statuses(conn) -> dict[str, dict]:
    """The open uptime event (status + error) of every monitor, by monitor name. Monitors without any event yet
    (never checked) are missing from the result."""
    statuses = {}
    for monitor_type, monitor_table in MONITOR_TABLES.items():
        rows = conn.execute(
            f"""
            SELECT m.name, e.status::text, e.error, e.started_at
            FROM {monitor_table} m
            JOIN {UPTIME_EVENT_TABLES[monitor_type]} e ON e.monitor_id = m.id AND e.ended_at IS NULL
            """
        ).fetchall()
        for name, status, error, started_at in rows:
            statuses[name] = {"type": monitor_type, "status": status, "error": error, "since": started_at}
    return statuses


def check_log_counts(conn) -> dict[str, int]:
    return {
        monitor_type: conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0]
        for monitor_type, (table, _) in CHECK_LOG_TABLES.items()
    }
