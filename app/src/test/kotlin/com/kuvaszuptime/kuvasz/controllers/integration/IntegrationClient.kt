package com.kuvaszuptime.kuvasz.controllers.integration

import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import io.micronaut.http.client.annotation.Client

@Client("${API_V2_PREFIX}/integrations")
interface IntegrationClient : IntegrationOperations
