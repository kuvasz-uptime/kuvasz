_Kuvasz_ provides a fully-fledged REST API to **manage your monitors, check their status, and more**. The API is designed to be easy to use and flexible, allowing you to integrate it into your existing systems or build your own UI on top of it.

## Authentication

Unless you [completely disabled authentication](../setup/configuration.md#authentication), you need to authenticate every API call (except the `GET /api/v1/health` health check endpoint), using your pre-configured API key.

!!! info "API key usage"

    The API key should be passed in the `X-API-KEY` header of every request.

## API documentation

There is an OpenAPI-compliant [**API documentation**](../api-doc.md) which you can use to explore the available endpoints, their parameters, and responses.

## Example request

```bash title="cURL"
curl -X GET "http://your.kuvasz.host/api/v1/monitors/107" \
  -H "X-API-KEY: ThisShouldBeVeryVerySecureToo"
```

```json title="Response"
{
  "id": 107,
  "name": "kuvasz docs",
  "url": "https://kuvasz-uptime.dev",
  "uptimeCheckInterval": 300,
  "enabled": true,
  "sslCheckEnabled": true,
  "createdAt": "2025-06-29T10:01:25.416294+02:00",
  "updatedAt": "2025-06-29T10:57:56.470142+02:00",
  "uptimeStatus": "UP",
  "uptimeStatusStartedAt": "2025-06-29T10:01:29.618775+02:00",
  "lastUptimeCheck": "2025-06-29T11:25:58.880898+02:00",
  "nextUptimeCheck": "2025-06-29T11:30:58.884+02:00",
  "sslStatus": "VALID",
  "sslStatusStartedAt": "2025-06-29T10:02:30.473866+02:00",
  "lastSSLCheck": "2025-06-29T10:59:03.527202+02:00",
  "nextSSLCheck": "2025-06-30T10:59:03.532+02:00",
  "uptimeError": null,
  "sslError": null,
  "requestMethod": "GET",
  "latencyHistoryEnabled": true,
  "forceNoCache": true,
  "followRedirects": true,
  "sslExpiryThreshold": 7,
  "sslValidUntil": "2025-08-10T10:54:01+02:00",
  "integrations": [
    "telegram:telegram_test"
  ],
  "effectiveIntegrations": [
    {
      "id": "email:email_test",
      "type": "EMAIL",
      "name": "email_test",
      "enabled": true,
      "global": true
    },
    {
      "id": "slack:slack_global",
      "type": "SLACK",
      "name": "slack_global",
      "enabled": true,
      "global": true
    },
    {
      "id": "telegram:telegram_test",
      "type": "TELEGRAM",
      "name": "telegram_test",
      "enabled": true,
      "global": false
    },
    {
      "id": "pagerduty:pd-test",
      "type": "PAGERDUTY",
      "name": "pd-test",
      "enabled": true,
      "global": true
    }
  ]
}
```
