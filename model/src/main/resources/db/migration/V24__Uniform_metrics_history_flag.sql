-- Aligns the HTTP monitors' metrics history flag with the other monitor types, which all call it the same way.
-- The API, the YAML config and the backup format keep using `latencyHistoryEnabled` / `latency-history-enabled`,
-- the mapping between the two happens in the application layer.
ALTER TABLE http_monitor
    RENAME COLUMN latency_history_enabled TO metrics_history_enabled;
