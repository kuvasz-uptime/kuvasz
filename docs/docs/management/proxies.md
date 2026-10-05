# Proxies

A **proxy** is an outbound HTTP or SOCKS5 proxy that the checks of your [**HTTP**](http-monitors.md#proxy) and [**TCP monitors**](tcp-monitors.md#proxy) can be routed through, instead of connecting to their targets directly. A few typical use cases:

- your network only lets outbound traffic through a **corporate egress proxy**,
- some of your targets are only reachable **inside a private network**, e.g. through a bastion host with `ssh -D`, or a SOCKS5 proxy deployed into that network,
- you'd like to check a service **from a different network position** than the one _Kuvasz_ runs at.

Proxies are **assigned per monitor, explicitly**: only the monitors that refer to a proxy go through it, every other monitor (and every other part of _Kuvasz_) keeps connecting directly.

Just like the [Docker hosts](docker-hosts.md), proxies are **defined in your configuration file**, and every monitor refers to its proxy **by name**. They can't be created or modified on the UI or through the API, so their credentials stay in your configuration file (or in your environment variables, or in your _Kubernetes_ secrets) instead of the database.

## Configuration

<!-- md:version 4.5.0 -->

```yaml title="Proxy examples"
proxies:
  # A corporate egress proxy, which requires authentication
  - name: "corporate-egress"
    url: "http://10.0.0.10:3128"
    username: "kuvasz"
    password: "${EGRESS_PROXY_PASSWORD}"
  # A SOCKS5 proxy, e.g. `ssh -D 0.0.0.0:1080 bastion` running next to Kuvasz
  - name: "office-network"
    url: "socks5://bastion-tunnel:1080"
```

!!! tip "Keeping the credentials out of your configuration file"

    You can use `${VARIABLE_NAME}` placeholders in the YAML, as described in the [**Keeping secrets out of your configuration file**](examples.md#keeping-secrets-out-of-your-configuration-file) recipe.

Every proxy is **validated at startup**, and _Kuvasz_ **refuses to start** with a clear error message if a proxy has a duplicate or an empty name, an invalid or unsupported URL, or only one of the `username` and `password` properties. The configured proxies are logged at startup too (without their credentials).

### Name

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type `string` -->
<!-- md:yaml_prop `name` -->

The name of the proxy, which **must be unique**. Monitors refer to their proxy by this name, so if you rename a proxy, it's the same as [removing it](#removing-or-renaming-a-proxy) and adding a new one.

### URL

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type `string` -->
<!-- md:yaml_prop `url` -->

The address of the proxy, consisting of a **scheme**, a **host** and a **port**, nothing else. The scheme decides the protocol _Kuvasz_ speaks to the proxy:

| URL                   | Protocol                                                              |
|-----------------------|-----------------------------------------------------------------------|
| `http://host:port`    | **HTTP proxy**, every connection is opened with an HTTP `CONNECT`     |
| `socks5://host:port`  | **SOCKS5 proxy**                                                      |

- The **port is required**, since there is no port every proxy listens on by default.
- The host can be a hostname or an IP address. It's resolved by _Kuvasz_, while the names of the **monitored targets are resolved by the proxy**, see [below](#how-the-checks-go-through-a-proxy).
- **Credentials in the URL** (e.g. `http://user:pass@host:3128`) are rejected, use the [`username` and `password`](#username-and-password) properties instead.
- `https://` URLs (i.e. **TLS between _Kuvasz_ and the proxy**) are **not supported**. If your proxy can only be reached over TLS, take a look at the [**stunnel sidecar recipe**](#reaching-a-proxy-over-tls-with-a-stunnel-sidecar).

### Username and password

<!-- md:version 4.5.0 -->
<!-- md:default empty -->
<!-- md:type `string` -->
<!-- md:yaml_prop `username`, `password` -->

The credentials _Kuvasz_ authenticates to the proxy with. They can only be configured **together**, and they're **never exposed** through the UI, the API or the MCP server.

- An **HTTP proxy** receives them as a `Proxy-Authorization: Basic ...` header.
- A **SOCKS5 proxy** receives them through the username/password authentication of the protocol ([RFC 1929](https://www.rfc-editor.org/rfc/rfc1929)), so they can only contain **ASCII characters**, and they can be **at most 255 bytes** long.

!!! warning

    Neither of them is encrypted on the way to the proxy, see the [**limitations**](#limitations).

## Referencing proxies from monitors

A monitor refers to its proxy by the proxy's [name](#name), through its optional **`proxy`** field, which can be set on the **UI**, through the **API** and the **MCP server**, in your **YAML** configuration, and it's part of the **YAML backups** too. If it's empty, the monitor is checked over a **direct connection**.

```yaml title="kuvasz.yml"
http-monitors:
  - name: "Intranet"
    url: "https://intranet.corp.local"
    uptime-check-interval: 60
    ssl-check-enabled: true
    proxy: "corporate-egress"
tcp-monitors:
  - name: "Office database"
    host: "db.office.local"
    port: 5432
    uptime-check-interval: 60
    proxy: "office-network"
```

What goes through the proxy:

- **HTTP monitors**: the uptime check (including **every hop of a redirect chain**), and the [**SSL check**](../features/ssl-monitoring.md) too.
- **TCP monitors**: the connection to the monitored host and port.

A newly set proxy **has to be configured**, otherwise the change is rejected with a validation error, whether it comes from the UI, the API, the MCP server, a YAML backup being imported, or a new monitor in your YAML file (which makes the startup fail).

!!! info "There is no fallback to a direct connection"

    If a check through a proxy fails, for whatever reason, the monitor is reported **DOWN**, and _Kuvasz_ never retries it over a direct connection. A direct connection would check the target from a **different network position**, so its result could be misleading in both directions.

### Removing or renaming a proxy

If you remove (or rename) a proxy that is still referenced by monitors, **those monitors are kept**, and their proxy isn't cleared either, since that would silently switch them to a direct connection. Instead:

1. their uptime checks **fail** with a _"The proxy "..." is not configured"_ error, so the monitors go **DOWN**, and you're notified as usual,
2. the SSL checks of the affected HTTP monitors are **skipped**, since the uptime check already reports the problem, and an SSL event would only blame the certificate,
3. a `WARN` entry is logged at startup, listing every monitor with an unknown proxy,
4. the Web UI marks them with a **warning badge** on the monitor list.

Existing monitors can still be updated without touching their proxy, so you can point them to another one (or to a direct connection) whenever you like, or you can add the proxy back to your configuration. A **clone** of such a monitor, on the other hand, has to pick a configured proxy, or none.

## Viewing the configured proxies

The configured proxies are listed in the **Proxies** block of the **Settings** page of the Web UI, together with their **type** (HTTP or SOCKS5), their **address**, and whether _Kuvasz_ **authenticates** to them.

The same is available through the API, at `GET /api/v2/proxies`, and through the `list-proxies` tool of the [**MCP server**](../features/mcp-server.md#docker-hosts-and-proxies). None of them exposes the credentials.

## How the checks go through a proxy

- **The names of the targets are resolved by the proxy**, never by _Kuvasz_, regardless of the type of the proxy. So a target that only resolves **behind the proxy** (e.g. an internal hostname of a private network) can be monitored, even though _Kuvasz_ itself can't resolve it.
- **HTTP proxies are always asked to open a tunnel** with `CONNECT host:port`, for plain `http://` targets as well, not only for `https://` ones (see the [**limitations**](#limitations) about what this means for some proxies).
- For **HTTPS targets**, TLS is established **inside the tunnel, end to end** between _Kuvasz_ and the target, so the certificate of the target is validated exactly the same way as over a direct connection, and the proxy can't see (or alter) the traffic. **HTTP/2** works through the tunnel too.
- The **latency of a TCP monitor** covers reaching the proxy and the **establishment of the tunnel**, since the time the proxy takes to connect to the target can't be told apart from the rest. Keep it in mind when you set a [latency threshold](tcp-monitors.md#latency-threshold).
- The errors of a proxied check tell which proxy was involved, e.g. _"The check through the proxy "corporate-egress" failed: The proxy refused to connect to example.com:8080: HTTP/1.1 403 Forbidden"_, or _"... The SOCKS5 proxy could not connect to db.office.local:5432: connection refused (0x05)"_.
- Every proxy has its **own connection pool**, so a connection opened through one proxy is **never reused** by a monitor that is checked through another one (or directly). If you point a monitor to another proxy, its very next check takes the new route.

## Limitations

- **Plain `http://` targets are tunneled too.** Since every connection is opened with `CONNECT`, a proxy that only allows `CONNECT` to the usual TLS ports refuses the checks of plain `http://` targets on other ports (and of TCP monitors on such ports) with a `403`. That's the default of **Squid**, for example, which only allows port 443 (its `SSL_ports` ACL). You can either allow the ports you need in your proxy's configuration, e.g. for Squid:

    ```squid title="squid.conf"
    acl SSL_ports port 443 8080 5432
    ```

    or remove the `http_access deny CONNECT !SSL_ports` rule entirely, if your proxy is only reachable from trusted clients anyway.

- **The connection to the proxy itself is not encrypted.** _Kuvasz_ talks to the proxy in plaintext, so the address of the target and the **credentials of the proxy** cross the network between _Kuvasz_ and the proxy **unencrypted**. The traffic of **HTTPS targets stays encrypted** (it's TLS inside the tunnel), but the traffic of plain HTTP targets doesn't. If the proxy is reachable over TLS, a [**stunnel sidecar**](#reaching-a-proxy-over-tls-with-a-stunnel-sidecar) can fix it.
- **Only HTTP and TCP monitors can be checked through a proxy.** DNS monitors aren't supported (their queries go over UDP by default), and Docker hosts have their own [connection settings](docker-hosts.md).
- **The notifications don't go through these proxies.** If your integrations can only reach the internet through a proxy too, see [**Sending the notifications through a proxy**](#sending-the-notifications-through-a-proxy).

## Proxies and the connectivity check

If the [**connectivity check**](../features/connectivity-check.md) is enabled, it's worth knowing that its probe is **always a direct TCP connection** to the configured [targets](../setup/configuration.md#connectivity-check-targets) (`1.1.1.1:53` and `8.8.8.8:53` by default), and it **never goes through a proxy**. The checks of the proxied monitors are suspended just like the others while the probe fails, which can backfire in both directions:

- On an **egress-filtered network**, where the internet is only reachable through a proxy, the default targets can **never be reached**. _Kuvasz_ then considers itself permanently disconnected, and **silently suspends every monitor**, including the proxied ones, even though their proxy works perfectly fine.
- The other way around, the host of _Kuvasz_ can have direct internet access while the **proxy is down**, so the proxied monitors are not suspended, and they go **DOWN**. That's the correct result for them, but you might have expected the connectivity check to cover it.

Therefore, if you use both, either of the following is recommended:

1. **Point the connectivity check at your proxies**, so its question becomes _"can Kuvasz reach its egress?"_. This fits setups where (almost) every monitor goes through the proxies. Keep in mind that a **single reachable target is enough** to consider the connectivity healthy, so on a mixed setup (with direct and proxied monitors), listing both kinds of targets doesn't tell you anything about either of the routes on its own.

    ```yaml title="kuvasz.yml"
    app-config:
      connectivity-check:
        enabled: true
        targets:
          - "10.0.0.10:3128" # the address of the "corporate-egress" proxy
          - "10.0.0.11:3128" # a second proxy, if there is one
    ```

2. **Opt the proxied monitors out** of the connectivity check with [**`ignore-connectivity-check`**](http-monitors.md#ignore-connectivity-check) (`ignoreConnectivityCheck` on the API and the MCP server). This is the **better fit for a mixed setup**: the direct monitors keep being protected by the connectivity check, while the proxied ones are always checked, and a broken proxy shows up as their failure.

    ```yaml title="kuvasz.yml"
    http-monitors:
      - name: "Intranet"
        url: "https://intranet.corp.local"
        uptime-check-interval: 60
        proxy: "corporate-egress"
        ignore-connectivity-check: true
    ```

## Sending the notifications through a proxy

The [**integrations**](integrations.md) (except for e-mail, which uses SMTP) send their notifications through the shared HTTP client of _Kuvasz_, which is **not affected** by the proxies above. If they can only reach the internet through a proxy too, it can be configured **globally** for them, with the standard settings of _Micronaut_:

=== "YAML"

    ```yaml
    micronaut:
      http:
        client:
          proxy-type: http
          proxy-address: "10.0.0.10:3128"
          # Only if the proxy requires authentication
          proxy-username: "kuvasz"
          proxy-password: "${EGRESS_PROXY_PASSWORD}"
    ```

=== "ENV"

    ```bash
    MICRONAUT_HTTP_CLIENT_PROXY_TYPE=http
    MICRONAUT_HTTP_CLIENT_PROXY_ADDRESS=10.0.0.10:3128
    # Only if the proxy requires authentication
    MICRONAUT_HTTP_CLIENT_PROXY_USERNAME=kuvasz
    MICRONAUT_HTTP_CLIENT_PROXY_PASSWORD=...
    ```

- It applies to **every integration** (and to the update check of _Kuvasz_ too), there is no way to exclude some of them.
- It **doesn't affect the monitors** at all: their checks only go through the proxy they refer to, if any.

## Reaching a proxy over TLS with a stunnel sidecar

_Kuvasz_ can't talk TLS to a proxy (i.e. `https://` proxy URLs are [not supported](#url)), but if your proxy is only reachable over TLS, or you'd like to keep the credentials of the proxy off the network, you can put a tiny [**stunnel**](https://www.stunnel.org/) sidecar next to _Kuvasz_:

```mermaid
flowchart LR
    kuvasz["Kuvasz"] -- "plaintext, local only" --> stunnel["stunnel sidecar"]
    stunnel -- "TLS" --> proxy["Your proxy"]
    proxy -- "TLS end to end for HTTPS targets" --> target["Monitored target"]
```

- _Kuvasz_ talks plaintext to the sidecar, so the address of the target and the credentials of the proxy only cross that **local hop** unencrypted, e.g. the internal network of a _Docker Compose_ project.
- The sidecar wraps everything into **TLS towards the proxy**, and **verifies its certificate** (both the chain and the hostname).
- The traffic of **HTTPS targets is still end-to-end encrypted** inside the tunnel, i.e. it's TLS inside TLS on the way to the proxy.

The setup below uses the [**dockurr/stunnel**](https://hub.docker.com/r/dockurr/stunnel) image, and it's covered by the test suite of _Kuvasz_. The image can be configured with environment variables too, but that configuration **doesn't verify the certificate of the proxy**, so mount a configuration file of your own at `/stunnel.conf`, which replaces the built-in one:

```ini title="stunnel.conf"
foreground = yes
pid =

[tls-proxy]
client = yes
accept = 0.0.0.0:3128
connect = proxy.example.com:3129
# Verify the certificate of the proxy, both its chain and its hostname
verifyChain = yes
CAfile = /etc/ssl/certs/ca-certificates.crt
checkHost = proxy.example.com
sni = proxy.example.com
```

```yaml title="docker-compose.yml"
services:
  kuvasz:
    image: kuvaszmonitoring/kuvasz:latest
    volumes:
      - ./kuvasz.yml:/config/kuvasz.yml
  stunnel:
    image: dockurr/stunnel:5.82
    environment:
      # Only used by the healthcheck of the image, so it has to match the "accept" port of stunnel.conf
      LISTEN_PORT: "3128"
    volumes:
      - ./stunnel.conf:/stunnel.conf:ro
    # No published ports: only Kuvasz should be able to reach it
```

```yaml title="kuvasz.yml"
proxies:
  - name: "corporate-egress"
    url: "http://stunnel:3128" # the sidecar, NOT the proxy itself
    # The credentials of the proxy itself, if it requires any
    username: "kuvasz"
    password: "${EGRESS_PROXY_PASSWORD}"
```

!!! warning "Never expose the listener of the sidecar"

    The sidecar forwards anything to your proxy, so its plaintext listener must **only be reachable by _Kuvasz_**. Don't publish its port, and if your proxy doesn't require authentication, keep in mind that anybody who can reach the sidecar can use your proxy.

!!! tip "Good to know"

    - If the certificate of your proxy is issued by a **private CA**, mount that CA into the sidecar, and point `CAfile` at it.
    - The very same setup works for a **SOCKS5 proxy behind TLS**: only the URL of the proxy entry changes to `socks5://stunnel:3128`.
    - On **Kubernetes**, the [official Helm chart](../setup/helm-deployment.md) can't add a container to the pod of _Kuvasz_, so run the sidecar as a separate _Deployment_ behind a _ClusterIP_ service, and allow only _Kuvasz_ to reach it with a _NetworkPolicy_. Keep in mind that the plaintext hop crosses the network of your cluster then.
