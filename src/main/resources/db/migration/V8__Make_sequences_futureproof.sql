alter sequence monitor_id_seq as bigint;
alter table monitor alter id type bigint;

alter sequence latency_log_id_seq as bigint;
alter table latency_log alter id type bigint;
alter table latency_log alter monitor_id type bigint;

alter sequence uptime_event_id_seq as bigint;
alter table uptime_event alter id type bigint;
alter table uptime_event alter monitor_id type bigint;

alter sequence ssl_event_id_seq as bigint;
alter table ssl_event alter id type bigint;
alter table ssl_event alter monitor_id type bigint;
