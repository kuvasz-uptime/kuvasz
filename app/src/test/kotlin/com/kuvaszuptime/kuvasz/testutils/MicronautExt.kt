package com.kuvaszuptime.kuvasz.testutils

import io.micronaut.context.ApplicationContext

inline fun <reified T : Any> ApplicationContext.getBean(): T = getBean(T::class.java)
