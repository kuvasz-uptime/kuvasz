package com.kuvaszuptime.kuvasz.jooq;

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus;
import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;

public interface UptimeEventRecord extends EventWithIntegrations {
    Long getId();

    Long getMonitorId();

    UptimeStatus getStatus();

    @Nullable
    String getError();

    OffsetDateTime getStartedAt();

    OffsetDateTime getUpdatedAt();

    @Nullable
    OffsetDateTime getEndedAt();
}
