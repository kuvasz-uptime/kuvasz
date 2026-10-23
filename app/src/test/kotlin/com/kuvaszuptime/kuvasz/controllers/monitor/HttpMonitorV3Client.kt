package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/http-monitors")
interface HttpMonitorV3Client : HttpMonitorV3Operations
