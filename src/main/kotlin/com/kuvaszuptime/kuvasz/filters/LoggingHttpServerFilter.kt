package com.kuvaszuptime.kuvasz.filters

import com.kuvaszuptime.kuvasz.services.HttpCommunicationLogger
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.FilterOrderProvider
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import javax.inject.Inject

@Filter("/**")
@Requires(property = "http-communication-log.enabled", value = "true")
class LoggingHttpServerFilter
@Inject constructor(private val service: HttpCommunicationLogger) : HttpServerFilter, FilterOrderProvider {

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> =
        Flowable
            .fromPublisher(chain.proceed(request))
            .doOnNext { response -> service.log(request, response) }
}
