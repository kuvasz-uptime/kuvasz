-- NULL stands for the default gray palette, so the pages created before this column follow it too
ALTER TABLE status_page ADD COLUMN theme_base TEXT DEFAULT NULL;
