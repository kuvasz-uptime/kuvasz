ALTER TABLE monitor
    ADD COLUMN integrations TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];

ALTER TABLE monitor
    DROP COLUMN pagerduty_integration_key;
