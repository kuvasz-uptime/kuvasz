-- An optional label shown to people instead of the name, so the name can stay a stable identifier (e.g. for monitors
-- managed through the YAML config, where a renamed monitor is a new one).
ALTER TABLE http_monitor ADD COLUMN display_name TEXT DEFAULT NULL;
ALTER TABLE push_monitor ADD COLUMN display_name TEXT DEFAULT NULL;
ALTER TABLE icmp_monitor ADD COLUMN display_name TEXT DEFAULT NULL;
ALTER TABLE tcp_monitor ADD COLUMN display_name TEXT DEFAULT NULL;
ALTER TABLE dns_monitor ADD COLUMN display_name TEXT DEFAULT NULL;
ALTER TABLE docker_monitor ADD COLUMN display_name TEXT DEFAULT NULL;
