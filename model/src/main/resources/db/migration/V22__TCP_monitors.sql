CREATE TABLE tcp_monitor
(
    id                      BIGINT                            DEFAULT nextval('monitor_id_seq'::regclass) NOT NULL,
    name                    TEXT                     NOT NULL,
    host                    TEXT                     NOT NULL,
    port                    INT                      NOT NULL,
    uptime_check_interval   INTEGER                  NOT NULL,
    timeout_ms              INT                      NOT NULL DEFAULT 5000,
    latency_threshold_ms    INT                      NULL,
    failure_count_threshold BIGINT                   NOT NULL DEFAULT 1,
    enabled                 BOOL                              DEFAULT true NOT NULL,
    metrics_history_enabled BOOLEAN                  NOT NULL DEFAULT TRUE,
    integrations            TEXT[]                   NOT NULL DEFAULT ARRAY []::TEXT[],
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT tcp_monitor_pkey PRIMARY KEY (id),
    CONSTRAINT unique_tcp_monitor_name UNIQUE (name)
);
CREATE INDEX tcp_monitor_enabled_idx ON tcp_monitor USING btree (enabled);

-- Table Triggers

create trigger trg_remove_tcp_monitor_from_status_pages
    after delete
    on tcp_monitor
    for each row
execute function remove_monitor_from_status_pages('tcp');
create trigger trg_update_tcp_monitor_in_status_pages
    after update
    on tcp_monitor
    for each row
execute function update_monitor_in_status_pages('tcp');
create trigger trg_remove_tcp_monitor_from_maintenance_windows
    after delete
    on tcp_monitor
    for each row
execute function remove_monitor_from_maintenance_windows('tcp');
create trigger trg_update_tcp_monitor_in_maintenance_windows
    after update
    on tcp_monitor
    for each row
execute function update_monitor_in_maintenance_windows('tcp');

-- Uptime event table

CREATE TABLE tcp_uptime_event
(
    id         BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT                    NOT NULL REFERENCES tcp_monitor (id) ON DELETE CASCADE,
    status     uptime_status             NOT NULL,
    error      text                      NULL,
    started_at timestamptz DEFAULT now() NOT NULL,
    ended_at   timestamptz               NULL,
    updated_at timestamptz               NOT NULL,
    CONSTRAINT tcp_uptime_event_key UNIQUE (monitor_id, status, ended_at)
);
CREATE INDEX tcp_uptime_event_ended_at_idx ON tcp_uptime_event USING btree (ended_at);
CREATE INDEX tcp_uptime_event_monitor_idx ON tcp_uptime_event USING btree (monitor_id);

-- Metrics log table

CREATE TABLE tcp_metrics_log
(
    id         BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT                    NOT NULL REFERENCES tcp_monitor (id) ON DELETE CASCADE,
    latency_ms INT                       NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX tcp_metrics_log_monitor_id_idx ON tcp_metrics_log USING btree (monitor_id);
CREATE INDEX tcp_metrics_log_created_at_idx ON tcp_metrics_log USING btree (created_at);
