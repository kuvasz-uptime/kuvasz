"""Loads a long history into the database of a stopped Kuvasz: the uptime events and the per-check rows of the metrics
history of the last N days, as the monitors of the generated set would have written them (same intervals, same target
behaviors). A run on top of it measures an installation that has been running for a while, instead of an empty one.

The secondary indexes of the per-check tables are dropped during the load and rebuilt afterwards, like a restore of a
dump would do, because inserting 100+ million rows through them would take hours."""

import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone

from kbench import schema
from kbench.generate import flap_period
from kbench.stack import db

# Days loaded in parallel, each through its own connection
WORKERS = 4

# Like the stub targets: fast answers mostly, slow ones for the slow targets
HEALTHY_LATENCY = "(5 + floor(random() * 45))::int"
SLOW_LATENCY = "(300 + floor(random() * 1700))::int"

# The columns written per type, besides monitor_id and created_at. Anything else that is NOT NULL without a default
# would make the load fail, see check_columns()
LOG_COLUMNS = {
    "http": {"latency": "{latency}"},
    "tcp": {"latency_ms": "{latency_or_null}"},
    "icmp": {"latency_ms": "{latency_or_null}", "packet_loss_percentage": "CASE WHEN {up} THEN 0 ELSE 100 END"},
    "dns": {"latency_ms": "{latency_or_null}"},
    "docker": {
        "latency_ms": "{healthy_latency}",
        "cpu_usage_percent": "CASE WHEN {up} THEN round((random() * 50)::numeric, 2) END",
        "memory_usage_bytes": "CASE WHEN {up} THEN (50e6 + random() * 200e6)::bigint END",
        "memory_limit_bytes": "CASE WHEN {up} THEN 1073741824 END",
        "restart_count": "CASE WHEN {up} THEN 0 END",
        "container_created_at": "CASE WHEN {up} THEN timestamptz '2025-12-01' END",
    },
}
EVENT_COLUMNS = {"monitor_id", "status", "error", "started_at", "ended_at", "updated_at"}
DOWN_ERROR = "Reason: Service Unavailable"

# Real monitors don't flap all the time: a flapping monitor of the history flaps in episodes (twice a week, for 2 hours),
# and it's UP between them. The last episode lasts until now, since the flapping stub keeps flapping during the run
FLAP_EPISODE_SECONDS = 2 * 3600
FLAP_EPISODE_EVERY_SECONDS = 302400
# Healthy targets go down now and then too: a 10-minute outage every 2 weeks, but never in the last hour, since the
# stub is healthy when Kuvasz starts
OUTAGE_SECONDS = 600
OUTAGE_EVERY_SECONDS = 14 * 86400
NO_OUTAGE_BEFORE_END_SECONDS = 3600


@dataclass(frozen=True)
class MonitorRow:
    monitor_type: str
    id: int
    interval: int
    behavior: str
    metrics: bool

    @property
    def flap_seconds(self) -> int:
        return flap_period(self.interval)

    @property
    def episode_offset(self) -> int:
        return self.id * 7919 % FLAP_EPISODE_EVERY_SECONDS

    @property
    def outage_offset(self) -> int:
        return self.id * 104729 % OUTAGE_EVERY_SECONDS


def behavior_of(expectation: dict | None) -> str:
    # The real-internet slice and anything unknown is treated as healthy
    behavior = (expectation or {}).get("behavior", "healthy")
    return behavior if behavior in ("healthy", "slow", "flapping", "down", "timeout") else "healthy"


def in_flap_episode(row: MonitorRow, t: int, end: int) -> bool:
    return t >= end - FLAP_EPISODE_SECONDS or (t - row.episode_offset) % FLAP_EPISODE_EVERY_SECONDS < FLAP_EPISODE_SECONDS


def in_outage(row: MonitorRow, t: int, end: int) -> bool:
    return t < end - NO_OUTAGE_BEFORE_END_SECONDS and (t - row.outage_offset) % OUTAGE_EVERY_SECONDS < OUTAGE_SECONDS


