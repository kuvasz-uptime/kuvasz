package com.akobor.kuvasz.util

import java.util.Optional

fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)

@Suppress("UNCHECKED_CAST")
fun Optional<*>.unnest(): Optional<*> =
    if (isPresent) {
        when (val inner = get()) {
            is Optional<*> -> (inner as Optional<Any>).unnest()
            else -> this
        }
    } else {
        this
    }
