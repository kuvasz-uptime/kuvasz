package com.kuvaszuptime.kuvasz.controllers.incident

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/incidents")
interface IncidentV3Client : IncidentV3Operations
