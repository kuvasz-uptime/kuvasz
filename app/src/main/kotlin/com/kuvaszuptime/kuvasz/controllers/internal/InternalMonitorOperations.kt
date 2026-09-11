package com.kuvaszuptime.kuvasz.controllers.internal

import io.micronaut.http.annotation.Get

interface InternalMonitorOperations {

    @Get
    fun getCategories(): List<String>
}
