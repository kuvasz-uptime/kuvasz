ALTER TABLE monitor
    ADD COLUMN expected_status_codes INTEGER[] NOT NULL DEFAULT ARRAY[]::INTEGER[],
    ADD COLUMN response_time_threshold_millis INTEGER DEFAULT NULL,
    ADD COLUMN expected_keyword TEXT DEFAULT NULL,
    ADD COLUMN expected_keyword_case_sensitive BOOLEAN DEFAULT FALSE,
    ADD COLUMN expected_keyword_negated BOOLEAN DEFAULT FALSE;
