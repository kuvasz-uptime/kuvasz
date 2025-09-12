package com.kuvaszuptime.kuvasz.controllers

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/status-pages")
interface StatusPageClient : StatusPageOperations
