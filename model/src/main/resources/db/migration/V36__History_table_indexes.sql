CREATE INDEX http_latency_log_monitor_id_created_at_idx ON http_latency_log USING btree (monitor_id, created_at);
CREATE INDEX http_latency_log_created_at_idx ON http_latency_log USING btree (created_at);
CREATE INDEX tcp_metrics_log_monitor_id_created_at_idx ON tcp_metrics_log USING btree (monitor_id, created_at);
CREATE INDEX icmp_metrics_log_monitor_id_created_at_idx ON icmp_metrics_log USING btree (monitor_id, created_at);
CREATE INDEX dns_metrics_log_monitor_id_created_at_idx ON dns_metrics_log USING btree (monitor_id, created_at);
CREATE INDEX docker_metrics_log_monitor_id_created_at_idx ON docker_metrics_log USING btree (monitor_id, created_at);

DROP INDEX latency_log_monitor_idx;
DROP INDEX tcp_metrics_log_monitor_id_idx;
DROP INDEX icmp_metrics_log_monitor_id_idx;
DROP INDEX dns_metrics_log_monitor_id_idx;
DROP INDEX docker_metrics_log_monitor_id_idx;

DROP INDEX latency_log_latency_idx;
DROP INDEX latency_log_latency_monitor_idx;
