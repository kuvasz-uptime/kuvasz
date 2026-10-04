package com.kuvaszuptime.kuvasz.models.dto.proxy

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "The protocol Kuvasz uses to open a tunnel through a proxy")
enum class ProxyType {
    @Schema(description = "An HTTP proxy, tunneling every connection with the CONNECT method")
    HTTP,

    @Schema(description = "A SOCKS5 proxy")
    SOCKS5,
}
