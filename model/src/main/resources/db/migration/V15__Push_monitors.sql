CREATE TABLE push_monitor (
    id BIGINT DEFAULT nextval('monitor_id_seq'::regclass) NOT NULL,
    name TEXT NOT NULL,
    heartbeat_interval BIGINT NOT NULL,
    grace_period BIGINT NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    enabled BOOL DEFAULT true NOT NULL,
    client_secret TEXT NOT NULL,
    integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT push_monitor_pkey PRIMARY KEY (id),
    CONSTRAINT unique_push_monitor_name UNIQUE (name),
    CONSTRAINT unique_push_client_secret UNIQUE (client_secret) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX push_monitor_effective_monitors_idx ON push_monitor USING btree (last_heartbeat, enabled);

-- Table Triggers

create trigger trg_remove_push_monitor_from_status_pages
    after delete on push_monitor
    for each row execute function remove_monitor_from_status_pages('push');
create trigger trg_update_push_monitor_in_status_pages
    after update on push_monitor
    for each row execute function update_monitor_in_status_pages('push');

-- Uptime event table

CREATE TABLE push_uptime_event (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES push_monitor (id) ON DELETE CASCADE,
    status uptime_status NOT NULL,
    error text NULL,
    started_at timestamptz DEFAULT now() NOT NULL,
    ended_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT push_uptime_event_key UNIQUE (monitor_id, status, ended_at)
);
CREATE INDEX push_uptime_event_ended_at_idx ON push_uptime_event USING btree (ended_at);
CREATE INDEX push_uptime_event_monitor_idx ON push_uptime_event USING btree (monitor_id);
