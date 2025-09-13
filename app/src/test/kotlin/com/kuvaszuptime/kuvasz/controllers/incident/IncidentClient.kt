package com.kuvaszuptime.kuvasz.controllers.incident

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/incidents")
interface IncidentClient : IncidentOperations
