CREATE TABLE status_page (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    custom_logo_url TEXT DEFAULT NULL,
    custom_favicon_url TEXT DEFAULT NULL,
    public BOOLEAN NOT NULL DEFAULT FALSE,
    monitors TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS "status_page_monitors_idx" ON status_page USING GIN (monitors);

-- Trigger functions to keep monitors in status pages up to date
CREATE OR REPLACE FUNCTION remove_monitor_from_status_pages()
RETURNS TRIGGER AS $$
DECLARE
    monitor_type TEXT;
    monitor_to_remove TEXT;
BEGIN
    monitor_type := TG_ARGV[0];
    monitor_to_remove := monitor_type || ':' || OLD.name;

    UPDATE status_page
    SET monitors   = array_remove(monitors, monitor_to_remove),
        updated_at = NOW()
    WHERE monitors @> ARRAY[monitor_to_remove];

RETURN OLD;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_remove_http_monitor_from_status_pages
    AFTER DELETE ON http_monitor
    FOR EACH ROW EXECUTE FUNCTION remove_monitor_from_status_pages('http');

CREATE OR REPLACE FUNCTION update_monitor_in_status_pages()
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

    UPDATE status_page
    SET monitors   = array_replace(monitors, old_monitor_name, new_monitor_name),
        updated_at = NOW()
    WHERE monitors @> ARRAY[old_monitor_name];
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_update_http_monitor_in_status_pages
    AFTER UPDATE ON http_monitor
    FOR EACH ROW EXECUTE FUNCTION update_monitor_in_status_pages('http');
