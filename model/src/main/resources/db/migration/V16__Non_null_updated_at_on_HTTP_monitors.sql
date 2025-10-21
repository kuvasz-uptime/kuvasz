UPDATE http_monitor
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE http_monitor
    ALTER COLUMN updated_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET NOT NULL;
