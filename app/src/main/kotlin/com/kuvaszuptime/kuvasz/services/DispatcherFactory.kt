package com.kuvaszuptime.kuvasz.services

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Factory
class DispatcherFactory {

    @Singleton
    @Suppress("InjectDispatcher")
    fun provideDispatcher() = Dispatchers.IO
}
