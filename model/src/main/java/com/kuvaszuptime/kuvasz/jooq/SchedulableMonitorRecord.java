package com.kuvaszuptime.kuvasz.jooq;

/**
 * Marks the monitor records whose checks are scheduled periodically, based on their own check interval.
 */
public interface SchedulableMonitorRecord extends MonitorRecord {
    Integer getUptimeCheckInterval();
}
