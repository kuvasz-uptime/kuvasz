-- Seeds an HTTP, an ICMP, a TCP and a DNS monitor with a long history of metrics and incidents, to have realistic data
-- for screenshots, and to try the charts of the monitor details pages with the longer periods (7 days, 30 days).
--
-- Usage (with the localdev postgres container running, and the DB already migrated by the app at least once):
--   docker exec -i postgres16 psql -U postgres -v ON_ERROR_STOP=1 < localdev/seed-chart-data.sql
-- Denser or longer histories can be seeded too, e.g. a check in every 20 seconds, for 30 days:
--   docker exec -i postgres16 psql -U postgres -v ON_ERROR_STOP=1 -v interval_seconds=20 -v days=30 \
--     < localdev/seed-chart-data.sql
--
-- It can be re-run any time: the previously seeded monitors (with their events and logs) are dropped first, and every
-- timestamp is relative to now(). The monitors are only scheduled for checks once the app is (re)started.
-- The nightly cleanup drops the logs older than the latency data retention, which is set to 30 days in
-- application-dev.yml.

\if :{?interval_seconds}
\else
    \set interval_seconds 60
\endif
\if :{?days}
\else
    \set days 30
\endif

SET search_path TO kuvasz;

BEGIN;

-- Every run produces the same shape of data
SELECT setseed(0.42);

DELETE FROM http_monitor WHERE name = 'Webshop API';
DELETE FROM icmp_monitor WHERE name = 'Core router';
DELETE FROM tcp_monitor WHERE name = 'Primary database';
DELETE FROM dns_monitor WHERE name = 'Company domain';

-- The outages shared by every seeded monitor: one in every couple of days at a random time, plus a few fixed ones, so
-- every selectable period has some incident markers to show
CREATE TEMP TABLE seed_outage ON COMMIT DROP AS
WITH slot AS (
    SELECT slot_index,
           make_interval(secs => (:days - 2) * 86400.0 / 12) AS slot_length
    FROM generate_series(0, 11) AS slot_index
),
random_outage AS (
    SELECT now() - make_interval(days => :days) + interval '6 hours'
               + slot_length * slot_index + slot_length * (random() * 0.5) AS started_at,
           make_interval(mins => (5 + random() * 85)::int) AS duration
    FROM slot
),
fixed_outage(started_at, duration) AS (
    VALUES
        -- Started before the seeded period, so only its end is within the longest view
        (now() - make_interval(days => :days) - interval '2 hours', interval '3 hours'),
        (now() - interval '20 hours', interval '35 minutes'),
        (now() - interval '3 hours', interval '12 minutes'),
        (now() - interval '40 minutes', interval '6 minutes')
)
SELECT started_at, started_at + duration AS ended_at
FROM random_outage
UNION ALL
SELECT started_at, started_at + duration
FROM fixed_outage;

-- The consecutive UP and DOWN events of a monitor around the outages, with a random error picked for every outage
CREATE FUNCTION pg_temp.seed_uptime_events(errors TEXT[])
    RETURNS TABLE (status uptime_status, error TEXT, started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ)
    LANGUAGE sql
AS
$$
WITH ordered AS (
    SELECT o.started_at,
           o.ended_at,
           lag(o.ended_at) OVER (ORDER BY o.started_at) AS previous_ended_at
    FROM seed_outage o
)
SELECT 'DOWN'::uptime_status, errors[1 + floor(random() * array_length(errors, 1))::int], started_at, ended_at
FROM ordered
UNION ALL
SELECT 'UP'::uptime_status, NULL, previous_ended_at, started_at
FROM ordered
WHERE previous_ended_at IS NOT NULL
UNION ALL
-- Still UP since the last outage
SELECT 'UP'::uptime_status, NULL, max(ended_at), NULL
FROM ordered
$$;

-- The timestamps of the checks, whether they fell into an outage, and a daily cycle of the load (peaking once a day)
CREATE TEMP TABLE seed_check ON COMMIT DROP AS
SELECT checked_at,
       EXISTS (
           SELECT 1 FROM seed_outage o WHERE checked_at >= o.started_at AND checked_at < o.ended_at
       ) AS is_down,
       1 + 0.35 * sin(2 * pi() * (extract(EPOCH FROM checked_at) / 86400.0 - 0.375)) AS daily_factor
FROM generate_series(
    now() - make_interval(days => :days),
    now(),
    make_interval(secs => :interval_seconds)
) AS checked_at;

