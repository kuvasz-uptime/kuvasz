package com.kuvaszuptime.kuvasz.controllers.internal

import io.micronaut.http.annotation.Get

interface InternalCategoryOperations {

    @Get
    fun getCategories(): List<String>
}
