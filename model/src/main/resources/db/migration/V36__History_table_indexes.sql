-- Every monitor writes its history at the same time, so the rows of one monitor are spread over the whole table.
-- To read the history of even a few monitors, the database had to read almost the whole table.
-- These indexes keep the rows of each monitor together, and they also store the values that are read (for example the
-- response time). This way the history of a monitor is read from the index alone, which is much faster.
CREATE INDEX http_latency_log_monitor_id_created_at_idx ON http_latency_log USING btree (monitor_id, created_at)
    INCLUDE (latency, id);
CREATE INDEX http_latency_log_created_at_idx ON http_latency_log USING btree (created_at);
CREATE INDEX tcp_metrics_log_monitor_id_created_at_idx ON tcp_metrics_log USING btree (monitor_id, created_at)
    INCLUDE (latency_ms, id);
CREATE INDEX icmp_metrics_log_monitor_id_created_at_idx ON icmp_metrics_log USING btree (monitor_id, created_at)
    INCLUDE (latency_ms, packet_loss_percentage, id);
CREATE INDEX dns_metrics_log_monitor_id_created_at_idx ON dns_metrics_log USING btree (monitor_id, created_at)
    INCLUDE (latency_ms, id);
CREATE INDEX docker_metrics_log_monitor_id_created_at_idx ON docker_metrics_log USING btree (monitor_id, created_at, id)
    INCLUDE (latency_ms, cpu_usage_percent, memory_usage_bytes, memory_limit_bytes, restart_count,
        container_created_at);

DROP INDEX latency_log_monitor_idx;
DROP INDEX tcp_metrics_log_monitor_id_idx;
DROP INDEX icmp_metrics_log_monitor_id_idx;
DROP INDEX dns_metrics_log_monitor_id_idx;
DROP INDEX docker_metrics_log_monitor_id_idx;

DROP INDEX latency_log_latency_idx;
DROP INDEX latency_log_latency_monitor_idx;
