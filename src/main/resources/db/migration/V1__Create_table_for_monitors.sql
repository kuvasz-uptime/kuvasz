CREATE SCHEMA IF NOT EXISTS kuvasz;

CREATE TABLE monitor
(
    id                    SERIAL PRIMARY KEY,
    name                  VARCHAR(255)             NOT NULL,
    url                   TEXT                     NOT NULL,
    uptime_check_interval INTEGER                  NOT NULL,
    enabled               BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE
);

COMMENT ON COLUMN monitor.name IS 'Monitor''s name';
COMMENT ON COLUMN monitor.url IS 'URL to check';
COMMENT ON COLUMN monitor.uptime_check_interval IS 'Uptime checking interval in seconds';
COMMENT ON COLUMN monitor.enabled IS 'Flag to toggle the monitor';
