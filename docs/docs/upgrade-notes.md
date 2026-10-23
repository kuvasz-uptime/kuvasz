## Upgrade to v4.6.0

!!!tip

    You don't need to change your configuration to upgrade to version 4.6.0. The notes below describe what behaves differently, and what to keep an eye on during the upgrade. See the [**changelog**](changelog.md#4.6.0) for every change.

### The first start takes longer if you have a lot of data

The tables that keep the response times and the other metrics of the checks get new indexes, which are built when _Kuvasz_ starts for the first time after the upgrade. With a lot of history and monitors (roughly above a 1,000), this can take a while: about **30 seconds for 28 million rows** on a single CPU in our tests. _Kuvasz_ doesn't run any checks until it's done, so don't stop it in the meantime. If you use the [Helm chart](setup/helm-deployment.md) and keep a very long history, raise `livenessProbe.initialDelaySeconds` for the upgrade, as the liveness probe waits only about 3 minutes before it restarts _Kuvasz_.

### Status pages <!-- md:config management/status-pages.md -->

- **The default status page is limited to 100 monitors**: above 100 enabled monitors, the [default status page](management/status-pages.md#the-default-status-page) can't be opened anymore, whether it's public or not, and `GET /api/v2/status-pages/0/details` returns `404`. Use [custom status pages](management/status-pages.md#custom-status-pages) with selected monitors or categories instead.
- **Only the latest 20 incidents of each monitor are listed**: on the status pages, in the status page details of the REST API, and in the `get-status-page-details` tool of the [MCP server](features/mcp-server.md). The number of incidents and the downtime of each day still count every incident. No property was removed from the responses, there is a new one instead: `incidentCount`, the number of every incident of each monitor.

### API

The full lists of `/api/v2` (e.g. `GET /api/v2/http-monitors` or `GET /api/v2/incidents`) keep working as before, but they are **deprecated**. Please switch to the new `/api/v3` lists, which return the items page by page (the `size` parameter doesn't have an upper limit, so you can achieve v2's unbounded returns by providing a huge number there). The list tools of the MCP server return one page at a time already (25 items by default).

## Upgrade from v3.x.x to v4.x.x

!!!tip

    This is **not a breaking change** — your existing configuration keeps working as-is, you don't need to change anything to upgrade to version 4.x.x. The notes below describe a significant, but **backwards-compatible** relaxation of the authentication configuration.

### Authentication config <!-- md:config setup/configuration.md#authentication -->

Version 4.x.x introduces [**OIDC authentication**](setup/configuration.md#oidc-authentication-optional) and reworks how the auth-related config properties relate to each other. As a result, several previously mandatory properties are now **conditionally optional**:

- `ADMIN_USER` / `ADMIN_PASSWORD` are only required when authentication is enabled **and OIDC is _not_ configured**. When you enable OIDC, the built-in username/password login is disabled, so these are no longer needed (and are ignored if set).
- `ADMIN_API_KEY` is now **fully optional**. If you don't set it (or leave it blank), API key-based authentication is simply **disabled**, and the REST API can only be accessed through a web UI / OIDC session. Previously a missing API key was not handled this gracefully — now it's a first-class, supported setup.

None of this requires action on your side: if you keep your existing `ADMIN_USER`, `ADMIN_PASSWORD`, and `ADMIN_API_KEY` values, everything behaves exactly as before. Please refer to the [**Authentication**](setup/configuration.md#authentication) section for the full details.

## Upgrade from v2.x.x to v3.x.x

!!!tip

    When you're not using the API, or the Metrics exporters and you didn't configure any monitors via YAML, you don't need to do anything to upgrade to version 3.x.x!

### YAML monitor config <!-- md:config management/http-monitors.md -->

The `monitors` property to define your monitors in your YAML configuration file has been renamed to `http-monitors` to better reflect its purpose. To make your existing file work with version 3.x.x, it's enough to **just rename the property**, no other changes are needed.

```yaml hl_lines="2 10"
# v2.x.x
monitors:
- name: "My Monitor"
  url: "https://kuvasz-uptime.dev"
  uptime-check-interval: 60
  integrations:
    - "email:my-email-integration"
    - "slack:my-slack-integration"
# v3.x,x
http-monitors:
- name: "My Monitor"
# ...
```

### API

`/api/v1` endpoints will be supported for a limited time only, because **a new version of the API** has been introduced under `/api/v2`. Endpoints not explicitly listed below are expected to remain the same besides the path change, which means, that you can just replace `/api/v1` with `/api/v2` in your requests.

!!! warning "End of life of v1 endpoints"

    The `v1` endpoints will be supported until 2025-12-31. After this date, they will be removed in the next feature release. Please make sure to **migrate to the `v2` endpoints** before then. Please refer to the [**API documentation**](https://api-docs.kuvasz-uptime.dev) for more details.

#### Changed endpoints

- `GET /api/v2/settings`: The response schema has changed compared to v1:
  
    - it doesn't return the `integrations` anymore (they're available under a separate endpoint now)
    - the `MeterSettingsDto` schema uses new property names for clarity: `latestLatency` -> `httpLatestLatency`, `uptimeStatus` -> `httpUptimeStatus`.
    - the editability of the monitors via the UI & the API is now reflected under `editabilityState` (`readOnlyMode` is gone)

#### Removed endpoints

The following endpoints have been removed/renamed in version 3.0.0:

- `GET /api/v1/health` -> `GET /api/v2/health`: Everything remains the same, just the path has changed.

- `GET /api/v1/prometheus` -> `GET /api/v2/prometheus`: Not just the path has changed, but also the gauge names are more scoped now. Please refer to the [Metrics export](#metrics-export) section below for more details.

### Metrics export <!-- md:config management/metrics-exporters.md -->

The provided gauges' names have changed to be more scoped. Here is a mapping of the old and new names (you need to update your Prometheus/OTLP configuration accordingly):

=== "OTLP"

    | Old gauge name                             | New gauge name                          |
    |--------------------------------------------|-----------------------------------------|
    | kuvasz.monitor.uptime.status               | kuvasz.http.uptime.status               |
    | kuvasz.monitor.latency.latest.milliseconds | kuvasz.http.latency.latest.milliseconds |
    | kuvasz.monitor.ssl.status                  | kuvasz.http.ssl.status                  |
    | kuvasz.monitor.ssl.expiry.seconds          | kuvasz.http.ssl.expiry.seconds          |


=== "Prometheus"

    | Old gauge name                             | New gauge name                          |
    |--------------------------------------------|-----------------------------------------|
    | kuvasz_monitor_uptime_status               | kuvasz_http_uptime_status               |
    | kuvasz_monitor_latency_latest_milliseconds | kuvasz_http_latency_latest_milliseconds |
    | kuvasz_monitor_ssl_status                  | kuvasz_http_ssl_status                  |
    | kuvasz_monitor_ssl_expiry_seconds          | kuvasz_http_ssl_expiry_seconds          |

#### Configuration

Also, the following configuration properties changed as well, make sure to update your configuration accordingly:

=== "YAML"
    
    | Old property                     | New property                         |
    |----------------------------------|--------------------------------------|
    | metrics-exports.uptime-status  | metrics-export.http-uptime-status  |
    | metrics-exports.latest-latency | metrics-export.http-latest-latency |

=== "ENV"
    
    | Old variable                   | New variable                        |
    |--------------------------------|-------------------------------------|
    | ENABLE_UPTIME_STATUS_EXPORT  | ENABLE_HTTP_UPTIME_STATUS_EXPORT  |
    | ENABLE_LATEST_LATENCY_EXPORT | ENABLE_HTTP_LATEST_LATENCY_EXPORT |

#### Labels

The `url` label has been renamed to `target` on all gauges, to better reflect its purpose in a generic way.

## Upgrading from v1.x.x to v2.x.x

If you're upgrading from _Kuvasz v1_ to _Kuvasz v2_, it's better if you just **start with a fresh setup**, except for the database (make sure that you do a backup of it), which should be backward compatible.
Even if it's not a complete rewrite, a lot of things have changed under the hood, and the new version is not fully compatible with the old one.

All in all, you can use your old database, **your data will be migrated automatically**, but **there are a few notable breaking changes** you should be aware of:

- _Kuvasz_ is not distributed as a **native** (GraalVM based) Docker image anymore
- The minimum required _PostgreSQL_ version is now **12**
- The [**REST API**](features/api.md) is versioned now, and a few **endpoints have been changed or removed**. You can find the new API documentation [here](https://api-docs.kuvasz-uptime.dev)
- [**Integrations**](management/integrations.md) are now configured via the _YAML_ file, and the old, environment-variable-based configuration is no longer supported
- The **authentication** and its configuration **has been simplified**, read the [**Authentication**](setup/configuration.md#authentication) section carefully!

!!! warning

    This list **is not exhaustive**, there might be other - minor - breaking changes that are not listed here.

    Detailed upgrade notes (if necessary) for the future releases will be available in a dedicated section of the documentation.  
