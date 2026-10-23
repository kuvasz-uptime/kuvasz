package com.kuvaszuptime.kuvasz.controllers.statuspage

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/status-pages")
interface StatusPageV3Client : StatusPageV3Operations
