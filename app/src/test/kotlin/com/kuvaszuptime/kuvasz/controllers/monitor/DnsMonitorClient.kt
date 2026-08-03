package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/dns-monitors")
interface DnsMonitorClient : DnsMonitorOperations
