package com.kuvaszuptime.kuvasz.jooq;

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID;

/**
 * An event (uptime or SSL) that records the integrations which were notified when it was opened.
 */
public interface EventWithIntegrations {
    IntegrationID[] getIntegrations();

    EventWithIntegrations setIntegrations(IntegrationID[] value);
}
