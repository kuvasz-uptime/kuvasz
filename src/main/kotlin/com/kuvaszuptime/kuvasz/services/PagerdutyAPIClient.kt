package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.Single

@Client("https://events.pagerduty.com/v2")
@Retryable
interface PagerdutyAPIClient {

    @Post("/enqueue")
    fun triggerAlert(@Body request: PagerdutyTriggerRequest): Single<String>

    @Post("/enqueue")
    fun resolveAlert(@Body request: PagerdutyResolveRequest): Single<String>
}
