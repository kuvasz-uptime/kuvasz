ALTER TABLE http_uptime_event
    ADD COLUMN failure_count BIGINT DEFAULT 0 NOT NULL;

ALTER TABLE push_uptime_event
    ADD COLUMN failure_count BIGINT DEFAULT 0 NOT NULL;

UPDATE http_uptime_event
SET failure_count = 1
WHERE status = 'DOWN';

UPDATE push_uptime_event
SET failure_count = 1
WHERE status = 'DOWN';

ALTER TABLE http_monitor
    ADD COLUMN failure_count_threshold BIGINT DEFAULT 1 NOT NULL;

ALTER TABLE push_monitor
    ADD COLUMN failure_count_threshold BIGINT DEFAULT 1 NOT NULL;
