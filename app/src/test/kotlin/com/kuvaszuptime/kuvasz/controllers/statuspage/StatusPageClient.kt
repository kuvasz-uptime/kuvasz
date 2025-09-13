package com.kuvaszuptime.kuvasz.controllers.statuspage

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/status-pages")
interface StatusPageClient : StatusPageOperations
