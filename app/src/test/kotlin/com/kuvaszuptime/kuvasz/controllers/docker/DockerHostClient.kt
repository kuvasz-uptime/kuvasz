package com.kuvaszuptime.kuvasz.controllers.docker

import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import io.micronaut.http.client.annotation.Client

@Client("${API_V2_PREFIX}/docker-hosts")
interface DockerHostClient : DockerHostOperations
