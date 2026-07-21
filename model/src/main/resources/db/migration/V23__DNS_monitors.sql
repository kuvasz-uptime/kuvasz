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
