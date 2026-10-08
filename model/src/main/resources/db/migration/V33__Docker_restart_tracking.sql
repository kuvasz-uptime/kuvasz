ALTER TABLE docker_uptime_event
    ADD COLUMN restart_count INT NULL;

ALTER TABLE docker_uptime_event
    ADD COLUMN container_created_at TIMESTAMPTZ NULL;

ALTER TABLE docker_monitor
    ADD COLUMN restart_alert_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE docker_metrics_log
    ADD COLUMN restart_count INT NULL;

ALTER TABLE docker_metrics_log
    ADD COLUMN container_created_at TIMESTAMPTZ NULL;
