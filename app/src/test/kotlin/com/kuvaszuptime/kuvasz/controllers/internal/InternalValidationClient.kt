package com.kuvaszuptime.kuvasz.controllers.internal

import io.micronaut.http.client.annotation.Client

@Client("/api/internal/validation")
interface InternalValidationClient : InternalValidationOperations
