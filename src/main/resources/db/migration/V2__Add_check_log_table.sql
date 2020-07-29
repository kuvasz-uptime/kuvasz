SET SCHEMA 'kuvasz';

CREATE TYPE uptime_status AS ENUM ('UP', 'DOWN');

CREATE TABLE uptime_event
(
    id         SERIAL PRIMARY KEY,
    monitor_id INTEGER                  NOT NULL REFERENCES monitor (id),
    status     uptime_status            NOT NULL,
    error      TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ended_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT "uptime_event_key" UNIQUE ("monitor_id", "status", "ended_at")
);

COMMENT ON COLUMN uptime_event.status IS 'Status of the event';
COMMENT ON COLUMN uptime_event.started_at IS 'The current event started at';
COMMENT ON COLUMN uptime_event.ended_at IS 'The current event ended at';

CREATE INDEX IF NOT EXISTS "uptime_event_monitor_idx" ON "uptime_event" USING btree ("monitor_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "uptime_event_ended_at_idx" ON "uptime_event" USING btree ("ended_at" ASC NULLS LAST);

CREATE TABLE latency_log
(
    id         SERIAL PRIMARY KEY,
    monitor_id INTEGER                  NOT NULL REFERENCES monitor (id),
    latency    INTEGER                  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

COMMENT ON COLUMN latency_log.latency IS 'Lateny in ms';

CREATE INDEX IF NOT EXISTS "latency_log_monitor_idx" ON "latency_log" USING btree ("monitor_id" ASC NULLS LAST);
