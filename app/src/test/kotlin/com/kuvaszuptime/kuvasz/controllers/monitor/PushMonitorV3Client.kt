package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/push-monitors")
interface PushMonitorV3Client : PushMonitorV3Operations
