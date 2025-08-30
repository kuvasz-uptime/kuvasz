## Upgrade from v2.x.x to v3.0.0

!!!tip

    When you're not using the API, or the Metrics exporters and you didn't configure any monitors via YAML, you don't need to do anything to upgrade to version 3.0.0!

### YAML monitor config <!-- md:config management/http-monitors.md -->

The `monitors` property to define your monitors in your YAML configuration file has been renamed to `http-monitors` to better reflect its purpose. To make your existing file work with version 3.0.0, it's enough to **just rename the property**, no other changes are needed.

```yaml hl_lines="2 10"
# v2.x.x
monitors:
- name: "My Monitor"
  url: "https://kuvasz-uptime.dev"
  uptime-check-interval: 60
  integrations:
    - "email:my-email-integration"
    - "slack:my-slack-integration"
# v3.0.0
http-monitors:
- name: "My Monitor"
# ...
```

### API

`/api/v1` endpoints will be supported for a limited time only, because **a new version of the API** has been introduced under `/api/v2`. Endpoints not explicitly listed below are expected to remain the same besides the path change, which means, that you can just replace `/api/v1` with `/api/v2` in your requests.

!!! warning "End of life of v1 endpoints"

    The `v1` endpoints will be supported until 2025-12-31. After this date, they will be removed in the next feature release. Please make sure to **migrate to the `v2` endpoints** before then. Please refer to the [**API documentation**](api-docs.md) for more details.

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
