-- Custom request headers are withheld from other origins during redirects, unless it's explicitly enabled
ALTER TABLE http_monitor
    ADD COLUMN cross_origin_header_propagation BOOLEAN NOT NULL DEFAULT FALSE;
