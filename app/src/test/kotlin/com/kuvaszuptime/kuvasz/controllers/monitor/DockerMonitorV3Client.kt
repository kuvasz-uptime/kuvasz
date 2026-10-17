package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/docker-monitors")
interface DockerMonitorV3Client : DockerMonitorV3Operations
