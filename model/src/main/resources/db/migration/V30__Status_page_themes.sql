ALTER TABLE status_page ADD COLUMN theme_base TEXT;
-- The pages created before this column get the default gray palette
UPDATE status_page SET theme_base = 'GRAY';
ALTER TABLE status_page ALTER COLUMN theme_base SET NOT NULL;
