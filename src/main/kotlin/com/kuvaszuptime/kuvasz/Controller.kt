package com.kuvaszuptime.kuvasz

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/")
class Controller {
    @Get("/hello")
    fun get(): HttpResponse<Any> = HttpResponse.ok()
}
