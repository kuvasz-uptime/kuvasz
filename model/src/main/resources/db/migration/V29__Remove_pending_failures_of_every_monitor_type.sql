-- V18 only removed the pending failures of deleted HTTP and push monitors, so the other monitor types left theirs behind

create trigger trg_remove_pending_failures_of_icmp_monitor
    after delete
    on icmp_monitor
    for each row
execute function delete_pending_failures_of_monitor();
create trigger trg_remove_pending_failures_of_tcp_monitor
    after delete
    on tcp_monitor
    for each row
execute function delete_pending_failures_of_monitor();
create trigger trg_remove_pending_failures_of_dns_monitor
    after delete
    on dns_monitor
    for each row
execute function delete_pending_failures_of_monitor();
create trigger trg_remove_pending_failures_of_docker_monitor
    after delete
    on docker_monitor
    for each row
execute function delete_pending_failures_of_monitor();

-- The ones left behind so far
DELETE
FROM pending_failure
WHERE monitor_id NOT IN (SELECT id FROM http_monitor
                         UNION ALL
                         SELECT id FROM push_monitor
                         UNION ALL
                         SELECT id FROM icmp_monitor
                         UNION ALL
                         SELECT id FROM tcp_monitor
                         UNION ALL
                         SELECT id FROM dns_monitor
                         UNION ALL
                         SELECT id FROM docker_monitor);
