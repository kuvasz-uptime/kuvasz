CREATE TYPE http_method AS ENUM ('GET', 'HEAD');

ALTER TABLE monitor
    ADD COLUMN latency_history_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN follow_redirects BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN force_no_cache BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN request_method http_method NOT NULL DEFAULT 'GET';
