CREATE TABLE icmp_monitor
(
    id                      BIGINT                            DEFAULT nextval('monitor_id_seq'::regclass) NOT NULL,
    name                    TEXT                     NOT NULL,
    host                    TEXT                     NOT NULL,
    uptime_check_interval   INTEGER                  NOT NULL,
    packet_count            INT                      NOT NULL DEFAULT 3,
    timeout_seconds         INT                      NOT NULL DEFAULT 5,
    packet_loss_threshold   INT                      NOT NULL DEFAULT 100,
    failure_count_threshold BIGINT                   NOT NULL DEFAULT 1,
    enabled                 BOOL                              DEFAULT true NOT NULL,
    metrics_history_enabled BOOLEAN                  NOT NULL DEFAULT TRUE,
    integrations            TEXT[]                   NOT NULL DEFAULT ARRAY []::TEXT[],
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT icmp_monitor_pkey PRIMARY KEY (id),
    CONSTRAINT unique_icmp_monitor_name UNIQUE (name)
);
CREATE INDEX icmp_monitor_enabled_idx ON icmp_monitor USING btree (enabled);

-- Table Triggers

create trigger trg_remove_icmp_monitor_from_status_pages
    after delete
    on icmp_monitor
    for each row
execute function remove_monitor_from_status_pages('icmp');
create trigger trg_update_icmp_monitor_in_status_pages
    after update
    on icmp_monitor
    for each row
execute function update_monitor_in_status_pages('icmp');

-- Uptime event table

CREATE TABLE icmp_uptime_event
(
    id         BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT                    NOT NULL REFERENCES icmp_monitor (id) ON DELETE CASCADE,
    status     uptime_status             NOT NULL,
    error      text                      NULL,
    started_at timestamptz DEFAULT now() NOT NULL,
    ended_at   timestamptz               NULL,
    updated_at timestamptz               NOT NULL,
    CONSTRAINT icmp_uptime_event_key UNIQUE (monitor_id, status, ended_at)
);
CREATE INDEX icmp_uptime_event_ended_at_idx ON icmp_uptime_event USING btree (ended_at);
CREATE INDEX icmp_uptime_event_monitor_idx ON icmp_uptime_event USING btree (monitor_id);

-- Metrics log table

CREATE TABLE icmp_metrics_log
(
    id                     BIGSERIAL PRIMARY KEY,
    monitor_id             BIGINT                    NOT NULL REFERENCES icmp_monitor (id) ON DELETE CASCADE,
    latency_ms             INT                       NULL,
    packet_loss_percentage INT                       NOT NULL,
    created_at             TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX icmp_metrics_log_monitor_id_idx ON icmp_metrics_log USING btree (monitor_id);
CREATE INDEX icmp_metrics_log_created_at_idx ON icmp_metrics_log USING btree (created_at);
