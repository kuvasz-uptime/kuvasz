-- Lets a monitor keep running while Kuvasz's global connectivity check considers the host offline, which is what
-- monitors targeting the local network need: their targets don't depend on outbound internet connectivity at all.
ALTER TABLE http_monitor ADD COLUMN ignore_connectivity_check BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE push_monitor ADD COLUMN ignore_connectivity_check BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE icmp_monitor ADD COLUMN ignore_connectivity_check BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE tcp_monitor ADD COLUMN ignore_connectivity_check BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE dns_monitor ADD COLUMN ignore_connectivity_check BOOLEAN DEFAULT FALSE NOT NULL;
