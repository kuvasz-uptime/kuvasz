ALTER TABLE uptime_event
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

UPDATE uptime_event
SET updated_at = COALESCE(ended_at, started_at);

ALTER TABLE uptime_event
    ALTER COLUMN updated_at SET NOT NULL;
