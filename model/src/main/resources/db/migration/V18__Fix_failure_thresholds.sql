ALTER TABLE http_uptime_event
DROP
COLUMN failure_count;

ALTER TABLE push_uptime_event
DROP
COLUMN failure_count;

CREATE TABLE pending_failure
(
    monitor_id    BIGINT                   NOT NULL PRIMARY KEY,
    failure_count BIGINT                   NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE
OR REPLACE FUNCTION delete_pending_failures_of_monitor()
RETURNS TRIGGER AS $$
BEGIN
DELETE
FROM pending_failure
WHERE monitor_id = OLD.id;

RETURN OLD;
END;
$$
LANGUAGE plpgsql;

CREATE
OR REPLACE TRIGGER trg_remove_pending_failures_of_http_monitor
    AFTER DELETE
ON http_monitor
    FOR EACH ROW EXECUTE FUNCTION delete_pending_failures_of_monitor();

CREATE
OR REPLACE TRIGGER trg_remove_pending_failures_of_push_monitor
    AFTER DELETE
ON push_monitor
    FOR EACH ROW EXECUTE FUNCTION delete_pending_failures_of_monitor();
