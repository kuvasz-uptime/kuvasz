from kbench.disksize import TABLES, delete_day_sql, insert_day_sql, rows_per_day


def test_rows_per_day():
    # 8.15 rows/s per 1,000 monitors
    assert rows_per_day("http", 1000) == round(8.15 * 86400)
    assert rows_per_day("http", 2000) == round(2 * 8.15 * 86400)


def test_insert_day_sql():
    sql = insert_day_sql("icmp", 1000, day=3)
    assert sql.startswith("INSERT INTO icmp_metrics_log (monitor_id, latency_ms, packet_loss_percentage, created_at)")
    assert f"generate_series(0, {rows_per_day('icmp', 1000) - 1})" in sql
    assert "make_interval(days => 3)" in sql
    # 100 ICMP monitors per 1,000
    assert "1 + floor(random() * 100)::bigint" in sql


def test_insert_day_sql_for_every_table():
    for monitor_type, table in TABLES.items():
        assert f"INSERT INTO {table} " in insert_day_sql(monitor_type, 500, day=0)


def test_delete_day_sql():
    sql = delete_day_sql(0)
    for table in TABLES.values():
        assert f"DELETE FROM {table} WHERE created_at < timestamptz '2026-01-01' + make_interval(days => 1);" in sql


def test_docker_rows_fill_the_restart_tracking_columns():
    sql = insert_day_sql("docker", 1000, day=0)
    assert "restart_count, container_created_at) SELECT" in sql
    assert ", 0, timestamptz '2025-12-01' FROM" in sql
