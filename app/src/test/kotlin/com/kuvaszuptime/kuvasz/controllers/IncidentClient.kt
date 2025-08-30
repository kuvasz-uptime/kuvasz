package com.kuvaszuptime.kuvasz.controllers

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/incidents")
interface IncidentClient : IncidentOperations
