# Docker hosts

A **Docker host** is a Docker daemon that _Kuvasz_ talks to, in order to check the containers of your [**Docker monitors**](docker-monitors.md). It can be the **local daemon** through its unix socket, or a **remote one** over TCP, optionally secured with **TLS or mutual TLS**.

Docker hosts are **defined in your configuration file**, similarly to the [integrations](integrations.md), and every monitor refers to its host **by name**. They can't be created or modified on the UI or through the API, and that's on purpose: access to a Docker daemon is **equivalent to root access** on its host, so these credentials should stay in your configuration file (or in your environment variables, or in your _Kubernetes_ secrets) instead of the database.

!!! warning "Read this first"

    Before exposing a Docker daemon to _Kuvasz_, please read the [**security considerations**](#security-considerations) at the bottom of this page.

## Configuration

```yaml title="Docker host examples"
docker-hosts:
  # The local daemon, through its unix socket
  - name: "local"
    url: "unix:///var/run/docker.sock"
  # A socket proxy on the same Docker network (plaintext, only for trusted networks!)
  - name: "local-proxy"
    url: "tcp://docker-socket-proxy:2375"
  # A remote daemon, secured with mutual TLS
  - name: "homelab"
    url: "tcp://192.168.1.100:2376"
    tls:
      ca: "/certs/homelab/ca.pem"
      cert: "/certs/homelab/cert.pem"
      key: "/certs/homelab/key.pem"
```

!!! tip "Keeping the paths out of your configuration file"

    You can use `${VARIABLE_NAME}` placeholders in the YAML, as described in the [**Keeping secrets out of your configuration file**](examples.md#keeping-secrets-out-of-your-configuration-file) recipe.

Every Docker host is **validated at startup**, and _Kuvasz_ **refuses to start** with a clear error message if a host has a duplicate or an empty name, an invalid URL, or TLS files that don't exist, aren't readable, or don't hold a valid certificate or key. The configured hosts are logged at startup too.

### Name

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type `string` -->
<!-- md:yaml_prop `name` -->

The name of the Docker host, which **must be unique**. Monitors refer to their host by this name, so if you rename a host, it's the same as [removing it](#removing-or-renaming-a-host) and adding a new one.

### URL

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type `string` -->
<!-- md:yaml_prop `url` -->

The address of the Docker daemon. The following formats are supported:

| URL                           | Connection                                                                     | Default port |
|-------------------------------|--------------------------------------------------------------------------------|--------------|
| `unix:///var/run/docker.sock` | The local daemon through a unix socket. The path must be absolute.             |              |
| `tcp://host:2375`             | Plaintext TCP, when there is no [`tls`](#tls) block                            | `2375`       |
| `tcp://host:2376`             | TLS, when there is a [`tls`](#tls) block                                       | `2376`       |
| `http://host:2375`            | Always plaintext TCP (a `tls` block is rejected)                               | `2375`       |
| `https://host:2376`           | Always TLS, even without a `tls` block (the system's trust store is used then) | `2376`       |

The host can be a hostname (including _Compose_ service names with underscores), or an IP address. `ssh://` URLs are **not supported**.

### TLS

<!-- md:version 4.5.0 -->
<!-- md:default empty -->
<!-- md:type `object` -->
<!-- md:yaml_prop `tls` -->

The **paths of the TLS material** used to reach the daemon, as PEM files. The three of them are independent, following the same rules as Docker's own client:

| Configured            | Meaning                                                                                                           |
|-----------------------|-------------------------------------------------------------------------------------------------------------------|
| `ca`                  | The daemon's certificate is verified against this CA (e.g. a self-signed one), no client certificate is presented |
| `cert` + `key`        | A client certificate is presented to a daemon whose certificate is trusted by the system's trust store            |
| `ca` + `cert` + `key` | **Mutual TLS**, the usual setup for a remote daemon                                                               |

- `cert` and `key` can only be configured **together**.
- Private keys are accepted in the `PKCS#8` (`BEGIN PRIVATE KEY`), `PKCS#1` (`BEGIN RSA PRIVATE KEY`) and `SEC1` (`BEGIN EC PRIVATE KEY`) formats, so the output of `openssl genrsa` works as it is. Encrypted keys are not supported.
- The daemon's certificate has to be **valid for the host in the URL** (i.e. the host must be in the certificate's _Subject Alternative Names_), otherwise the handshake fails.
- A `tls` block can't be combined with a `unix://` or an `http://` URL.

The files have to be **readable by the user _Kuvasz_ runs as**, which is checked at startup.

## Referencing hosts from monitors

A monitor refers to its host by the host's [name](#name). The name of a host **has to be configured when a monitor is created** - through the UI, the API, the MCP server, or as a new monitor in your YAML file - otherwise the creation is rejected with a validation error (and a new YAML monitor with an unknown host makes the startup fail).

### Removing or renaming a host

If you remove (or rename) a host that still has monitors, **those monitors are kept**, since deleting or disabling them automatically would be even worse. Instead:

1. their checks **fail** with a _"The Docker host "..." is not configured"_ error, so the monitors go **DOWN**, and you're notified as usual,
2. a `WARN` entry is logged at startup, listing every monitor with an unknown host,
3. the Web UI marks them with a **warning badge** on the monitor list, and with a **"Host not configured"** badge on their details page.

Existing monitors can still be updated, so you can point them to another host, or you can add the host back to your configuration.

## Viewing the configured hosts

The configured hosts are listed on the **Settings** page of the Web UI, together with their **authentication method** and the **negotiated API version** (which is only known after _Kuvasz_ talked to the given host for the first time, until then it's shown as `API ?`).

The same is available through the API, at `GET /api/v2/docker-hosts`. It **never exposes** the paths or the contents of the TLS material.

## How the daemon is called

- Every call is **read-only**: _Kuvasz_ only inspects containers, samples their resource usage, and lists them for the container picker of the Web UI.
- The **API version is negotiated** per host through the `/_ping` endpoint. See the [**compatibility matrix**](../features/docker-monitoring.md#docker-engine-versions) for the supported Engine versions.
- A request that fails at the network level (a timeout, a refused connection, a failed TLS handshake), or is answered with a `5xx` status, is **retried twice** (after 0.5 and 1.5 seconds), the same way HTTP checks are. The monitor's [timeout](docker-monitors.md#timeout) applies to every attempt, so a daemon that never answers takes about `3 × timeout + 2 seconds` to be reported DOWN. The only exception is the resource sampling: it's best-effort, so it gets a **single attempt**, and a slow daemon doesn't hold back the check's result with retries.

## Recipes

### Local daemon with Docker Compose

Mount the socket of the daemon into the container of _Kuvasz_:

```yaml title="docker-compose.yml"
services:
  kuvasz:
    image: kuvaszmonitoring/kuvasz:latest
    volumes:
      - ./kuvasz.yml:/config/kuvasz.yml
      - /var/run/docker.sock:/var/run/docker.sock
    # Only if you run Kuvasz as a non-root user, e.g. with `user: 1000:1000`:
    # the socket belongs to the `docker` group of the host, so add its ID
    # (you can look it up with `getent group docker | cut -d: -f3`)
    # group_add:
    #   - "999"
```

```yaml title="kuvasz.yml"
docker-hosts:
  - name: "local"
    url: "unix:///var/run/docker.sock"
```

!!! info "Mounting the socket as read-only doesn't make it read-only"

    Adding `:ro` to the mount only prevents deleting or replacing the socket file itself, but everything can still be **written through it**. If you'd like to restrict what _Kuvasz_ can do with the daemon, use a [socket proxy](#behind-a-socket-proxy).

### Behind a socket proxy

A socket proxy, like [**tecnativa/docker-socket-proxy**](https://github.com/Tecnativa/docker-socket-proxy), sits between _Kuvasz_ and the socket, and only lets through the API calls you allow. _Kuvasz_ needs **read access to the containers** (`CONTAINERS=1`) and the **`/_ping` endpoint** (allowed by default), nothing else:

```yaml title="docker-compose.yml"
services:
  docker-socket-proxy:
    image: tecnativa/docker-socket-proxy:latest
    environment:
      CONTAINERS: 1
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
  kuvasz:
    image: kuvaszmonitoring/kuvasz:latest
    volumes:
      - ./kuvasz.yml:/config/kuvasz.yml
```

```yaml title="kuvasz.yml"
docker-hosts:
  - name: "local"
    url: "tcp://docker-socket-proxy:2375"
```

!!! warning "Good to know"

    - If the proxy denies `/_ping`, _Kuvasz_ can't negotiate the API version, and **falls back to guessing** it (1.40 first, then 1.44). It works, but it's better to allow it.
    - `CONTAINERS=1` allows every read-only endpoint under `/containers`, which includes **reading the logs and downloading files out of any container**. The proxy prevents changing anything, but it doesn't make the access harmless.
    - Don't publish the port of the proxy, keep it on an internal network that only _Kuvasz_ can reach.

### Local daemon on Kubernetes (Helm)

With the [**official Helm chart**](../setup/helm-deployment.md), the socket of the node can be mounted with `extraVolumes` and `extraVolumeMounts`. The chart runs _Kuvasz_ as a **non-root user** (`1000`), so the group owning the socket on the node (usually `docker`) has to be added too:

```yaml title="values.yaml"
podSecurityContext:
  fsGroup: 1000
  supplementalGroups:
    - 999 # the ID of the group owning the socket on the node

extraVolumes:
  - name: docker-socket
    hostPath:
      path: /var/run/docker.sock
      type: Socket

extraVolumeMounts:
  - name: docker-socket
    mountPath: /var/run/docker.sock

config:
  raw: |
    docker-hosts:
      - name: "node"
        url: "unix:///var/run/docker.sock"
```

!!! info

    This only makes sense on nodes that actually run a Docker daemon. Most _Kubernetes_ distributions run their containers with `containerd` or `CRI-O` directly, which don't provide the Docker Engine API.

### Remote daemon with mutual TLS

A remote daemon should **never be exposed without TLS**, and since anybody who can reach a plaintext daemon can take over its host, **mutual TLS** is strongly recommended: that way the daemon only accepts clients with a certificate signed by your own CA.

The steps are described in detail in Docker's official [**Protect the Docker daemon socket**](https://docs.docker.com/engine/security/protect-access/#use-tls-https-to-protect-the-docker-daemon-socket) guide. In short:

1. Create your own CA, then a **server certificate** for the daemon (its _Subject Alternative Names_ must contain the hostname or the IP address you'll use in the [URL](#url)), and a **client certificate** for _Kuvasz_ (with the `clientAuth` extended key usage).
2. Configure the daemon to listen on TCP with `tlsverify` enabled, and the CA, the server certificate and its key.
3. Mount the CA, the client certificate and its key into the container of _Kuvasz_, and configure them in the [`tls`](#tls) block:

```yaml title="kuvasz.yml"
docker-hosts:
  - name: "homelab"
    url: "tcp://192.168.1.108:2376"
    tls:
      ca: "/certs/homelab/ca.pem"
      cert: "/certs/homelab/cert.pem"
      key: "/certs/homelab/key.pem"
```

!!! tip "Adding a TCP listener to a daemon started by systemd"

    The `docker.service` unit of most distributions already passes `-H fd://` to the daemon, and the daemon refuses to start if there is a `hosts` entry in `daemon.json` too. Keep the TLS options in `daemon.json`, and add the TCP listener with a drop-in (`sudo systemctl edit docker`) instead:

    ```ini
    [Service]
    ExecStart=
    ExecStart=/usr/bin/dockerd -H fd:// -H tcp://0.0.0.0:2376 --containerd=/run/containerd/containerd.sock
    ```

    The empty `ExecStart=` line is required: it clears the command of the original unit, otherwise systemd would reject the unit for having two of them. The unix socket keeps working as before, without a certificate.

## Security considerations

- **Access to a Docker daemon is equivalent to root access** on its host: whoever can talk to it can start a privileged container that mounts the host's filesystem. This applies to _Kuvasz_ too, even though it only ever reads.
- **Mounting the socket as `:ro` doesn't help**, it only protects the socket file, not the API behind it.
- **Mutual TLS authenticates, but it doesn't authorize.** A valid client certificate gives **full access** to the daemon, so treat the client key as a root credential, and don't reuse it for anything else.
- **A socket proxy reduces what can be done**, but `CONTAINERS=1` still allows reading the logs and the files of any container, see [above](#behind-a-socket-proxy).
- **Never expose a plaintext daemon** (`tcp://` without TLS) beyond a network you fully trust, like an internal _Docker_ network.
