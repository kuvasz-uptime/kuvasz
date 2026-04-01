alter table http_monitor
    add column sensitive_url boolean not null default false;
