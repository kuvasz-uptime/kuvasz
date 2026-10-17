"""Measures how much disk the per-check history takes, without running Kuvasz: a scratch Postgres gets the per-check
tables of Kuvasz (same columns, same indexes), filled with the rows of N monitors for the retention period, then the
daily cleanup is replayed a few times (delete the oldest day, vacuum, insert a new day), so the tables reach the size
they settle at in a long-running installation."""

import argparse
import json
import subprocess
import time
from pathlib import Path

CONTAINER = "kb-disksize"
IMAGE = "postgres:18-alpine"

# The per-check tables as the migrations leave them (model/src/main/resources/db/migration), without the foreign keys,
# which don't take any space
DDL = """
CREATE TABLE http_latency_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX http_latency_log_monitor_id_created_at_idx ON http_latency_log USING btree (monitor_id, created_at);
CREATE INDEX http_latency_log_created_at_idx ON http_latency_log USING btree (created_at);

CREATE TABLE tcp_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX tcp_metrics_log_monitor_id_created_at_idx ON tcp_metrics_log USING btree (monitor_id, created_at);
CREATE INDEX tcp_metrics_log_created_at_idx ON tcp_metrics_log USING btree (created_at);

CREATE TABLE icmp_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, packet_loss_percentage INT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX icmp_metrics_log_monitor_id_created_at_idx ON icmp_metrics_log USING btree (monitor_id, created_at);
CREATE INDEX icmp_metrics_log_created_at_idx ON icmp_metrics_log USING btree (created_at);

CREATE TABLE dns_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX dns_metrics_log_monitor_id_created_at_idx ON dns_metrics_log USING btree (monitor_id, created_at);
CREATE INDEX dns_metrics_log_created_at_idx ON dns_metrics_log USING btree (created_at);

CREATE TABLE docker_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, cpu_usage_percent NUMERIC(7, 2) NULL,
    memory_usage_bytes BIGINT NULL, memory_limit_bytes BIGINT NULL, created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    restart_count INT NULL, container_created_at TIMESTAMPTZ NULL
);
CREATE INDEX docker_metrics_log_monitor_id_created_at_idx ON docker_metrics_log USING btree (monitor_id, created_at);
CREATE INDEX docker_metrics_log_created_at_idx ON docker_metrics_log USING btree (created_at);
"""

# Measured with the `balanced` mix (see the results of the campaign): rows per second per 1,000 monitors, and the
# number of monitors per 1,000 that write them (docker: only the 30% with metrics history)
ROWS_PER_SECOND_PER_1000 = {"http": 8.15, "tcp": 1.73, "icmp": 1.93, "dns": 0.54, "docker": 0.6}
MONITORS_PER_1000 = {"http": 450, "tcp": 100, "icmp": 100, "dns": 100, "docker": 30}
TABLES = {
    "http": "http_latency_log",
    "tcp": "tcp_metrics_log",
    "icmp": "icmp_metrics_log",
    "dns": "dns_metrics_log",
    "docker": "docker_metrics_log",
}
# Mostly fast responses, some slow ones, like the targets of the benchmark
_LATENCY = "(CASE WHEN random() < 0.9 THEN 5 + floor(random() * 45) ELSE 300 + floor(random() * 1700) END)::int"


def rows_per_day(monitor_type: str, monitors: int) -> int:
    return round(ROWS_PER_SECOND_PER_1000[monitor_type] * monitors / 1000 * 86400)


def insert_day_sql(monitor_type: str, monitors: int, day: int) -> str:
    """Inserts the rows of one day (day 0 is the oldest), spread evenly over the day, in time order, like the checks
    of many monitors running side by side would write them."""
    rows = rows_per_day(monitor_type, monitors)
    monitor_count = max(1, round(MONITORS_PER_1000[monitor_type] * monitors / 1000))
    timestamp = f"timestamptz '2026-01-01' + make_interval(days => {day}) + (g * interval '86400 seconds' / {rows})"
    monitor_id = f"1 + floor(random() * {monitor_count})::bigint"
    columns, values = {
        "http": ("monitor_id, latency, created_at", f"{monitor_id}, {_LATENCY}, {timestamp}"),
        "tcp": ("monitor_id, latency_ms, created_at", f"{monitor_id}, {_LATENCY}, {timestamp}"),
        "dns": ("monitor_id, latency_ms, created_at", f"{monitor_id}, {_LATENCY}, {timestamp}"),
        "icmp": ("monitor_id, latency_ms, packet_loss_percentage, created_at",
                 f"{monitor_id}, {_LATENCY}, 0, {timestamp}"),
        "docker": ("monitor_id, latency_ms, cpu_usage_percent, memory_usage_bytes, memory_limit_bytes, created_at, "
                   "restart_count, container_created_at",
                   f"{monitor_id}, {_LATENCY}, round((random() * 100)::numeric, 2), "
                   f"(random() * 1e9)::bigint, 1073741824, {timestamp}, 0, timestamptz '2025-12-01'"),
    }[monitor_type]
    return f"INSERT INTO {TABLES[monitor_type]} ({columns}) SELECT {values} FROM generate_series(0, {rows - 1}) g;"


