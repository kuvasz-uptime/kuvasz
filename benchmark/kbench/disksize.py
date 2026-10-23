"""Measures how much disk the per-check history takes, without running Kuvasz: a scratch Postgres gets the per-check
tables of Kuvasz (same columns, same indexes), filled with the history of a generated monitor set for the retention
period, then the daily cleanup is replayed a few times (delete the oldest day, vacuum, insert a new day), so the tables
reach the size they settle at in a long-running installation. The history is the same as what kbench.backfill loads:
every monitor checks at its own interval, and the failing checks of HTTP monitors don't write a row."""

import argparse
import json
import subprocess
import time
from pathlib import Path

from kbench import backfill, generate, schema
from kbench.generate import BASE_SET_SIZE

CONTAINER = "kb-disksize"
IMAGE = "postgres:18-alpine"

# The per-check tables as the migrations leave them (model/src/main/resources/db/migration), without the foreign keys,
# which don't take any space
DDL = """
CREATE TABLE http_latency_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX http_latency_log_monitor_id_created_at_idx ON http_latency_log USING btree (monitor_id, created_at)
    INCLUDE (latency, id);
CREATE INDEX http_latency_log_created_at_idx ON http_latency_log USING btree (created_at);

CREATE TABLE tcp_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX tcp_metrics_log_monitor_id_created_at_idx ON tcp_metrics_log USING btree (monitor_id, created_at)
    INCLUDE (latency_ms, id);
CREATE INDEX tcp_metrics_log_created_at_idx ON tcp_metrics_log USING btree (created_at);

CREATE TABLE icmp_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, packet_loss_percentage INT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX icmp_metrics_log_monitor_id_created_at_idx ON icmp_metrics_log USING btree (monitor_id, created_at)
    INCLUDE (latency_ms, packet_loss_percentage, id);
CREATE INDEX icmp_metrics_log_created_at_idx ON icmp_metrics_log USING btree (created_at);

CREATE TABLE dns_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX dns_metrics_log_monitor_id_created_at_idx ON dns_metrics_log USING btree (monitor_id, created_at)
    INCLUDE (latency_ms, id);
CREATE INDEX dns_metrics_log_created_at_idx ON dns_metrics_log USING btree (created_at);

CREATE TABLE docker_metrics_log (
    id BIGSERIAL PRIMARY KEY, monitor_id BIGINT NOT NULL, latency_ms INT NULL, cpu_usage_percent NUMERIC(7, 2) NULL,
    memory_usage_bytes BIGINT NULL, memory_limit_bytes BIGINT NULL, created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    restart_count INT NULL, container_created_at TIMESTAMPTZ NULL
);
CREATE INDEX docker_metrics_log_monitor_id_created_at_idx ON docker_metrics_log USING btree (monitor_id, created_at, id)
    INCLUDE (latency_ms, cpu_usage_percent, memory_usage_bytes, memory_limit_bytes, restart_count,
        container_created_at);
CREATE INDEX docker_metrics_log_created_at_idx ON docker_metrics_log USING btree (created_at);
"""

TABLES = {monitor_type: table for monitor_type, (table, _) in schema.CHECK_LOG_TABLES.items()}
# The first day of the history
EPOCH = 1767225600  # 2026-01-01


def monitors_of(mix: str, monitors: int) -> list[backfill.MonitorRow]:
    """The monitors of the generated set, with their ids as Kuvasz would give them (counted per type)."""
    rows = []
    ids = dict.fromkeys(generate.TYPES, 0)
    for m in generate.generate(generate.load_mix(mix), max(1, monitors // BASE_SET_SIZE)):
        ids[m.type] += 1
        metrics = m.type != "push" and (m.type != "docker" or m.config.get("metrics-history-enabled", False))
        rows.append(backfill.MonitorRow(m.type, ids[m.type], m.interval, m.behavior, metrics))
    return rows


def monitor_table_sql(monitors: list[backfill.MonitorRow]) -> str:
    values = ", ".join(
        f"('{m.monitor_type}', {m.id}, {m.interval}, {m.id * 7919 % m.interval}, '{m.behavior}', {m.flap_seconds}, "
        f"{m.episode_offset}, {m.outage_offset}, {str(m.metrics).lower()})"
        for m in monitors
    )
    return f"""CREATE TABLE bf_monitor (monitor_type text, id bigint, interval_s int, phase int, behavior text, flap int,
               episode_offset int, outage_offset int, metrics boolean);
               INSERT INTO bf_monitor VALUES {values};"""


def insert_day_sql(day: int, history_end: int) -> str:
    """Inserts the rows of one day (day 0 is the oldest) into every table, in time order, like the checks of many
    monitors running side by side write them."""
    params = {"chunk_start": EPOCH + day * 86400, "chunk_end": EPOCH + (day + 1) * 86400, "history_end": history_end}
    return "".join(backfill.log_insert_sql(t, table) % params + ";" for t, table in TABLES.items())


def delete_day_sql(day: int) -> str:
    """The daily cleanup of Kuvasz: everything older than the retention period goes."""
    limit = f"to_timestamp({EPOCH + (day + 1) * 86400})"
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


def run(mix: str, monitors: int, retention_days: int, cycles: int) -> dict:
    subprocess.run(["docker", "rm", "-f", "-v", CONTAINER], capture_output=True)
    subprocess.run(["docker", "run", "-d", "--name", CONTAINER, "--cpus", "2", "--memory", "2g",
                    "-e", "POSTGRES_PASSWORD=postgres", IMAGE], check=True, capture_output=True)
    try:
        while subprocess.run(["docker", "exec", CONTAINER, "pg_isready", "-U", "postgres"],
                             capture_output=True).returncode != 0:
            time.sleep(1)
        time.sleep(2)  # pg_isready also answers while the init scripts restart the server
        psql(DDL)
        psql(monitor_table_sql(monitors_of(mix, monitors)))
        # The cleanup runs once a day, so right before it, one more day than the retention period is stored
        stored_days = retention_days + 1
        history_end = EPOCH + (stored_days + cycles) * 86400
        for day in range(stored_days):
            psql(insert_day_sql(day, history_end))
        psql("VACUUM ANALYZE;")
        fresh = measure()
        for cycle in range(cycles):
            psql(delete_day_sql(cycle))
            psql("VACUUM;")  # what autovacuum does after the cleanup: the space becomes reusable, the files don't shrink
            psql(insert_day_sql(stored_days + cycle, history_end))
        psql("VACUUM ANALYZE;")
        settled = measure()
    finally:
        subprocess.run(["docker", "rm", "-f", "-v", CONTAINER], capture_output=True)
    per_day_mb = settled["total_mb"] / stored_days
    return {
        "mix": mix,
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
    parser.add_argument("--mix", default="balanced")
    parser.add_argument("--monitors", type=int, default=1000)
    parser.add_argument("--retention-days", type=int, default=7)
    parser.add_argument("--cycles", type=int, default=3, help="how many times the daily cleanup is replayed")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = run(args.mix, args.monitors, args.retention_days, args.cycles)
    text = json.dumps(result, indent=1)
    if args.out:
        args.out.write_text(text)
    print(text)


if __name__ == "__main__":
    main()