-- HTTP
INSERT INTO http_monitor (name, url, uptime_check_interval, category, created_at, updated_at)
VALUES ('Webshop API', 'https://example.com', :interval_seconds, 'Demo', now() - make_interval(days => :days + 1), now())
RETURNING id AS http_monitor_id
\gset

INSERT INTO http_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT :http_monitor_id, status, error, started_at, ended_at, coalesce(ended_at, now())
FROM pg_temp.seed_uptime_events(ARRAY[
    'Request timed out after 30000 ms',
    'Unexpected status code: 503',
    'Connection refused'
]);

-- Only the successful checks have a latency log
INSERT INTO http_latency_log (monitor_id, latency, created_at)
SELECT :http_monitor_id,
       round(180 * daily_factor * (0.8 + 0.4 * random()) * CASE WHEN random() < 0.003 THEN 5 ELSE 1 END),
       checked_at
FROM seed_check
WHERE NOT is_down;

-- ICMP
INSERT INTO icmp_monitor (name, host, uptime_check_interval, category, created_at, updated_at)
VALUES ('Core router', '1.1.1.1', :interval_seconds, 'Demo', now() - make_interval(days => :days + 1), now())
RETURNING id AS icmp_monitor_id
\gset

INSERT INTO icmp_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT :icmp_monitor_id, status, error, started_at, ended_at, coalesce(ended_at, now())
FROM pg_temp.seed_uptime_events(ARRAY[
    'Packet loss: 100% (sent=3, received=0)',
    'Host unreachable'
]);

INSERT INTO icmp_metrics_log (monitor_id, latency_ms, packet_loss_percentage, created_at)
SELECT :icmp_monitor_id,
       CASE
           WHEN is_down THEN NULL
           ELSE round(24 * daily_factor * (0.8 + 0.4 * random()) * CASE WHEN random() < 0.003 THEN 4 ELSE 1 END)
       END,
       CASE
           WHEN is_down THEN 100
           WHEN random() < 0.01 THEN 67
           WHEN random() < 0.04 THEN 33
           ELSE 0
       END,
       checked_at
FROM seed_check;

-- TCP
INSERT INTO tcp_monitor (name, host, port, uptime_check_interval, category, created_at, updated_at)
VALUES ('Primary database', 'localhost', 5432, :interval_seconds, 'Demo', now() - make_interval(days => :days + 1), now())
RETURNING id AS tcp_monitor_id
\gset

INSERT INTO tcp_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT :tcp_monitor_id, status, error, started_at, ended_at, coalesce(ended_at, now())
FROM pg_temp.seed_uptime_events(ARRAY[
    'Connection refused',
    'Connect timed out after 5000 ms',
    'Connect latency of 5210 ms exceeded the threshold of 1000 ms'
]);

INSERT INTO tcp_metrics_log (monitor_id, latency_ms, created_at)
SELECT :tcp_monitor_id,
       CASE
           WHEN is_down THEN NULL
           ELSE round(4 * daily_factor * (0.7 + 0.6 * random()) * CASE WHEN random() < 0.003 THEN 6 ELSE 1 END)
       END,
       checked_at
FROM seed_check;

-- DNS
INSERT INTO dns_monitor (name, host, uptime_check_interval, category, created_at, updated_at)
VALUES ('Company domain', 'example.com', :interval_seconds, 'Demo', now() - make_interval(days => :days + 1), now())
RETURNING id AS dns_monitor_id
\gset

INSERT INTO dns_uptime_event (monitor_id, status, error, started_at, ended_at, updated_at)
SELECT :dns_monitor_id, status, error, started_at, ended_at, coalesce(ended_at, now())
FROM pg_temp.seed_uptime_events(ARRAY[
    'Unexpected response code: SERVFAIL',
    'Resolution timed out after 5000 ms'
]);

INSERT INTO dns_metrics_log (monitor_id, latency_ms, created_at)
SELECT :dns_monitor_id,
       CASE
           WHEN is_down THEN NULL
           ELSE round(35 * daily_factor * (0.8 + 0.4 * random()) * CASE WHEN random() < 0.003 THEN 5 ELSE 1 END)
       END,
       checked_at
FROM seed_check;

COMMIT;

SELECT 'HTTP' AS type, :http_monitor_id AS monitor_id, '/http-monitors/' || :http_monitor_id AS details_page
UNION ALL
SELECT 'ICMP', :icmp_monitor_id, '/icmp-monitors/' || :icmp_monitor_id
UNION ALL
SELECT 'TCP', :tcp_monitor_id, '/tcp-monitors/' || :tcp_monitor_id
UNION ALL
SELECT 'DNS', :dns_monitor_id, '/dns-monitors/' || :dns_monitor_id;
