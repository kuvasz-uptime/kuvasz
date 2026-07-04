CREATE TABLE maintenance_window
(
    id                   BIGSERIAL PRIMARY KEY,
    name                 TEXT                     NOT NULL UNIQUE,
    description          TEXT,
    enabled              BOOLEAN                  NOT NULL DEFAULT TRUE,
    global               BOOLEAN                  NOT NULL DEFAULT FALSE,
    show_on_status_pages BOOLEAN                  NOT NULL DEFAULT FALSE,
    cron                 TEXT NULL,
    start                TIMESTAMP WITH TIME ZONE NULL,
    duration             TEXT NULL,
    monitors             TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    integrations         TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT cron_or_single_schedule CHECK ((cron IS NULL) OR (start IS NULL)),
    CONSTRAINT cron_with_duration CHECK ((cron IS NULL) OR (cron IS NOT NULL AND duration IS NOT NULL)),
    CONSTRAINT single_with_duration CHECK ((start IS NULL) OR (start IS NOT NULL AND duration IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS "maintenance_monitors_idx" ON maintenance_window USING GIN (monitors);
CREATE INDEX IF NOT EXISTS "maintenance_enabled_idx" ON maintenance_window USING BTREE (enabled);

-- Trigger functions to keep monitors in maintenance windows up to date
CREATE OR REPLACE FUNCTION remove_monitor_from_maintenance_windows()
RETURNS TRIGGER AS $$
DECLARE
monitor_type TEXT;
    monitor_to_remove TEXT;
BEGIN
    monitor_type := TG_ARGV[0];
    monitor_to_remove := monitor_type || ':' || OLD.name;

UPDATE maintenance_window
SET monitors   = array_remove(monitors, monitor_to_remove),
    updated_at = NOW()
WHERE monitors @> ARRAY[monitor_to_remove];

RETURN OLD;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_remove_http_monitor_from_maintenance_windows
    AFTER DELETE ON http_monitor
    FOR EACH ROW EXECUTE FUNCTION remove_monitor_from_maintenance_windows('http');
CREATE OR REPLACE TRIGGER trg_remove_push_monitor_from_maintenance_windows
    after delete on push_monitor
    for each row execute function remove_monitor_from_maintenance_windows('push');
CREATE OR REPLACE TRIGGER trg_remove_icmp_monitor_from_maintenance_windows
    after delete on icmp_monitor
    for each row execute function remove_monitor_from_maintenance_windows('icmp');

CREATE OR REPLACE FUNCTION update_monitor_in_maintenance_windows()
RETURNS TRIGGER AS $$
DECLARE
monitor_type TEXT;
    old_monitor_name TEXT;
    new_monitor_name TEXT;
BEGIN
    monitor_type := TG_ARGV[0];
    old_monitor_name := monitor_type || ':' || OLD.name;
    new_monitor_name := monitor_type || ':' || NEW.name;

    IF old_monitor_name = new_monitor_name THEN
        RETURN NEW; -- No change in name, no need to update
    END IF;

    UPDATE maintenance_window
    SET monitors   = array_replace(monitors, old_monitor_name, new_monitor_name),
        updated_at = NOW()
    WHERE monitors @> ARRAY[old_monitor_name];
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_update_http_monitor_in_maintenance_windows
    AFTER UPDATE ON http_monitor
    FOR EACH ROW EXECUTE FUNCTION update_monitor_in_maintenance_windows('http');
CREATE OR REPLACE TRIGGER trg_update_push_monitor_in_maintenance_windows
    AFTER UPDATE ON push_monitor
    FOR EACH ROW EXECUTE FUNCTION update_monitor_in_maintenance_windows('push');
CREATE OR REPLACE TRIGGER trg_update_icmp_monitor_in_maintenance_windows
    AFTER UPDATE ON icmp_monitor
    FOR EACH ROW EXECUTE FUNCTION update_monitor_in_maintenance_windows('icmp');
