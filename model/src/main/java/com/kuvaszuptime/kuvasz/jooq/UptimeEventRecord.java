package com.kuvaszuptime.kuvasz.jooq;

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus;

import java.time.OffsetDateTime;

public interface UptimeEventRecord {
//    UptimeEventRecord setMonitorId(Long value);
//
//    UptimeEventRecord setStatus(UptimeStatus value);
//
//    UptimeEventRecord setError(String value);
//
//    UptimeEventRecord setStartedAt(OffsetDateTime value);
//
//    UptimeEventRecord setUpdatedAt(OffsetDateTime value);
//
//    UptimeEventRecord setEndedAt(OffsetDateTime value);

    Long getId();

    Long getMonitorId();

    UptimeStatus getStatus();

    String getError();

    OffsetDateTime getStartedAt();

    OffsetDateTime getUpdatedAt();

    OffsetDateTime getEndedAt();
}
