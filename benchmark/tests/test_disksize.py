from kbench.disksize import EPOCH, TABLES, delete_day_sql, insert_day_sql, monitor_table_sql, monitors_of


def test_monitors_of_the_generated_set():
    monitors = monitors_of("balanced", 1000)
    assert len(monitors) == 1000
    by_type = {}
    for m in monitors:
        by_type.setdefault(m.monitor_type, []).append(m)
    assert {t: len(ms) for t, ms in by_type.items()} == {
        "http": 450, "push": 150, "tcp": 100, "icmp": 100, "dns": 100, "docker": 100}
    # Ids are counted per type, like the tables of Kuvasz count them
    assert [m.id for m in by_type["tcp"]] == list(range(1, 101))
    assert not any(m.metrics for m in by_type["push"])
    assert all(m.metrics for m in by_type["http"])
    # Docker monitors write rows only with their metrics history on (30% of them)
    assert 25 <= sum(m.metrics for m in by_type["docker"]) <= 35


def test_insert_day_sql_fills_every_table_with_one_day():
    sql = insert_day_sql(day=3, history_end=EPOCH + 10 * 86400)
    for table in TABLES.values():
        assert f"INSERT INTO {table} " in sql
    assert f"ceil(({EPOCH + 3 * 86400} - m.phase)" in sql
    assert f"floor(({EPOCH + 4 * 86400} - 1 - m.phase)" in sql
    assert "%(" not in sql


def test_monitor_table_sql():
    sql = monitor_table_sql(monitors_of("balanced", 100))
    assert sql.count("('http', ") == 45
    assert "CREATE TABLE bf_monitor" in sql


def test_delete_day_sql():
    sql = delete_day_sql(0)
    for table in TABLES.values():
        assert f"DELETE FROM {table} WHERE created_at < to_timestamp({EPOCH + 86400});" in sql
