ALTER TABLE http_monitor ADD COLUMN category TEXT DEFAULT NULL;
ALTER TABLE push_monitor ADD COLUMN category TEXT DEFAULT NULL;
ALTER TABLE icmp_monitor ADD COLUMN category TEXT DEFAULT NULL;
ALTER TABLE tcp_monitor ADD COLUMN category TEXT DEFAULT NULL;
ALTER TABLE dns_monitor ADD COLUMN category TEXT DEFAULT NULL;

ALTER TABLE status_page ADD COLUMN categories TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
ALTER TABLE maintenance_window ADD COLUMN categories TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];

CREATE INDEX IF NOT EXISTS "status_page_categories_idx" ON status_page USING GIN (categories);
CREATE INDEX IF NOT EXISTS "maintenance_categories_idx" ON maintenance_window USING GIN (categories);

CREATE INDEX IF NOT EXISTS "http_monitor_category_idx" ON http_monitor USING BTREE (category) WHERE category IS NOT NULL;
CREATE INDEX IF NOT EXISTS "push_monitor_category_idx" ON push_monitor USING BTREE (category) WHERE category IS NOT NULL;
CREATE INDEX IF NOT EXISTS "icmp_monitor_category_idx" ON icmp_monitor USING BTREE (category) WHERE category IS NOT NULL;
CREATE INDEX IF NOT EXISTS "tcp_monitor_category_idx" ON tcp_monitor USING BTREE (category) WHERE category IS NOT NULL;
CREATE INDEX IF NOT EXISTS "dns_monitor_category_idx" ON dns_monitor USING BTREE (category) WHERE category IS NOT NULL;

ALTER TABLE status_page ADD COLUMN display_categories BOOLEAN NOT NULL DEFAULT TRUE;
