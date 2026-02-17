package com.kuvaszuptime.kuvasz.jooq;

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID;

import java.time.OffsetDateTime;

public interface MonitorRecord {
    Long getId();

    String getName();

    Boolean getEnabled();

    IntegrationID[] getIntegrations();

    OffsetDateTime getCreatedAt();

    OffsetDateTime getUpdatedAt();

    Long getFailureCountThreshold();
}
