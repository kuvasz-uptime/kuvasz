_Kuvasz_ can be configured in two ways: via a **_YAML_ configuration file, or via environment variables**. Most of the configuration options are available in both formats, but some are only available in one of them.

!!! info

    The _YAML_ configuration file **should be mounted as a volume** in the _Docker_ container under `/config/kuvasz.yml`.

    === "Docker Compose"

        ```yaml
        services:
          kuvasz:
            # ...
            volumes:
              - /path/to/your/kuvasz.yml:/config/kuvasz.yml
        ```

    If you modify your configuration (via _YAML_ or _ENV_, it doesn't matter), you need to restart the _Kuvasz_ container for the changes to take effect. In certain cases if you changed an environment variable, **you might need to rebuild the container** as well.

## Authentication

### Toggling authentication

<!-- md:version 2.0.0 -->
<!-- md:default `true` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    micronaut.security.enabled: true
    ```

=== "ENV"

    ```bash
    ENABLE_AUTH=true
    ```

If it's set to `false`, the authentication will be completely disabled, and you can access the web UI or the API without any credentials.

!!! warning "Disabling authentication"
    If you disable authentication, **anyone can access your Kuvasz instance** (assuming that it's exposed), so make sure to secure it by other means, like a firewall or a reverse proxy with authentication.

    If you're running it locally, or on your private network, it might be fine, but be aware of the risks.

### Authentication max age

<!-- md:version 2.0.0 -->
<!-- md:default `86400` -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    micronaut.security.token.generator.access-token.expiration: 86400
    ```

=== "ENV"

    ```bash
    AUTH_MAX_AGE=86400
    ```

The **maximum age of the authentication token** in seconds, default is 24 hours (86400 seconds). After this time, the admin user will need to log in again on the web UI.

### JWT signing secret

<!-- md:default _a random value generated on each startup_ -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    micronaut.security.token.jwt.signatures.secret.generator.secret: your-very-long-random-secret
    ```

The secret used to **sign the JWT** that backs the web UI session. By default _Kuvasz_ generates a **new random secret on every startup**, which makes the **sessions don't survive a restart** - every logged in user has to log in again after _Kuvasz_ is restarted.

If you want sessions to survive restarts, you can set a fixed one, but only via YAML.

!!! warning "Keep it secret, keep it strong"
    Anyone who knows this secret can forge valid sessions. Use a **long, random** value (at least 32 characters / 256 bits is required), keep it out of version control, and rotate it if you suspect it leaked (rotating invalidates all existing sessions, forcing a re-login).

### Credentials

!!! note "When are the admin credentials required?"
    The admin username and password are only used by the built-in username/password login form. They are **required when authentication is enabled and [OIDC](#oidc-authentication-optional) is _not_ configured**. When OIDC is enabled, the form-based login is disabled, so you don't need to set `ADMIN_USER` / `ADMIN_PASSWORD` at all.

#### Admin username

<!-- md:required_if auth enabled & OIDC not configured -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    admin-auth.username: YourSuperSecretUsername
    ```

=== "ENV"

    ```bash
    ADMIN_USER=YourSuperSecretUsername
    ```

The username for the admin user.

#### Admin password

<!-- md:required_if auth enabled & OIDC not configured -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    admin-auth.password: YourSuperSecretPassword 
    ```

=== "ENV"

    ```bash
    ADMIN_PASSWORD=YourSuperSecretPassword
    ```

The password for the admin user, **minimum 12 characters long, can't be the same as the username**.


#### API key

<!-- md:version 2.0.0 -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    admin-auth.api-key: ThisShouldBeVeryVerySecureToo
    ```

=== "ENV"

    ```bash
    ADMIN_API_KEY=ThisShouldBeVeryVerySecureToo
    ```

The API key for the [REST API](../features/api.md). It's **optional**: if you don't set it (or leave it blank), API key-based authentication is simply **disabled**, and the REST API can only be accessed when authenticated through the web UI / OIDC login. When it's set, it must be **at least 16 characters long**, otherwise the application won't start.

!!! note
    This key grants access to the REST API only. The [MCP server](../features/mcp-server.md) is protected by a separate [MCP API key](#mcp-api-key).

#### MCP API key

<!-- md:version 4.0.0 -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    admin-auth.mcp-api-key: ThisShouldBeVeryVerySecureToo
    ```

=== "ENV"

    ```bash
    ADMIN_MCP_API_KEY=ThisShouldBeVeryVerySecureToo
    ```

The API key dedicated to the [MCP server](../features/mcp-server.md) (the `/mcp` endpoint). It's **optional**: if you don't set it (or leave it blank), the MCP endpoint cannot be accessed via API key authentication at all. When it's set, it must be **at least 16 characters long**, otherwise the application won't start.

This key is **separate** from the [REST API key](#api-key) on purpose: the `/mcp` endpoint can **only** be accessed with this key, and the REST API key (or a web UI / OIDC session) does **not** grant access to it. This lets you hand out MCP access independently from REST API access, and revoke either one without affecting the other.

### OIDC authentication (optional)

As an alternative to the built-in username/password form, you can configure an OpenID Connect (OIDC) provider. When OIDC is enabled, the login page will display a **"Login (OIDC)"** button instead of the username/password form, and the username/password form-based login will be disabled. The API key authentication continues to work regardless.

!!! tip
    Once OIDC is enabled, the **Settings** page (and the `/settings` endpoint of the [REST API](../features/api.md)) shows the effective OIDC configuration — the issuer, client ID, the exact redirect (callback) URL, the valid post logout redirect URI, and the web origin to register with your provider. The client secret is never exposed.

    When OIDC is enabled, the `ADMIN_USER` and `ADMIN_PASSWORD` properties are **no longer needed** (and are ignored), since the built-in username/password login is disabled. If you still want API key-based programmatic access to the REST API, configure `ADMIN_API_KEY` — it works independently of the chosen interactive login method.

#### Enable OIDC

<!-- md:version 4.0.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "ENV"

    ```bash
    ENABLE_OIDC=true
    ```

When OIDC is enabled, you **must also provide** the issuer, client ID, and client secret below, otherwise the application won't start.

#### OIDC issuer

<!-- md:version 4.0.0 -->
<!-- md:required_if OIDC enabled -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    micronaut.security.oauth2.clients.oidc.openid.issuer: https://your-oidc-provider/
    ```

=== "ENV"

    ```bash
    OIDC_ISSUER=https://your-oidc-provider/
    ```

The issuer URL of your OIDC provider. _Kuvasz_ uses it to discover the provider's endpoints via its `/.well-known/openid-configuration` document.

#### OIDC client ID

<!-- md:version 4.0.0 -->
<!-- md:required_if OIDC enabled -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    micronaut.security.oauth2.clients.oidc.client-id: your-client-id
    ```

=== "ENV"

    ```bash
    OIDC_CLIENT_ID=your-client-id
    ```

#### OIDC client secret

<!-- md:version 4.0.0 -->
<!-- md:required_if OIDC enabled -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    micronaut.security.oauth2.clients.oidc.client-secret: your-client-secret
    ```

=== "ENV"

    ```bash
    OIDC_CLIENT_SECRET=your-client-secret
    ```

#### OIDC authorization server

<!-- md:version 4.0.0 -->
<!-- md:default _auto-detected from the issuer URL_ -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    micronaut.security.oauth2.clients.oidc.authorization-server: keycloak
    ```

=== "ENV"

    ```bash
    OIDC_AUTH_SERVER=keycloak
    ```

Identifies the type of your OIDC provider. For most cloud providers _Kuvasz_ infers this automatically from the issuer URL, but in general **it's recommended to explicitly provide it**. 

The value is **case-insensitive**, and the supported providers are:

| Value          | Provider             |
|----------------|----------------------|
| `AUTH0`        | Auth0                |
| `COGNITO`      | AWS Cognito          |
| `KEYCLOAK`     | Keycloak             |
| `MICROSOFT`    | Microsoft (Entra ID) |
| `OKTA`         | Okta                 |
| `ORACLE_CLOUD` | Oracle Cloud (IDCS)  |

#### OIDC end-session

<!-- md:version 4.0.0 -->
<!-- md:default `true` -->
<!-- md:type `boolean` -->

When OIDC is enabled, signing out from the web UI also logs you out at the OIDC provider. This behavior is **enabled by default** whenever OIDC is on. If you want to opt out and only clear the local _Kuvasz_ session on logout, **disable the end-session support:**

=== "YAML"

    ```yaml
    micronaut.security.oauth2.clients.oidc.openid.end-session.enabled: false
    ```

=== "ENV"

    ```bash
    ENABLE_OIDC_END_SESSION=false
    ```

!!! warning "Register the post-logout redirect URI"
    Most providers require the post-logout redirect URI to be explicitly registered on the client. For example, in _Keycloak_ add `https://<your-kuvasz-host>/*` (or the exact `https://<your-kuvasz-host>/auth/logout` URL) to the client's **Valid post logout redirect URIs**. Otherwise the provider will reject the redirect back to _Kuvasz_ after logout.

#### OIDC email allowlist

<!-- md:version 4.0.0 -->
<!-- md:default _empty (every authenticated user is allowed)_ -->
<!-- md:type `string` -->

By default **any user your OIDC provider successfully authenticates is allowed to sign in** and is granted full access. If your provider serves a broader audience than the people who should access _Kuvasz_ (e.g. a shared corporate IdP or a public provider), restrict access with an **allowlist of email addresses**:

!!! warning "Restrict access when no allowlist is configured"
    Without an email allowlist, **every user the OIDC provider can authenticate gets full access to _Kuvasz_** — including the REST API. If your provider is shared with other applications, backed by a public sign-up, or otherwise authenticates more people than should be able to administer _Kuvasz_, this effectively hands them admin rights.

    If you choose not to configure an allowlist, it's **strongly recommended to use a dedicated, restricted OAuth client** instead — one that only the intended users can obtain tokens for (for example, by limiting the client to a specific group/role assignment in your provider). Otherwise, configure the allowlist below.

=== "YAML"

    ```yaml
    admin-auth.oidc.allowed-emails:
      - alice@example.com
      - bob@example.com
    ```

=== "ENV"

    ```bash
    OIDC_ALLOWED_EMAILS=alice@example.com,bob@example.com
    ```

When set, only users whose email matches one of the listed addresses are allowed in; everyone else is rejected at login. Matching is **case-insensitive** and surrounding whitespace is ignored. Provide the list as a **comma-separated** value when using the environment variable.

!!! note
    The allowlist relies on the `email` claim, so make sure your provider returns it (it's part of the standard `email` scope, which _Kuvasz_ requests by default).

##### Require verified email

<!-- md:version 4.0.0 -->
<!-- md:default `true` -->
<!-- md:type `boolean` -->

When the allowlist is in use, an email is only accepted if the provider also reports it as **verified** (the `email_verified` claim). This prevents a user with an unverified — and potentially spoofed — email from satisfying the allowlist. If your provider doesn't supply `email_verified` (or you intentionally want to skip the check), disable it:

=== "YAML"

    ```yaml
    admin-auth.oidc.require-verified-email: false
    ```

=== "ENV"

    ```bash
    OIDC_REQUIRE_VERIFIED_EMAIL=false
    ```

This setting only has an effect when the [email allowlist](#oidc-email-allowlist) is configured.

## Database

_Kuvasz_ uses a _PostgreSQL_ database to store its data. You can use an existing _PostgreSQL_ instance, and point _Kuvasz_ to a separate database in it, the only thing you should watch out for is that **the database user has the necessary permissions** to create schemas and tables.

The database connection can be configured **only via environment variables**.

!!! info

    The **minimum, tested version of _PostgreSQL_ is 14**, `alpine` distributions are supported.

### Host

<!-- md:default localhost -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_HOST=localhost
    ```

A hostname or IP address of the database server.

### Port

<!-- md:default 5432 -->
<!-- md:type `number` -->

=== "ENV"

    ```bash
    DATABASE_PORT=5432
    ```

### Database name

<!-- md:default postgres -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_NAME=postgres
    ```

The name of the database to connect to. _Kuvasz_ will create a schema called `kuvasz` in the database, and will use it to store all the data it needs.

### User

<!-- md:flag required -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_USER=change_me
    ```

The username to connect to the database. The user **must have the necessary permissions** to create schemas and tables.

### Password

<!-- md:flag required -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_PASSWORD=change_me
    ```

The password of the user above.

## SMTP

!!! tip

    Using an SMTP server is **optional**, it's only needed if you want to use the [**email integration**](../features/notifications.md#email) to send notifications about events.

    The SMTP configuration can be set **only via _YAML_**.

    Be aware that if you include `smtp-config` in your configuration, then you'll **need to provide all the required** properties of it, otherwise _Kuvasz_ won't start.

### Host

<!-- md:version 2.0.0 -->
<!-- md:required_if `smtp-config` is present -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    smtp-config.host: 'your.smtp.server'
    ```

The **hostname or IP address** of the SMTP server you want to use for sending emails.

### Port

<!-- md:version 2.0.0 -->
<!-- md:required_if `smtp-config` is present -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    smtp-config.port: 465
    ```

The **port** of the SMTP server you want to use for sending emails.

### Username

<!-- md:version 2.0.0 -->
<!-- md:default `null` -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    smtp-config.username: YourSMTPUsername
    ```

The **username** to authenticate with the SMTP server. If your SMTP server doesn't require authentication, you can omit specifying this property, or set it explicitly to `null`.

### Password

<!-- md:version 2.0.0 -->
<!-- md:default `null` -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    smtp-config.password: YourSMTPPassword
    ```

The **password** to authenticate with the SMTP server. If your SMTP server doesn't require authentication, you can omit specifying this property, or set it explicitly to `null`.

### Transport strategy

<!-- md:version 2.0.0 -->
<!-- md:default `SMTP_TLS` -->
<!-- md:type enum: `SMTP_TLS`, `SMTPS`, `SMTP` -->

=== "YAML"

    ```yaml
    smtp-config.transport-strategy: SMTP_TLS
    ```

The **transport strategy** to use for sending emails. The default is `SMTP_TLS`, which uses TLS encryption for the connection. You can also use `SMTPS` (implicit SSL) or `SMTP` (no encryption).

## Miscellaneous

### Timezone

<!-- md:default UTC -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    TZ=UTC
    ```

The timezone to use for the application. It's recommended to set it to your **local timezone**.

### Event data retention

<!-- md:version 2.0.0 -->
<!-- md:default 365 -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    app-config.event-data-retention-days: 365
    ```

=== "ENV"

    ```bash
    EVENT_DATA_RETENTION_DAYS=365
    ```

The number of days to keep the finished events in the database. After this time, finished uptime & SSL **events will be cleaned up** to spare space in the database. The **minimum is 1 day**.

### Latency data retention

<!-- md:version 2.0.0 -->
<!-- md:default 7 -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    app-config.latency-data-retention-days: 7
    ```

=== "ENV"

    ```bash
    LATENCY_DATA_RETENTION_DAYS=7
    ```

The number of days to keep the latency data in the database. After this time, **latency data will be cleaned up** to spare space in the database. The **minimum is 1 day**.

### Event logging

<!-- md:default true -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    app-config.log-event-handler: true
    ```

=== "ENV"

    ```bash
    ENABLE_LOG_EVENT_HANDLER=true
    ```

Toggles the log based event handler, which **writes the uptime & SSL events** coming from monitors (i.e. UP/DOWN/redirect/etc.) **to the STDOUT** of the container.

**Examples**:

- `✅ Your monitor "test_up" (https://test.com) is UP (200). Latency was: 1826ms.`
- `🚨 Your monitor "test_down" (https://test2.com) is DOWN. Reason: Connect Error: Connection refused: test2.com`
- `ℹ Request to "test_redirected" (https://redirected.com) has been redirected`
- `🔒️ Your site "test_good_ssl" (https://good-ssl.com) has a VALID certificate`
- `🚨 Your site "test_bad_ssl" (https://no-subject.badssl.com/) has an INVALID certificate. Reason: PKIX path validation failed`

### Language

<!-- md:version 2.0.0 -->
<!-- md:default en -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    app-config.language: en
    ```

=== "ENV"

    ```bash
    APP_LANGUAGE=en
    ```

The language to use. If you would like to translate _Kuvasz_ to your desired language, check out the [**Localization**](../localization.md) section for more details.

!!! info "Currently supported languages"

    - English (`en`)
    - French (`fr`)
    - Polish (`pl`)

### MCP server

<!-- md:version 4.0.0 -->
<!-- md:flag experimental -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    micronaut.mcp.server.enabled: true
    ```

=== "ENV"

    ```bash
    ENABLE_MCP_SERVER=true
    ```

Enables the built-in [**MCP server**](../features/mcp-server.md), which exposes your monitors, incidents, configuration, etc. as tools that AI assistants can call. The server is available at `/mcp` once enabled and is protected by a dedicated [MCP API key](#mcp-api-key), separate from the REST API key.

### Check for updates

<!-- md:version 3.0.1 -->
<!-- md:default true -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    app-config.check-updates: true
    ```

=== "ENV"

    ```bash
    ENABLE_CHECK_UPDATES=true
    ```

Toggles the automatic check for new versions of _Kuvasz_. If it's enabled, the application will **check for new releases in the background**, and if a new version is available, it will show a notification on the UI and populate the relevant information through the API too.

### HTTP check timeout

<!-- md:version 3.4.0 -->
<!-- md:default 30 -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    app-config.http-check-timeout-seconds: 30
    ```

=== "ENV"

    ```bash
    HTTP_CHECK_TIMEOUT_SECONDS=30
    ```

The **global read timeout limit** for HTTP monitors (the default and the maximum value are both 30 seconds). Useful if you would like to use a relatively low [response time threshold](../management/http-monitors.md#response-time-threshold) or [uptime check interval](../management/http-monitors.md#uptime-check-interval) on your monitors, and you want to **make sure that consecutive checks don't wait for slow responses**. For example, if you use a low uptime check interval on all of your monitors (like 10 seconds), then it makes sense to also set a global timeout lower than the default, otherwise the checks won't really happen every 10 seconds because the HTTP client will wait more than that until it eventually fails.

## Full configuration example

You can find the full configuration example below, which includes all the options currently available. You can use it as a starting point for your own configuration.

=== "YAML"

    ```yaml
    micronaut:
      security:
        enabled: true
        token:
          generator.access-token.expiration: 86400 # 24 hours
          jwt.signatures.secret.generator.secret: your-very-long-random-secret
        oauth2:
          clients:
            oidc:
              client-id: your-client-id
              client-secret: your-client-secret
              authorization-server: keycloak
              openid:
                issuer: https://your-oidc-provider/
                end-session.enabled: true
      mcp.server.enabled: false
      metrics:
        enabled: true
        export:
          otlp:
            enabled: true
            url: https://example.host:4318/v1/metrics
            headers: 'Authorization=Bearer Your-collectors-API-token,key2=value'
            step: PT1M
          prometheus:
            enabled: true
            descriptions: true
    ---
    metrics-exports:
      http-uptime-status: true
      http-latest-latency: true
      ssl-status: true
      ssl-expiry: true
      push-uptime-status: true
      icmp-uptime-status: true
      icmp-latest-latency: true
      icmp-latest-packet-loss: true
    ---
    admin-auth:
      username: YourSuperSecretUsername
      password: YourSuperSecretPassword
      api-key: ThisShouldBeVeryVerySecureToo
      mcp-api-key: ThisShouldBeVeryVerySecureToo
      oidc:
        allowed-emails:
          - alice@example.com
          - bob@example.com
        require-verified-email: true
    ---
    app-config:
      event-data-retention-days: 365
      latency-data-retention-days: 7
      log-event-handler: true
      language: en
      check-updates: true
      http-check-timeout-seconds: 30
    ---
    smtp-config:
      host: 'your.smtp.server'
      port: 465
      transport-strategy: SMTP_TLS
      username: YourSMTPUsername
      password: YourSMTPPassword
    ```

=== "ENV"

    ```bash
    ENABLE_AUTH=true
    AUTH_MAX_AGE=86400 # 24 hours
    ADMIN_USER=YourSuperSecretUsername
    ADMIN_PASSWORD=YourSuperSecretPassword
    ADMIN_API_KEY=ThisShouldBeVeryVerySecureToo
    ADMIN_MCP_API_KEY=ThisShouldBeVeryVerySecureToo
    ENABLE_OIDC=true
    OIDC_ISSUER=https://your-oidc-provider/
    OIDC_CLIENT_ID=your-client-id
    OIDC_CLIENT_SECRET=your-client-secret
    OIDC_AUTH_SERVER=keycloak
    ENABLE_OIDC_END_SESSION=true
    OIDC_ALLOWED_EMAILS=alice@example.com,bob@example.com
    OIDC_REQUIRE_VERIFIED_EMAIL=true
    DATABASE_HOST=localhost
    DATABASE_PORT=5432
    DATABASE_NAME=postgres
    DATABASE_USER=change_me
    DATABASE_PASSWORD=change_me
    EVENT_DATA_RETENTION_DAYS=365
    LATENCY_DATA_RETENTION_DAYS=7
    ENABLE_LOG_EVENT_HANDLER=true
    APP_LANGUAGE=en
    ENABLE_CHECK_UPDATES=true
    HTTP_CHECK_TIMEOUT_SECONDS=30
    ENABLE_MCP_SERVER=false
    TZ=UTC
    ENABLE_METRICS_EXPORT=true
    ENABLE_OTLP_EXPORT=true
    OTLP_EXPORT_URL=https://example.host:4318/v1/metrics
    OTLP_EXPORT_HEADERS='Authorization=Bearer Your-collectors-API-token,key2=value'
    OTLP_EXPORT_STEP=PT1M
    ENABLE_PROMETHEUS_EXPORT=true
    ENABLE_PROMETHEUS_DESCRIPTIONS=true
    # Enable the individual metrics
    ENABLE_HTTP_UPTIME_STATUS_EXPORT=true
    ENABLE_HTTP_LATEST_LATENCY_EXPORT=true
    ENABLE_SSL_STATUS_EXPORT=true
    ENABLE_SSL_EXPIRY_EXPORT=true
    ENABLE_PUSH_UPTIME_STATUS_EXPORT=true
    ENABLE_ICMP_UPTIME_STATUS_EXPORT=true
    ENABLE_ICMP_LATEST_LATENCY_EXPORT=true
    ENABLE_ICMP_LATEST_PACKET_LOSS_EXPORT=true
    ```
