CREATE TYPE investigation_status AS ENUM ('INVESTIGATING', 'IDENTIFIED', 'RESOLVED');

CREATE TABLE incident_note
(
    id            BIGSERIAL PRIMARY KEY,
    incident_type TEXT                     NOT NULL
        CHECK (incident_type IN ('HTTP', 'SSL', 'PUSH', 'ICMP', 'TCP', 'DNS', 'DOCKER')),
    -- The ID of the uptime event, or the ID of the SSL event in case of SSL incidents
    event_id      BIGINT                   NOT NULL,
    status        investigation_status     NOT NULL,
    content       TEXT                     NOT NULL,
    timestamp     TIMESTAMP WITH TIME ZONE NULL,
    public        BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX incident_note_event_idx ON incident_note USING BTREE (incident_type, event_id);

-- The reference is polymorphic, so there is no FK to cascade: the notes are deleted together with their events instead
-- (retention cleanup, cascaded monitor deletion, etc.). Statement level, because the events are deleted in bulk.
CREATE OR REPLACE FUNCTION delete_incident_notes_of_events()
RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM incident_note n
    USING old_events o
    WHERE n.incident_type = TG_ARGV[0]
      AND n.event_id = o.id;

    RETURN NULL;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_delete_incident_notes_of_http_uptime_events
    AFTER DELETE ON http_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_incident_notes_of_events('HTTP');
CREATE OR REPLACE TRIGGER trg_delete_incident_notes_of_push_uptime_events
    AFTER DELETE ON push_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_incident_notes_of_events('PUSH');
CREATE OR REPLACE TRIGGER trg_delete_incident_notes_of_icmp_uptime_events
    AFTER DELETE ON icmp_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_incident_notes_of_events('ICMP');
CREATE OR REPLACE TRIGGER trg_delete_incident_notes_of_tcp_uptime_events
    AFTER DELETE ON tcp_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_incident_notes_of_events('TCP');
CREATE OR REPLACE TRIGGER trg_delete_incident_notes_of_dns_uptime_events
    AFTER DELETE ON dns_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_incident_notes_of_events('DNS');
CREATE OR REPLACE TRIGGER trg_delete_incident_notes_of_docker_uptime_events
    AFTER DELETE ON docker_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_incident_notes_of_events('DOCKER');
CREATE OR REPLACE TRIGGER trg_delete_incident_notes_of_ssl_events
    AFTER DELETE ON ssl_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_incident_notes_of_events('SSL');

-- The integrations that were notified when the event was opened
ALTER TABLE http_uptime_event
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
ALTER TABLE push_uptime_event
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
ALTER TABLE icmp_uptime_event
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
ALTER TABLE tcp_uptime_event
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
ALTER TABLE dns_uptime_event
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
ALTER TABLE docker_uptime_event
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
ALTER TABLE ssl_event
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