def delete_day_sql(day: int) -> str:
    """The daily cleanup of Kuvasz: everything older than the retention period goes."""
    limit = f"timestamptz '2026-01-01' + make_interval(days => {day + 1})"
    return " ".join(f"DELETE FROM {table} WHERE created_at < {limit};" for table in TABLES.values())


def psql(sql: str) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1", "-At", "-F", "\t"],
        input=sql, capture_output=True, text=True, check=True,
    )
    return result.stdout


def measure() -> dict:
    rows = psql(" UNION ALL ".join(
        f"SELECT '{t}', pg_total_relation_size('{t}'), pg_relation_size('{t}'), (SELECT count(*) FROM {t})"
        for t in TABLES.values()
    ) + ";")
    tables = {}
    for line in rows.strip().splitlines():
        name, total, heap, count = line.split("\t")
        tables[name] = {"total_bytes": int(total), "table_bytes": int(heap), "rows": int(count),
                        "bytes_per_row": round(int(total) / int(count), 1) if int(count) else None}
    total = sum(t["total_bytes"] for t in tables.values())
    count = sum(t["rows"] for t in tables.values())
    return {"tables": tables, "total_mb": round(total / 2**20, 1), "rows": count,
            "bytes_per_row": round(total / count, 1) if count else None}


def run(monitors: int, retention_days: int, cycles: int) -> dict:
    subprocess.run(["docker", "rm", "-f", CONTAINER], capture_output=True)
    subprocess.run(["docker", "run", "-d", "--name", CONTAINER, "--cpus", "2", "--memory", "2g",
                    "-e", "POSTGRES_PASSWORD=postgres", IMAGE], check=True, capture_output=True)
    try:
        while subprocess.run(["docker", "exec", CONTAINER, "pg_isready", "-U", "postgres"],
                             capture_output=True).returncode != 0:
            time.sleep(1)
        time.sleep(2)  # pg_isready also answers while the init scripts restart the server
        psql(DDL)
        # The cleanup runs once a day, so right before it, one more day than the retention period is stored
        stored_days = retention_days + 1
        for day in range(stored_days):
            psql("".join(insert_day_sql(t, monitors, day) for t in TABLES))
        psql("VACUUM ANALYZE;")
        fresh = measure()
        for cycle in range(cycles):
            psql(delete_day_sql(cycle))
            psql("VACUUM;")  # what autovacuum does after the cleanup: the space becomes reusable, the files don't shrink
            psql("".join(insert_day_sql(t, monitors, stored_days + cycle) for t in TABLES))
        psql("VACUUM ANALYZE;")
        settled = measure()
    finally:
        subprocess.run(["docker", "rm", "-f", CONTAINER], capture_output=True)
    per_day_mb = settled["total_mb"] / stored_days
    return {
        "monitors": monitors,
        "retention_days": retention_days,
        "stored_days": stored_days,
        "cleanup_cycles": cycles,
        "fresh": fresh,
        "settled": settled,
        "mb_per_day": round(per_day_mb, 1),
        "mb_per_1000_monitors_per_day": round(per_day_mb * 1000 / monitors, 1),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--monitors", type=int, default=1000)
    parser.add_argument("--retention-days", type=int, default=7)
    parser.add_argument("--cycles", type=int, default=3, help="how many times the daily cleanup is replayed")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = run(args.monitors, args.retention_days, args.cycles)
    text = json.dumps(result, indent=1)
    if args.out:
        args.out.write_text(text)
    print(text)


if __name__ == "__main__":
    main()