def is_down(row: MonitorRow, t: int, end: int) -> bool:
    """Whether the monitor was DOWN at the given epoch second. The phases of a flapping episode are aligned to the
    phases of the flapping stub (UP while floor(epoch / period) is even)."""
    if row.behavior in ("down", "timeout"):
        return True
    if row.behavior == "flapping":
        return in_flap_episode(row, t, end) and (t // row.flap_seconds) % 2 == 1
    return in_outage(row, t, end)


def changes(row: MonitorRow, start: int, end: int) -> list[int]:
    """Every epoch second between start and end where is_down() may change."""
    if row.behavior in ("down", "timeout"):
        return []
    if row.behavior == "flapping":
        every, length, offset = FLAP_EPISODE_EVERY_SECONDS, FLAP_EPISODE_SECONDS, row.episode_offset
    else:
        every, length, offset = OUTAGE_EVERY_SECONDS, OUTAGE_SECONDS, row.outage_offset
    first = start - (start - offset) % every
    windows = [(w, w + length) for w in range(first, end, every)]
    if row.behavior == "flapping":
        windows.append((end - FLAP_EPISODE_SECONDS, end))
        period = row.flap_seconds
        points = {b for w_start, w_end in windows for b in (w_start, w_end)}
        points |= {k * period for w_start, w_end in windows
                   for k in range(w_start // period + 1, (w_end - 1) // period + 1)}
    else:
        points = {b for w in windows for b in w} | {end - NO_OUTAGE_BEFORE_END_SECONDS}
    return sorted(p for p in points if start < p < end)


def events_of(row: MonitorRow, start: int, end: int) -> list[tuple[int, int | None, str]]:
    """(start, end, status) of the UP/DOWN events of a monitor between two epoch seconds. The last one is open (end
    None)."""
    events = []
    for point in [start, *changes(row, start, end)]:
        status = "DOWN" if is_down(row, point, end) else "UP"
        if events and events[-1][2] == status:
            continue
        if events:
            events[-1] = (events[-1][0], point, events[-1][2])
        events.append((point, None, status))
    return events


def up_condition(row_alias: str = "m", ts: str = "ts") -> str:
    """SQL: whether the monitor was UP at the time of the check, matching is_down(). %(history_end)s is the end of the
    history in epoch seconds."""
    t = f"floor(extract(epoch FROM {ts}))::bigint"
    m = row_alias
    flapping_down = (f"({m}.behavior = 'flapping' AND ({t} >= %(history_end)s - {FLAP_EPISODE_SECONDS} "
                     f"OR mod({t} - {m}.episode_offset, {FLAP_EPISODE_EVERY_SECONDS}) < {FLAP_EPISODE_SECONDS}) "
                     f"AND mod(floor({t}::numeric / {m}.flap)::bigint, 2) = 1)")
    outage = (f"({m}.behavior IN ('healthy', 'slow') AND {t} < %(history_end)s - {NO_OUTAGE_BEFORE_END_SECONDS} "
              f"AND mod({t} - {m}.outage_offset, {OUTAGE_EVERY_SECONDS}) < {OUTAGE_SECONDS})")
    return f"({m}.behavior NOT IN ('down', 'timeout') AND NOT {flapping_down} AND NOT {outage})"


def log_insert_sql(monitor_type: str, table: str) -> str:
    """Inserts the per-check rows of one type between two epoch seconds (%(chunk_start)s inclusive, %(chunk_end)s
    exclusive), in time order. Every monitor checks at the multiples of its interval, shifted by its own phase."""
    up = up_condition()
    latency = f"CASE WHEN m.behavior = 'slow' THEN {SLOW_LATENCY} ELSE {HEALTHY_LATENCY} END"
    values = {
        "latency": latency,
        "latency_or_null": f"CASE WHEN {up} THEN {latency} END",
        "healthy_latency": HEALTHY_LATENCY,
        "up": up,
    }
    columns = LOG_COLUMNS[monitor_type]
    names = ", ".join(["monitor_id", "created_at", *columns])
    expressions = ", ".join(["m.id", "ts", *(e.format(**values) for e in columns.values())])
    # HTTP writes a row only for a successful check, the others for every check
    only_up = f"WHERE {up}" if monitor_type == "http" else ""
    return f"""
        INSERT INTO {table} ({names})
        SELECT {expressions}
        FROM (
            SELECT m.*, to_timestamp(k * m.interval_s + m.phase) AS ts
            FROM public.bf_monitor m, generate_series(
                ceil((%(chunk_start)s - m.phase)::numeric / m.interval_s)::bigint,
                floor((%(chunk_end)s - 1 - m.phase)::numeric / m.interval_s)::bigint
            ) k
            WHERE m.monitor_type = '{monitor_type}' AND m.metrics
        ) m
        {only_up}
        ORDER BY ts
    """


def load_monitors(conn, expectations: dict) -> list[MonitorRow]:
    rows = []
    for monitor_type, table in schema.MONITOR_TABLES.items():
        interval = "heartbeat_interval" if monitor_type == "push" else "uptime_check_interval"
        metrics = "false" if monitor_type == "push" else "metrics_history_enabled"
        for monitor_id, name, seconds, has_metrics in conn.execute(
            f"SELECT id, name, {interval}, {metrics} FROM {table}"
        ).fetchall():
            rows.append(MonitorRow(monitor_type, monitor_id, seconds, behavior_of(expectations.get(name)),
                                   has_metrics))
    return rows


def check_columns(conn):
    """Fails early if a table got a required column that the load doesn't know about."""
    tables = {t: {"monitor_id", "created_at", *LOG_COLUMNS[m]} for m, (t, _) in schema.CHECK_LOG_TABLES.items()}
    tables |= {t: EVENT_COLUMNS for t in schema.UPTIME_EVENT_TABLES.values()}
    for table, known in tables.items():
        required = {
            name for (name,) in conn.execute(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'kuvasz' AND table_name = %s AND is_nullable = 'NO' AND column_default IS NULL
                """,
                (table,),
            ).fetchall()
        }
        if unknown := required - known - {"id"}:
            raise RuntimeError(f"{table} has required columns the backfill doesn't fill: {sorted(unknown)}")


def secondary_indexes(conn, tables: list[str]) -> list[tuple[str, str]]:
    return conn.execute(
        """
        SELECT i.indexname, i.indexdef FROM pg_indexes i
        JOIN pg_index x ON x.indexrelid = format('%%I.%%I', i.schemaname, i.indexname)::regclass
        WHERE i.schemaname = 'kuvasz' AND i.tablename = ANY(%s) AND NOT x.indisprimary
        """,
        (tables,),
    ).fetchall()


def insert_events(conn, monitors: list[MonitorRow], start: int, end: int) -> int:
    count = 0
    for monitor_type, table in schema.UPTIME_EVENT_TABLES.items():
        rows = []
        for row in (m for m in monitors if m.monitor_type == monitor_type):
            for phase_start, phase_end, status in events_of(row, start, end):
                rows.append((row.id, status, DOWN_ERROR if status == "DOWN" else None,
                             ts(phase_start), ts(phase_end) if phase_end else None, ts(phase_end or end)))
        with conn.cursor() as cursor, cursor.copy(
            f"COPY {table} (monitor_id, status, error, started_at, ended_at, updated_at) FROM STDIN"
        ) as copy:
            for record in rows:
                copy.write_row(record)
        count += len(rows)
    return count


def ts(epoch: int) -> datetime:
    return datetime.fromtimestamp(epoch, tz=timezone.utc)


def chunks(start: int, end: int, size: int = 86400) -> list[tuple[int, int]]:
    return [(chunk_start, min(chunk_start + size, end)) for chunk_start in range(start, end, size)]


def write_monitor_table(conn, monitors: list[MonitorRow]):
    """public.bf_monitor: the monitors with everything log_insert_sql() needs to know about them."""
    conn.execute("""CREATE UNLOGGED TABLE public.bf_monitor (monitor_type text, id bigint, interval_s int,
                    phase int, behavior text, flap int, episode_offset int, outage_offset int, metrics boolean)""")
    with conn.cursor() as cursor, cursor.copy("COPY public.bf_monitor FROM STDIN") as copy:
        for m in monitors:
            # The checks of a monitor are spread like the scheduler spreads them: a fixed offset per monitor
            copy.write_row((m.monitor_type, m.id, m.interval, m.id * 7919 % m.interval, m.behavior,
                            m.flap_seconds, m.episode_offset, m.outage_offset, m.metrics))


def load_chunk(chunk: tuple[int, int], statements: list[str], history_end: int):
    with db() as conn:
        conn.execute("SET synchronous_commit = off")
        for statement in statements:
            conn.execute(statement, {"chunk_start": chunk[0], "chunk_end": chunk[1], "history_end": history_end})


def run(expectations: dict, days: int, log=print) -> dict:
    """Replaces the history of every monitor with `days` days of it, ending now. Kuvasz must not be running."""
    started = time.monotonic()
    end = int(time.time())
    start = end - days * 86400
    log_tables = [t for t, _ in schema.CHECK_LOG_TABLES.values()]
    with db() as conn:
        check_columns(conn)
        monitors = load_monitors(conn, expectations)
        conn.execute(f"TRUNCATE {', '.join(log_tables + list(schema.UPTIME_EVENT_TABLES.values()))}")
        # The monitors were only just imported, but they have been around for as long as their history
        for table in schema.MONITOR_TABLES.values():
            conn.execute(f"UPDATE {table} SET created_at = %s", (ts(start),))
        conn.execute("DROP TABLE IF EXISTS public.bf_monitor")
        write_monitor_table(conn, monitors)
        indexes = secondary_indexes(conn, log_tables)
        for name, _ in indexes:
            conn.execute(f"DROP INDEX kuvasz.{name}")
        conn.execute("ALTER SYSTEM SET max_wal_size = '8GB'")
        conn.execute("SELECT pg_reload_conf()")
        events = insert_events(conn, monitors, start, end)
        log(f"  backfill: {events:,} uptime events")

    statements = [log_insert_sql(t, table) for t, (table, _) in schema.CHECK_LOG_TABLES.items()]
    days_to_load = chunks(start, end)
    with ThreadPoolExecutor(WORKERS) as pool:
        for done, _ in enumerate(pool.map(lambda c: load_chunk(c, statements, end), days_to_load), start=1):
            log(f"  backfill: day {done}/{len(days_to_load)} of the metrics loaded")

    with db() as conn:
        conn.execute("SET maintenance_work_mem = '1GB'")
        conn.execute("SET max_parallel_maintenance_workers = 3")
        for name, definition in indexes:
            log(f"  backfill: rebuilding {name}")
            conn.execute(definition)
        conn.execute("DROP TABLE public.bf_monitor")
        conn.execute("ALTER SYSTEM RESET max_wal_size")
        conn.execute("SELECT pg_reload_conf()")
        for table in log_tables + list(schema.UPTIME_EVENT_TABLES.values()):
            conn.execute(f"VACUUM ANALYZE {table}")
        sizes = {
            table: {"rows": conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0],
                    "mb": round(conn.execute("SELECT pg_total_relation_size(%s)", (table,)).fetchone()[0] / 2**20)}
            for table in log_tables + list(schema.UPTIME_EVENT_TABLES.values())
        }
        database_mb = round(conn.execute("SELECT pg_database_size(current_database())").fetchone()[0] / 2**20)
    window_start = end - 7 * 86400
    return {
        "days": days,
        "start": ts(start).isoformat(),
        "end": ts(end).isoformat(),
        "uptime_events": events,
        "uptime_events_in_last_7_days": sum(
            1 for m in monitors for s, e, _ in events_of(m, start, end) if e is None or e > window_start),
        "tables": sizes,
        "database_mb": database_mb,
        "seconds": round(time.monotonic() - started),
    }

