package com.kuvaszuptime.kuvasz.services.docker.client

import com.kuvaszuptime.kuvasz.services.docker.DockerHost

/**
 * The seam between "how bytes reach a daemon" and "what the Engine API means". Only `GET` is needed, because every
 * endpoint the checker uses is a read-only inspection.
 *
 * Kuvasz serves many hosts from one bean, so the host (and that host's monitor-specific timeout) is passed per call.
 */
interface DockerHttpTransport {

    fun get(host: DockerHost, path: String, timeoutMs: Int): DockerHttpResponse
}

data class DockerHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)
