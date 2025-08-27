package com.kuvaszuptime.kuvasz.controllers

import io.micronaut.http.client.annotation.Client

@Client("$API_V2_PREFIX/integrations")
interface IntegrationClient : IntegrationOperations
