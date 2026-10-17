package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/dns-monitors")
interface DnsMonitorV3Client : DnsMonitorV3Operations
