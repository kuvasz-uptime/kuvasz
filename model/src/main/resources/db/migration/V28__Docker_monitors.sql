CREATE TABLE docker_monitor
(
    id                        BIGINT                            DEFAULT nextval('monitor_id_seq'::regclass) NOT NULL,
    name                      TEXT                     NOT NULL,
    docker_host               TEXT                     NOT NULL,
    container                 TEXT                     NOT NULL,
    uptime_check_interval     INTEGER                  NOT NULL,
    timeout_ms                INT                      NOT NULL DEFAULT 5000,
    failure_count_threshold   BIGINT                   NOT NULL DEFAULT 1,
    enabled                   BOOL                              DEFAULT true NOT NULL,
    metrics_history_enabled   BOOLEAN                  NOT NULL DEFAULT FALSE,
    integrations              TEXT[]                   NOT NULL DEFAULT ARRAY []::TEXT[],
    category                  TEXT                              DEFAULT NULL,
    ignore_connectivity_check BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT docker_monitor_pkey PRIMARY KEY (id),
    CONSTRAINT unique_docker_monitor_name UNIQUE (name)
);
CREATE INDEX docker_monitor_enabled_idx ON docker_monitor USING btree (enabled);
CREATE INDEX docker_monitor_category_idx ON docker_monitor USING btree (category) WHERE category IS NOT NULL;
CREATE INDEX docker_monitor_docker_host_idx ON docker_monitor USING btree (docker_host);

-- Table Triggers

create trigger trg_remove_docker_monitor_from_status_pages
    after delete
    on docker_monitor
    for each row
execute function remove_monitor_from_status_pages('docker');
create trigger trg_update_docker_monitor_in_status_pages
    after update
    on docker_monitor
    for each row
execute function update_monitor_in_status_pages('docker');
create trigger trg_remove_docker_monitor_from_maintenance_windows
    after delete
    on docker_monitor
    for each row
execute function remove_monitor_from_maintenance_windows('docker');
create trigger trg_update_docker_monitor_in_maintenance_windows
    after update
    on docker_monitor
    for each row
execute function update_monitor_in_maintenance_windows('docker');

-- Uptime event table

CREATE TABLE docker_uptime_event
(
    id         BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT                    NOT NULL REFERENCES docker_monitor (id) ON DELETE CASCADE,
    status     uptime_status             NOT NULL,
    error      text                      NULL,
    started_at timestamptz DEFAULT now() NOT NULL,
    ended_at   timestamptz               NULL,
    updated_at timestamptz               NOT NULL,
    image      text                      NULL,
    CONSTRAINT docker_uptime_event_key UNIQUE (monitor_id, status, ended_at)
);
CREATE INDEX docker_uptime_event_ended_at_idx ON docker_uptime_event USING btree (ended_at);
CREATE INDEX docker_uptime_event_monitor_idx ON docker_uptime_event USING btree (monitor_id);

-- Metrics log table

CREATE TABLE docker_metrics_log
(
    id                 BIGSERIAL PRIMARY KEY,
    monitor_id         BIGINT                    NOT NULL REFERENCES docker_monitor (id) ON DELETE CASCADE,
    latency_ms         INT                       NULL,
    cpu_usage_percent  NUMERIC(7, 2)             NULL,
    memory_usage_bytes BIGINT                    NULL,
    memory_limit_bytes BIGINT                    NULL,
    created_at         TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX docker_metrics_log_monitor_id_idx ON docker_metrics_log USING btree (monitor_id);
CREATE INDEX docker_metrics_log_created_at_idx ON docker_metrics_log USING btree (created_at);
