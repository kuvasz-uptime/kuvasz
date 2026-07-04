package com.kuvaszuptime.kuvasz.models.handlers

enum class IntegrationEventType {
    HTTP_UP,
    HTTP_DOWN,
    PUSH_UP,
    PUSH_DOWN,
    ICMP_UP,
    ICMP_DOWN,
    SSL_VALID,
    SSL_INVALID,
    SSL_WILL_EXPIRE,
    MAINTENANCE_START,
    MAINTENANCE_END,
}
