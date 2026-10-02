-- Seeds ~50 monitors of every type with 35 days of uptime history, SSL certificates and maintenance windows, to try
-- out the dashboard with every period of its selector, and to have realistic data for screenshots. Every state the
-- dashboard can show is covered: up, down, paused (also while being down), pending, under maintenance (both up and
-- down), flaky and unreliable monitors, correlated outages of a whole category, monitors with a short history, valid,
-- expiring and invalid certificates, and active, upcoming, recurring, past and disabled maintenance windows.
--
-- Usage (with the localdev postgres container running, and the DB already migrated by the app at least once):
--   docker exec -i postgres16 psql -U postgres -v ON_ERROR_STOP=1 < localdev/seed-dashboard-data.sql
-- Options:
--   -v days=60        The length of the seeded history (35 days by default, to fill the 30 days period entirely)
--   -v healthy=true   Every monitor that would be down is up again, its last outage resolved a little later
--   -v wipe=true      DELETES EVERY MONITOR AND MAINTENANCE WINDOW first, not just the seeded ones, so only the seeded
--                     data shows up on the dashboard. The monitors of application-dev.yml are re-created on startup.
-- e.g.
--   docker exec -i postgres16 psql -U postgres -v ON_ERROR_STOP=1 -v healthy=true -v wipe=true \
--     < localdev/seed-dashboard-data.sql
--
-- It can be re-run any time: the previously seeded monitors (with their events) and maintenance windows are dropped
-- first, and every timestamp is relative to now().
--
-- Seed it while the app is RUNNING, and don't restart it while you're looking around: the checks of the monitors are
-- only scheduled on startup, so until then the seeded states stay exactly as they are (the hosts below don't exist).
-- Also, application-dev.yml defines the HTTP and the push monitors, and on startup every HTTP and push monitor that is
-- not in there is deleted, together with its history.
--
-- The two active maintenance windows end 50 and 95 minutes after seeding, then their monitors aren't under maintenance
-- anymore, so re-run it to start them over. The Docker monitors are on the "local" Docker host, which has to be
-- defined in application-local.yml.

\if :{?days}
\else
    \set days 35
\endif
\if :{?healthy}
\else
    \set healthy false
\endif
\if :{?wipe}
\else
    \set wipe false
\endif

SET search_path TO kuvasz;

BEGIN;

-- Every run produces the same shape of data
SELECT setseed(0.42);

CREATE TEMP TABLE seed_param ON COMMIT DROP AS
SELECT now() - make_interval(days => :days) AS history_start, :'healthy'::boolean AS healthy;

-- profile: how often and how long it goes down (solid < steady < flaky (often, but short) < bad (rarely, but long))
-- state: up, down, paused, paused_down (paused while it was down) or pending (no checks yet)
-- since: how long it's been down or paused
-- created_ago: when it was created, if not before the seeded history
CREATE TEMP TABLE seed_monitor (
    type        TEXT     NOT NULL,
    name        TEXT     NOT NULL,
    category    TEXT,
    target      TEXT,
    port        INTEGER,
    profile     TEXT     NOT NULL,
    state       TEXT     NOT NULL DEFAULT 'up',
    since       INTERVAL,
    created_ago INTERVAL,
    -- valid, invalid or the number of days until the certificate expires, for the HTTP monitors
    ssl         TEXT,
    id          BIGINT
) ON COMMIT DROP;

INSERT INTO seed_monitor (type, name, category, target, port, profile, state, since, created_ago, ssl)
VALUES
    -- HTTP
    ('http', 'Storefront', 'Storefront', 'https://shop.example.com', NULL, 'steady', 'up', NULL, NULL, 'valid'),
    ('http', 'Product catalog API', 'Storefront', 'https://api.example.com/catalog/health', NULL, 'flaky', 'up', NULL, NULL, 'valid'),
    ('http', 'Checkout service', 'Storefront', 'https://checkout.example.com/health', NULL, 'steady', 'down', '7 minutes', NULL, 'valid'),
    ('http', 'Image CDN', 'Storefront', 'https://cdn.example.com/ping', NULL, 'solid', 'up', NULL, NULL, '6'),
    ('http', 'Payment gateway', 'Payments', 'https://pay.example.com/health', NULL, 'bad', 'up', NULL, NULL, 'valid'),
    ('http', 'Fraud scoring API', 'Payments', 'https://fraud.example.com/v2/health', NULL, 'steady', 'up', NULL, NULL, 'valid'),
    ('http', 'Invoice portal', 'Payments', 'https://billing.example.com', NULL, 'solid', 'up', NULL, NULL, '19'),
    ('http', 'Search API', 'Search', 'https://search.example.com/health', NULL, 'flaky', 'up', NULL, NULL, 'valid'),
    ('http', 'Autocomplete', 'Search', 'https://search.example.com/suggest?q=ping', NULL, 'steady', 'down', '25 minutes', NULL, 'valid'),
    ('http', 'Grafana', 'Internal tools', 'https://grafana.internal.example.com', NULL, 'solid', 'up', NULL, NULL, 'invalid'),
    ('http', 'Wiki', 'Internal tools', 'https://wiki.internal.example.com', NULL, 'steady', 'paused', '3 days 5 hours', NULL, NULL),
    ('http', 'CI server', 'Internal tools', 'https://ci.internal.example.com', NULL, 'flaky', 'up', NULL, NULL, NULL),
    ('http', 'Marketing site', NULL, 'https://www.example.com', NULL, 'solid', 'up', NULL, NULL, 'valid'),
    ('http', 'Blog', NULL, 'https://blog.example.com', NULL, 'steady', 'up', NULL, '3 days 2 hours', 'valid'),
    ('http', 'Status webhook relay', NULL, 'https://hooks.example.com/health', NULL, 'solid', 'pending', NULL, '4 minutes', NULL),
    -- Push
    ('push', 'Nightly database backup', 'Data pipelines', NULL, NULL, 'solid', 'up', NULL, NULL, NULL),
    ('push', 'Daily analytics export', 'Data pipelines', NULL, NULL, 'bad', 'up', NULL, NULL, NULL),
    ('push', 'Search index rebuild', 'Search', NULL, NULL, 'steady', 'up', NULL, NULL, NULL),
    ('push', 'Settlement reconciliation job', 'Payments', NULL, NULL, 'steady', 'up', NULL, NULL, NULL),
    ('push', 'Newsletter sender', NULL, NULL, NULL, 'solid', 'paused', '9 days', NULL, NULL),
    -- ICMP
    ('icmp', 'Edge router (Vienna)', 'Infrastructure', '10.0.0.1', NULL, 'solid', 'up', NULL, NULL, NULL),
    ('icmp', 'Edge router (Frankfurt)', 'Infrastructure', '10.1.0.1', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('icmp', 'VPN gateway', 'Infrastructure', 'vpn.example.com', NULL, 'flaky', 'up', NULL, NULL, NULL),
    ('icmp', 'Backup NAS', 'Infrastructure', 'nas.internal.example.com', NULL, 'bad', 'down', '1 hour 50 minutes', NULL, NULL),
    ('icmp', 'Office firewall', 'Infrastructure', '192.168.10.1', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('icmp', 'Search node 1', 'Search', 'search-1.internal.example.com', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('icmp', 'Lab switch', NULL, '192.168.99.2', NULL, 'solid', 'paused_down', '2 days', NULL, NULL),
    -- TCP
    ('tcp', 'PostgreSQL primary', 'Infrastructure', 'db-1.internal.example.com', 5432, 'solid', 'up', NULL, NULL, NULL),
    ('tcp', 'PostgreSQL replica', 'Infrastructure', 'db-2.internal.example.com', 5432, 'steady', 'up', NULL, NULL, NULL),
    ('tcp', 'Redis cache', 'Infrastructure', 'cache.internal.example.com', 6379, 'flaky', 'up', NULL, NULL, NULL),
    ('tcp', 'Payment queue (RabbitMQ)', 'Payments', 'mq.internal.example.com', 5672, 'steady', 'down', '48 minutes', NULL, NULL),
    ('tcp', 'SMTP relay', 'Storefront', 'smtp.example.com', 587, 'steady', 'up', NULL, NULL, NULL),
    ('tcp', 'Elasticsearch', 'Search', 'search-1.internal.example.com', 9200, 'flaky', 'up', NULL, NULL, NULL),
    ('tcp', 'LDAP', 'Internal tools', 'ldap.internal.example.com', 636, 'solid', 'up', NULL, NULL, NULL),
    ('tcp', 'Legacy FTP', NULL, 'ftp.example.com', 21, 'bad', 'up', NULL, NULL, NULL),
    -- DNS
    ('dns', 'example.com A records', 'Storefront', 'example.com', NULL, 'solid', 'up', NULL, NULL, NULL),
    ('dns', 'shop.example.com CNAME', 'Storefront', 'shop.example.com', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('dns', 'pay.example.com', 'Payments', 'pay.example.com', NULL, 'solid', 'up', NULL, NULL, NULL),
    ('dns', 'Intranet resolver', 'Internal tools', 'intranet.example.com', NULL, 'flaky', 'up', NULL, NULL, NULL),
    ('dns', 'Mail MX records', NULL, 'example.com', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('dns', 'Legacy domain', NULL, 'example.net', NULL, 'bad', 'up', NULL, NULL, NULL),
    -- Docker
    ('docker', 'storefront-web', 'Storefront', 'storefront-web', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('docker', 'storefront-worker', 'Storefront', 'storefront-worker', NULL, 'flaky', 'up', NULL, NULL, NULL),
    ('docker', 'payments-api', 'Payments', 'payments-api', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('docker', 'etl-scheduler', 'Data pipelines', 'etl-scheduler', NULL, 'bad', 'up', NULL, NULL, NULL),
    ('docker', 'search-indexer', 'Search', 'search-indexer', NULL, 'steady', 'up', NULL, NULL, NULL),
    ('docker', 'grafana', 'Internal tools', 'grafana', NULL, 'solid', 'up', NULL, NULL, NULL),
    ('docker', 'legacy-cron', NULL, 'legacy-cron', NULL, 'steady', 'pending', NULL, '2 minutes', NULL);

-- The outages that hit a whole category (or a single monitor) at once, on top of the random ones, so the timeline has
-- some spikes, and the shorter periods have some incidents too
CREATE TEMP TABLE seed_shared_outage (
    category     TEXT,
    monitor_name TEXT,
    started_ago  INTERVAL NOT NULL,
    duration     INTERVAL NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_shared_outage (category, monitor_name, started_ago, duration)
VALUES
    ('Search', NULL, '26 days 3 hours', '25 minutes'),
    ('Infrastructure', NULL, '9 days 4 hours', '14 minutes'),
    ('Storefront', NULL, '2 days 6 hours', '12 minutes'),
    ('Payments', NULL, '5 hours 20 minutes', '6 minutes'),
    (NULL, 'CI server', '3 hours 5 minutes', '9 minutes'),
    (NULL, 'Product catalog API', '22 minutes', '3 minutes'),
    (NULL, 'Redis cache', '41 minutes', '2 minutes'),
    (NULL, 'storefront-worker', '9 hours', '6 minutes');

-- With healthy=true, the monitors that would be down are up again, after an outage of 20 minutes at most
UPDATE seed_monitor
SET state = 'up'
FROM seed_param
WHERE healthy AND state = 'down';

INSERT INTO seed_shared_outage (monitor_name, started_ago, duration)
SELECT m.name, m.since, least(m.since / 2, interval '20 minutes')
FROM seed_monitor m, seed_param
WHERE healthy AND m.since IS NOT NULL AND m.state = 'up';

-- The consecutive UP and DOWN events of a monitor between history_start and history_end, with random outages based on
-- the profile, plus the given extra ones, closed by an open event of open_status, last updated at open_updated_at
CREATE FUNCTION pg_temp.seed_uptime_events(
    profile TEXT,
    history_start TIMESTAMPTZ,
    history_end TIMESTAMPTZ,
    open_status uptime_status,
    open_updated_at TIMESTAMPTZ,
    extra_outages TSTZRANGE[],
    errors TEXT[]
)
    RETURNS TABLE (status uptime_status, error TEXT, started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ, updated_at TIMESTAMPTZ)
    LANGUAGE plpgsql
AS
$$
DECLARE
    outages      TSTZRANGE[] := extra_outages;
    outage_count INTEGER;
    outage_start TIMESTAMPTZ;
    outage       TSTZRANGE;
    up_since     TIMESTAMPTZ := history_start;
BEGIN
    outage_count := CASE profile
        WHEN 'solid' THEN floor(random() * 2)
        WHEN 'steady' THEN 2 + floor(random() * 3)
        WHEN 'flaky' THEN 8 + floor(random() * 8)
        WHEN 'bad' THEN 3 + floor(random() * 3)
    END;
    FOR i IN 1..outage_count LOOP
        outage_start := history_start + (history_end - history_start) * random();
        outages := outages || tstzrange(
            outage_start,
            outage_start + CASE profile
                WHEN 'flaky' THEN make_interval(secs => 60 + random() * 180)
                WHEN 'bad' THEN make_interval(secs => 900 + random() * 4500)
                ELSE make_interval(secs => 60 + random() * 840)
            END
        );
    END LOOP;

    -- The overlapping outages are merged, and the ones outside the history are trimmed, but an UP event is always
    -- left before the open one
    FOR outage IN
        SELECT unnest(range_agg(o * tstzrange(history_start, history_end - interval '1 minute')))
        FROM unnest(outages) AS o
        WHERE NOT isempty(o * tstzrange(history_start, history_end - interval '1 minute'))
    LOOP
        IF lower(outage) > up_since THEN
            RETURN QUERY SELECT 'UP'::uptime_status, NULL, up_since, lower(outage), lower(outage);
        END IF;
        RETURN QUERY SELECT 'DOWN'::uptime_status,
                            errors[1 + floor(random() * array_length(errors, 1))::int],
                            lower(outage),
                            upper(outage),
                            upper(outage);
        up_since := upper(outage);
    END LOOP;

    IF open_status = 'UP' THEN
        RETURN QUERY SELECT 'UP'::uptime_status, NULL, up_since, NULL::timestamptz, open_updated_at;
    ELSE
        RETURN QUERY SELECT 'UP'::uptime_status, NULL, up_since, history_end, history_end;
        RETURN QUERY SELECT 'DOWN'::uptime_status,
                            errors[1 + floor(random() * array_length(errors, 1))::int],
                            history_end,
                            NULL::timestamptz,
                            open_updated_at;
    END IF;
END
$$;

-- The events of every seeded monitor that has any
CREATE TEMP TABLE seed_event ON COMMIT DROP AS
SELECT m.type, m.name, e.*
FROM (SELECT * FROM seed_monitor WHERE state <> 'pending') m
CROSS JOIN seed_param p
CROSS JOIN LATERAL (
    SELECT greatest(p.history_start, now() - m.created_ago) AS history_start,
           CASE m.state
               WHEN 'up' THEN now()
               WHEN 'down' THEN now() - m.since
               WHEN 'paused' THEN now() - m.since
               -- It went down 20 minutes before it was paused
               WHEN 'paused_down' THEN now() - m.since - interval '20 minutes'
           END AS history_end,
           CASE WHEN m.state IN ('down', 'paused_down') THEN 'DOWN' ELSE 'UP' END::uptime_status AS open_status,
           CASE WHEN m.state IN ('paused', 'paused_down') THEN now() - m.since ELSE now() END AS open_updated_at
) h
CROSS JOIN LATERAL pg_temp.seed_uptime_events(
    m.profile,
    h.history_start,
    h.history_end,
    h.open_status,
    h.open_updated_at,
    ARRAY(
        SELECT tstzrange(now() - o.started_ago, now() - o.started_ago + o.duration)
        FROM seed_shared_outage o
        WHERE o.category = m.category OR o.monitor_name = m.name
    ),
    CASE m.type
        WHEN 'http' THEN ARRAY[
            'Request timed out after 30000 ms',
            'Unexpected status code: 503',
            'Unexpected status code: 502',
            'Connection refused'
        ]
        WHEN 'push' THEN ARRAY['Missed heartbeat', 'Missed heartbeat', 'Client signaled an explicit failure']
        WHEN 'icmp' THEN ARRAY['Packet loss: 100% (sent=3, received=0)', 'Host unreachable']
        WHEN 'tcp' THEN ARRAY[
            'Connection refused',
            'Connect timed out after 5000 ms',
            'Connect latency of 5210 ms exceeded the threshold of 1000 ms'
        ]
        WHEN 'dns' THEN ARRAY[
            'Expected response code NOERROR but the resolver returned SERVFAIL',
            'Resolution timed out after 5000 ms'
        ]
        WHEN 'docker' THEN ARRAY[
            'The container exited (137) after it was OOM killed',
            'The container exited (1)',
            'The container is restarting',
            'The container is running, but its healthcheck reports it unhealthy'
        ]
    END
) e;

\if :wipe
    DELETE FROM http_monitor;
    DELETE FROM push_monitor;
    DELETE FROM icmp_monitor;
    DELETE FROM tcp_monitor;
    DELETE FROM dns_monitor;
    DELETE FROM docker_monitor;
    DELETE FROM maintenance_window;
\else
    DELETE FROM http_monitor WHERE name IN (SELECT name FROM seed_monitor WHERE type = 'http');
    DELETE FROM push_monitor WHERE name IN (SELECT name FROM seed_monitor WHERE type = 'push');
    DELETE FROM icmp_monitor WHERE name IN (SELECT name FROM seed_monitor WHERE type = 'icmp');
    DELETE FROM tcp_monitor WHERE name IN (SELECT name FROM seed_monitor WHERE type = 'tcp');
    DELETE FROM dns_monitor WHERE name IN (SELECT name FROM seed_monitor WHERE type = 'dns');
    DELETE FROM docker_monitor WHERE name IN (SELECT name FROM seed_monitor WHERE type = 'docker');
\endif

-- The monitors, the paused ones last updated when they were paused
CREATE TEMP TABLE seed_monitor_row ON COMMIT DROP AS
SELECT m.*,
       m.state NOT IN ('paused', 'paused_down') AS enabled,
       now() - coalesce(m.created_ago, make_interval(days => :days + 1)) AS created_at,
       CASE WHEN m.state IN ('paused', 'paused_down') THEN now() - m.since ELSE now() END AS updated_at
FROM seed_monitor m;

INSERT INTO http_monitor (name, url, uptime_check_interval, enabled, ssl_check_enabled, category, created_at, updated_at)
SELECT name, target, 60, enabled, ssl IS NOT NULL, category, created_at, updated_at
FROM seed_monitor_row
WHERE type = 'http';

-- Daily (and weekly) jobs, so they stay up for a day after seeding, even if the app is running
INSERT INTO push_monitor (name, heartbeat_interval, grace_period, last_heartbeat, enabled, client_secret, category,
                          created_at, updated_at)
SELECT name,
       CASE WHEN enabled THEN 86400 ELSE 604800 END,
       600,
       CASE
           WHEN state = 'up' THEN now() - make_interval(mins => (10 + random() * 240)::int)
           ELSE now() - since
       END,
       enabled,
       gen_random_uuid()::text,
       category,
       created_at,
       updated_at
FROM seed_monitor_row
WHERE type = 'push';

INSERT INTO icmp_monitor (name, host, uptime_check_interval, enabled, category, created_at, updated_at)
SELECT name, target, 60, enabled, category, created_at, updated_at
FROM seed_monitor_row
WHERE type = 'icmp';

INSERT INTO tcp_monitor (name, host, port, uptime_check_interval, enabled, category, created_at, updated_at)
SELECT name, target, port, 60, enabled, category, created_at, updated_at
FROM seed_monitor_row
WHERE type = 'tcp';

INSERT INTO dns_monitor (name, host, uptime_check_interval, enabled, category, created_at, updated_at)
SELECT name, target, 120, enabled, category, created_at, updated_at
FROM seed_monitor_row
WHERE type = 'dns';

INSERT INTO docker_monitor (name, docker_host, container, uptime_check_interval, enabled, category, created_at,
                            updated_at)
SELECT name, 'local', target, 30, enabled, category, created_at, updated_at
FROM seed_monitor_row
WHERE type = 'docker';

UPDATE seed_monitor m
SET id = inserted.id
FROM (
    SELECT 'http' AS type, id, name FROM http_monitor
    UNION ALL
    SELECT 'push', id, name FROM push_monitor
    UNION ALL
    SELECT 'icmp', id, name FROM icmp_monitor
    UNION ALL
    SELECT 'tcp', id, name FROM tcp_monitor
    UNION ALL
    SELECT 'dns', id, name FROM dns_monitor
    UNION ALL
    SELECT 'docker', id, name FROM docker_monitor
) inserted
WHERE inserted.type = m.type AND inserted.name = m.name;

-- The uptime events
INSERT INTO http_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT m.id, e.status, e.error, e.started_at, e.ended_at, e.updated_at
FROM seed_event e JOIN seed_monitor m USING (type, name)
WHERE type = 'http';

INSERT INTO push_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT m.id, e.status, e.error, e.started_at, e.ended_at, e.updated_at
FROM seed_event e JOIN seed_monitor m USING (type, name)
WHERE type = 'push';

INSERT INTO icmp_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT m.id, e.status, e.error, e.started_at, e.ended_at, e.updated_at
FROM seed_event e JOIN seed_monitor m USING (type, name)
WHERE type = 'icmp';

INSERT INTO tcp_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT m.id, e.status, e.error, e.started_at, e.ended_at, e.updated_at
FROM seed_event e JOIN seed_monitor m USING (type, name)
WHERE type = 'tcp';

INSERT INTO dns_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT m.id, e.status, e.error, e.started_at, e.ended_at, e.updated_at
FROM seed_event e JOIN seed_monitor m USING (type, name)
WHERE type = 'dns';

INSERT INTO docker_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at, image)
SELECT m.id, e.status, e.error, e.started_at, e.ended_at, e.updated_at, 'registry.example.com/' || m.target || ':2026.9'
FROM seed_event e JOIN seed_monitor m USING (type, name)
WHERE type = 'docker';

-- The certificates: a valid one is good for 5-12 more weeks, an expiring one has been about to expire since it got
-- within the default threshold of 30 days, and an invalid one has been invalid for 2 days
INSERT INTO ssl_event (monitor_id, status, error, started_at, ended_at, updated_at, ssl_expiry_date)
SELECT m.id, c.status::ssl_status, c.error, c.started_at, c.ended_at, coalesce(c.ended_at, now()), c.expiry_date
FROM seed_monitor_row r
JOIN seed_monitor m USING (type, name)
CROSS JOIN LATERAL (
    SELECT 'VALID' AS status,
           NULL AS error,
           r.created_at AS started_at,
           CASE
               WHEN r.ssl = 'invalid' THEN now() - interval '2 days'
               WHEN r.ssl ~ '^\d+$' THEN now() + make_interval(days => r.ssl::int - 30)
           END AS ended_at,
           CASE
               WHEN r.ssl ~ '^\d+$' THEN now() + make_interval(days => r.ssl::int)
               ELSE now() + make_interval(days => 35 + (random() * 50)::int)
           END AS expiry_date
    UNION ALL
    SELECT 'WILL_EXPIRE', NULL, now() + make_interval(days => r.ssl::int - 30), NULL,
           now() + make_interval(days => r.ssl::int)
    WHERE r.ssl ~ '^\d+$'
    UNION ALL
    SELECT 'INVALID', 'PKIX path building failed: unable to find valid certification path to requested target',
           now() - interval '2 days', NULL, NULL
    WHERE r.ssl = 'invalid'
) c
WHERE r.type = 'http' AND r.ssl IS NOT NULL AND r.state <> 'pending';

-- The maintenance windows: two active ones (one for a category, one for a monitor), four starting within the 7 days
-- the dashboard looks ahead, so one of them doesn't fit in the list, a past and a disabled one
DELETE FROM maintenance_window
WHERE name IN (
    'Search cluster upgrade',
    'Database failover drill',
    'Nightly backup window',
    'Weekly OS patching',
    'Payment provider migration',
    'CDN provider switch',
    'Data warehouse migration',
    'Office network maintenance'
);

INSERT INTO maintenance_window (name, description, enabled, global, cron, start, duration, monitors, categories)
VALUES
    ('Search cluster upgrade', 'Upgrading Elasticsearch to the next major version', TRUE, FALSE, NULL,
     date_trunc('minute', now()) - interval '25 minutes', 'PT2H', ARRAY[]::text[], ARRAY['Search']),
    ('Database failover drill', NULL, TRUE, FALSE, NULL,
     date_trunc('minute', now()) - interval '10 minutes', 'PT1H', ARRAY['tcp:PostgreSQL replica'], ARRAY[]::text[]),
    ('Nightly backup window', NULL, TRUE, FALSE, '0 2 * * *', NULL, 'PT30M',
     ARRAY['push:Nightly database backup'], ARRAY[]::text[]),
    ('Weekly OS patching', 'Rolling reboots of every host', TRUE, FALSE, '0 3 * * SUN', NULL, 'PT1H',
     ARRAY[]::text[], ARRAY['Infrastructure']),
    ('Payment provider migration', NULL, TRUE, FALSE, NULL, date_trunc('hour', now()) + interval '2 days 22 hours',
     'PT3H', ARRAY['http:Payment gateway', 'tcp:Payment queue (RabbitMQ)', 'push:Settlement reconciliation job'],
     ARRAY[]::text[]),
    ('CDN provider switch', NULL, TRUE, FALSE, NULL, date_trunc('hour', now()) + interval '5 days 4 hours', 'PT45M',
     ARRAY['http:Image CDN', 'http:Storefront'], ARRAY[]::text[]),
    ('Data warehouse migration', NULL, TRUE, FALSE, NULL, date_trunc('hour', now()) - interval '3 days', 'PT4H',
     ARRAY[]::text[], ARRAY['Data pipelines']),
    ('Office network maintenance', NULL, FALSE, TRUE, NULL, date_trunc('hour', now()) + interval '1 day', 'PT2H',
     ARRAY[]::text[], ARRAY[]::text[]);

SELECT m.type,
       count(*) AS monitors,
       count(*) FILTER (WHERE m.state = 'down') AS down,
       (SELECT count(*) FROM seed_event e WHERE e.type = m.type) AS events
FROM seed_monitor m
GROUP BY m.type
ORDER BY m.type;

COMMIT;
