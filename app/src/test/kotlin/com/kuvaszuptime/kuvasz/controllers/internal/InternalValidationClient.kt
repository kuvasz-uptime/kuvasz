package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.controllers.API_INTERNAL_PREFIX
import io.micronaut.http.client.annotation.Client

@Client("$API_INTERNAL_PREFIX/validation")
interface InternalValidationClient : InternalValidationOperations
