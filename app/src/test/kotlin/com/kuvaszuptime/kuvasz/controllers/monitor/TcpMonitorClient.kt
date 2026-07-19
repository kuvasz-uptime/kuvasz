package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/tcp-monitors")
interface TcpMonitorClient : TcpMonitorOperations
