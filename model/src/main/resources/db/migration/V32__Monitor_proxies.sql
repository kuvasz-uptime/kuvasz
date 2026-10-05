ALTER TABLE http_monitor
    ADD COLUMN proxy TEXT;

CREATE INDEX http_monitor_proxy_idx ON http_monitor USING btree (proxy);

ALTER TABLE tcp_monitor
    ADD COLUMN proxy TEXT;

CREATE INDEX tcp_monitor_proxy_idx ON tcp_monitor USING btree (proxy);
