CREATE TYPE integration_thread_subject AS ENUM ('HTTP', 'SSL', 'PUSH', 'ICMP', 'TCP', 'DNS', 'DOCKER', 'MAINTENANCE');

CREATE TABLE integration_thread
(
    integration_id TEXT                       NOT NULL,
    subject_type   integration_thread_subject NOT NULL,
    subject_id     BIGINT                     NOT NULL,
    ts             TEXT                       NOT NULL,
    root_text      TEXT                       NOT NULL,
    PRIMARY KEY (integration_id, subject_type, subject_id)
);

CREATE INDEX integration_thread_subject_idx ON integration_thread USING BTREE (subject_type, subject_id);

CREATE OR REPLACE FUNCTION delete_integration_threads_of_events()
RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM integration_thread t
    USING old_events o
    WHERE t.subject_type = TG_ARGV[0]::integration_thread_subject
      AND t.subject_id = o.id;

    RETURN NULL;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_delete_integration_threads_of_http_uptime_events
    AFTER DELETE ON http_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_integration_threads_of_events('HTTP');
CREATE OR REPLACE TRIGGER trg_delete_integration_threads_of_push_uptime_events
    AFTER DELETE ON push_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_integration_threads_of_events('PUSH');
CREATE OR REPLACE TRIGGER trg_delete_integration_threads_of_icmp_uptime_events
    AFTER DELETE ON icmp_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_integration_threads_of_events('ICMP');
CREATE OR REPLACE TRIGGER trg_delete_integration_threads_of_tcp_uptime_events
    AFTER DELETE ON tcp_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_integration_threads_of_events('TCP');
CREATE OR REPLACE TRIGGER trg_delete_integration_threads_of_dns_uptime_events
    AFTER DELETE ON dns_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_integration_threads_of_events('DNS');
CREATE OR REPLACE TRIGGER trg_delete_integration_threads_of_docker_uptime_events
    AFTER DELETE ON docker_uptime_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_integration_threads_of_events('DOCKER');
CREATE OR REPLACE TRIGGER trg_delete_integration_threads_of_ssl_events
    AFTER DELETE ON ssl_event
    REFERENCING OLD TABLE AS old_events
    FOR EACH STATEMENT EXECUTE FUNCTION delete_integration_threads_of_events('SSL');
