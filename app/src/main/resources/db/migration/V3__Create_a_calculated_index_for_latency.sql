DROP INDEX IF EXISTS "latency_log_latency_monitor_idx";
CREATE INDEX IF NOT EXISTS "latency_log_latency_monitor_idx" ON "latency_log" USING btree ("monitor_id", round("latency", -1));
