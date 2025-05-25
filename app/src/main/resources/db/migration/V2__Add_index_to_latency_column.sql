CREATE INDEX IF NOT EXISTS "latency_log_latency_idx" ON "latency_log" USING btree ("latency");
CREATE INDEX IF NOT EXISTS "latency_log_latency_monitor_idx" ON "latency_log" USING btree ("monitor_id", "latency");
