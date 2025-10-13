package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/push-monitors")
interface PushMonitorClient : PushMonitorOperations
