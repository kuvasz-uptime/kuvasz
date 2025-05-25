CREATE TYPE ssl_status AS ENUM ('VALID', 'INVALID', 'WILL_EXPIRE');

CREATE TABLE ssl_event
(
    id         SERIAL PRIMARY KEY,
    monitor_id INTEGER                  NOT NULL REFERENCES monitor (id) ON DELETE CASCADE,
    status     ssl_status               NOT NULL,
    error      TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ended_at   TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT "ssl_event_key" UNIQUE ("monitor_id", "status", "ended_at")
);

COMMENT ON COLUMN ssl_event.status IS 'Status of the event';
COMMENT ON COLUMN ssl_event.started_at IS 'The current event started at';
COMMENT ON COLUMN ssl_event.ended_at IS 'The current event ended at';

CREATE INDEX IF NOT EXISTS "ssl_event_monitor_idx" ON "ssl_event" USING btree ("monitor_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "ssl_event_ended_at_idx" ON "ssl_event" USING btree ("ended_at" ASC NULLS LAST);

ALTER TABLE monitor
    ADD COLUMN ssl_check_enabled BOOLEAN NOT NULL DEFAULT FAlSE;
