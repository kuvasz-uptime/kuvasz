CREATE TABLE pagerduty_incident
(
    id                SERIAL PRIMARY KEY,
    uptime_event_id   INTEGER                  NULL REFERENCES uptime_event (id) ON DELETE CASCADE,
    ssl_event_id      INTEGER                  NULL REFERENCES ssl_event (id) ON DELETE CASCADE,
    deduplication_key VARCHAR(100)             NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ended_at          TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS "pagerduty_incident_dedup_key_idx" ON "pagerduty_incident" USING btree ("deduplication_key" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "pagerduty_incident_uptime_event_idx" ON "pagerduty_incident" USING btree ("uptime_event_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "pagerduty_incident_ssl_event_idx" ON "pagerduty_incident" USING btree ("ssl_event_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "pagerduty_incident_ended_at_idx" ON "pagerduty_incident" USING btree ("ended_at" ASC NULLS LAST);

ALTER TABLE monitor
    ADD COLUMN pagerduty_integration_key VARCHAR NULL;
