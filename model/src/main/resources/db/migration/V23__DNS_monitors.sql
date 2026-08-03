CREATE TYPE dns_response_code AS ENUM ('NOERROR', 'NXDOMAIN', 'SERVFAIL', 'REFUSED');
CREATE TYPE dns_transport AS ENUM ('UDP', 'TCP');

CREATE TABLE dns_monitor
(
    id                      BIGINT                            DEFAULT nextval('monitor_id_seq'::regclass) NOT NULL,
    name                    TEXT                     NOT NULL,
    host                    TEXT                     NOT NULL,
    resolver_host           TEXT                     NULL,
    resolver_port           INT                      NOT NULL DEFAULT 53,
    transport               dns_transport            NOT NULL DEFAULT 'UDP',
    record_matchers         JSONB                    NOT NULL DEFAULT '[]',
    expected_response_code  dns_response_code        NOT NULL DEFAULT 'NOERROR',
    drift_detection_enabled BOOLEAN                  NOT NULL DEFAULT FALSE,
    drift_record_types      TEXT[]                   NOT NULL DEFAULT ARRAY []::TEXT[],
    uptime_check_interval   INTEGER                  NOT NULL,
    timeout_ms              INT                      NOT NULL DEFAULT 5000,
    latency_threshold_ms    INT                      NULL,
    failure_count_threshold BIGINT                   NOT NULL DEFAULT 1,
    enabled                 BOOL                              DEFAULT true NOT NULL,
    metrics_history_enabled BOOLEAN                  NOT NULL DEFAULT TRUE,
    integrations            TEXT[]                   NOT NULL DEFAULT ARRAY []::TEXT[],
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT dns_monitor_pkey PRIMARY KEY (id),
    CONSTRAINT unique_dns_monitor_name UNIQUE (name)
);
CREATE INDEX dns_monitor_enabled_idx ON dns_monitor USING btree (enabled);

-- Table Triggers

create trigger trg_remove_dns_monitor_from_status_pages
    after delete
    on dns_monitor
    for each row
execute function remove_monitor_from_status_pages('dns');
create trigger trg_update_dns_monitor_in_status_pages
    after update
    on dns_monitor
    for each row
execute function update_monitor_in_status_pages('dns');
create trigger trg_remove_dns_monitor_from_maintenance_windows
    after delete
    on dns_monitor
    for each row
execute function remove_monitor_from_maintenance_windows('dns');
create trigger trg_update_dns_monitor_in_maintenance_windows
    after update
    on dns_monitor
    for each row
execute function update_monitor_in_maintenance_windows('dns');

-- Uptime event table

CREATE TABLE dns_uptime_event
(
    id         BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT                    NOT NULL REFERENCES dns_monitor (id) ON DELETE CASCADE,
    status     uptime_status             NOT NULL,
    error      text                      NULL,
    started_at timestamptz DEFAULT now() NOT NULL,
    ended_at   timestamptz               NULL,
    updated_at timestamptz               NOT NULL,
    CONSTRAINT dns_uptime_event_key UNIQUE (monitor_id, status, ended_at)
);
CREATE INDEX dns_uptime_event_ended_at_idx ON dns_uptime_event USING btree (ended_at);
CREATE INDEX dns_uptime_event_monitor_idx ON dns_uptime_event USING btree (monitor_id);

-- Metrics log table

CREATE TABLE dns_metrics_log
(
    id         BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT                    NOT NULL REFERENCES dns_monitor (id) ON DELETE CASCADE,
    latency_ms INT                       NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX dns_metrics_log_monitor_id_idx ON dns_metrics_log USING btree (monitor_id);
CREATE INDEX dns_metrics_log_created_at_idx ON dns_metrics_log USING btree (created_at);

-- Drift detection snapshot table (runtime state)

CREATE TABLE dns_resolution_snapshot
(
    monitor_id BIGINT      PRIMARY KEY REFERENCES dns_monitor (id) ON DELETE CASCADE,
    records    JSONB       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The snapshot is runtime state derived from the drift-relevant config. When any of those inputs change, the stored
-- answer set is no longer comparable, so it is dropped and re-seeded silently on the next check. The WHEN clause keeps
-- the baseline intact across restarts / unrelated edits (the YAML bootstrap upserts every monitor on every boot).

CREATE OR REPLACE FUNCTION reset_dns_snapshot_on_drift_config_change() RETURNS TRIGGER AS
$$
BEGIN
    DELETE FROM dns_resolution_snapshot WHERE monitor_id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reset_dns_snapshot_on_drift_config_change
    AFTER UPDATE
    ON dns_monitor
    FOR EACH ROW
    WHEN (
        OLD.drift_detection_enabled IS DISTINCT FROM NEW.drift_detection_enabled
        OR OLD.drift_record_types IS DISTINCT FROM NEW.drift_record_types
        OR OLD.record_matchers IS DISTINCT FROM NEW.record_matchers
        OR OLD.host IS DISTINCT FROM NEW.host
        OR OLD.resolver_host IS DISTINCT FROM NEW.resolver_host
        OR OLD.resolver_port IS DISTINCT FROM NEW.resolver_port
        OR OLD.transport IS DISTINCT FROM NEW.transport
        )
EXECUTE FUNCTION reset_dns_snapshot_on_drift_config_change();
