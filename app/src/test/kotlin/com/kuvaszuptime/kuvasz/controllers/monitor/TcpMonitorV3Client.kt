package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/tcp-monitors")
interface TcpMonitorV3Client : TcpMonitorV3Operations
