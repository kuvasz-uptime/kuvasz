package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/icmp-monitors")
interface IcmpMonitorV3Client : IcmpMonitorV3Operations
