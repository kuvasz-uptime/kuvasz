create index http_uptime_event_open_idx on http_uptime_event (monitor_id) where ended_at is null;
create index push_uptime_event_open_idx on push_uptime_event (monitor_id) where ended_at is null;
create index icmp_uptime_event_open_idx on icmp_uptime_event (monitor_id) where ended_at is null;
create index tcp_uptime_event_open_idx on tcp_uptime_event (monitor_id) where ended_at is null;
create index dns_uptime_event_open_idx on dns_uptime_event (monitor_id) where ended_at is null;
create index docker_uptime_event_open_idx on docker_uptime_event (monitor_id) where ended_at is null;
create index ssl_event_open_idx on ssl_event (monitor_id) where ended_at is null;
