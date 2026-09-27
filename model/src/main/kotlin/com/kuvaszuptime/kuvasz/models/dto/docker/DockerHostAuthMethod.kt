package com.kuvaszuptime.kuvasz.models.dto.docker

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "How Kuvasz is authenticated to a Docker daemon")
enum class DockerHostAuthMethod {
    @Schema(description = "A local unix socket, guarded by the permissions of the socket file")
    UNIX_SOCKET,

    @Schema(description = "Plaintext TCP, the daemon does not authenticate the client")
    NONE,

    @Schema(description = "TLS without a client certificate, so the daemon does not authenticate the client")
    TLS,

    @Schema(description = "TLS with a client certificate, which the daemon authenticates the client by")
    MUTUAL_TLS,
}
