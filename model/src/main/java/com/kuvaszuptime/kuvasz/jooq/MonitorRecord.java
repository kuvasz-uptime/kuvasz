package com.kuvaszuptime.kuvasz.jooq;

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID;
import com.kuvaszuptime.kuvasz.models.monitor.WithCategory;
import com.kuvaszuptime.kuvasz.models.monitor.WithDisplayName;

import java.time.OffsetDateTime;

public interface MonitorRecord extends WithCategory, WithDisplayName {
    Long getId();

    String getName();

    String getDisplayName();

    Boolean getEnabled();

    IntegrationID[] getIntegrations();

    OffsetDateTime getCreatedAt();

    OffsetDateTime getUpdatedAt();

    Long getFailureCountThreshold();

    Boolean getIgnoreConnectivityCheck();
}
