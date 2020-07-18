package com.akobor.kuvasz.filters

import com.akobor.kuvasz.services.HttpLoggingService
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
class LoggingHttpServerFilter
@Inject constructor(private val service: HttpLoggingService) : HttpServerFilter, FilterOrderProvider {

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> =
        Flowable
            .fromPublisher(chain.proceed(request))
            .doOnNext { response -> service.log(request, response) }
}
