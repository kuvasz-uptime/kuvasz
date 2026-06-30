# Kuvasz Helm Chart

This Helm chart deploys [Kuvasz](https://kuvasz-uptime.dev) - an open-source uptime monitoring solution on a Kubernetes cluster.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+
- PostgreSQL 14+ (can be deployed as part of this chart or use external)

## Installation

### Quick Start

```bash
# Install with default values
helm install my-kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz-uptime --version 3.3.0

# Or install with custom values
helm install my-kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz-uptime --version 3.3.0 -f my-values.yaml
```

### Using External Database

If you have an existing PostgreSQL database:

```yaml
postgresql:
  enabled: false

externalDatabase:
  host: "postgres.example.com"
  port: 5432
  database: "kuvasz"
  user: "kuvasz"
  password: "your-password"
  # Or use existing secret:
  # existingSecret: "my-postgres-secret"
  # existingSecretPasswordKey: "password"
```

### Using Internal Database

The chart can deploy PostgreSQL as a simple StatefulSet using the official PostgreSQL image:

```yaml
postgresql:
  enabled: true
  image:
    repository: pgautoupgrade/pgautoupgrade
    tag: "18-alpine"
  auth:
    username: kuvasz
    password: ""  # Will be auto-generated if empty
    database: kuvasz
  persistence:
    enabled: true
    size: 8Gi
    storageClass: ""  # Uses default storage class if empty
```

## Configuration

The following table lists the most important parameters and their default values:

| Parameter                                      | Description                                                                                                              | Default                   |
|------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------|
| `image.repository`                             | Image repository                                                                                                         | `kuvaszmonitoring/kuvasz` |
| `image.tag`                                    | Image tag                                                                                                                | `latest`                  |
| `image.pullPolicy`                             | Image pull policy                                                                                                        | `IfNotPresent`            |
| `service.type`                                 | Service type                                                                                                             | `ClusterIP`               |
| `service.port`                                 | Service port                                                                                                             | `8080`                    |
| `ingress.enabled`                              | Enable ingress                                                                                                           | `false`                   |
| `httpRoute.enabled`                            | Enable Gateway API HTTPRoute                                                                                             | `false`                   |
| `httpRoute.parentRefs`                         | Gateway parent references. Defaults to a Gateway named `gateway` in the release namespace                                | `[]`                      |
| `httpRoute.hostnames`                          | Hostnames matching the HTTPRoute                                                                                         | `[kuvasz.local]`          |
| `httpRoute.matches`                            | Match rules applied to the default service backend                                                                       | See `values.yaml`         |
| `httpRoute.filters`                            | HTTPRoute filters for request/response manipulation                                                                      | `[]`                      |
| `httpRoute.timeouts`                           | HTTPRoute timeout configuration                                                                                          | `{}`                      |
| `httpRoute.extraRules`                         | Additional HTTPRoute rules to append                                                                                     | `[]`                      |
| `auth.enabled`                                 | Enable authentication                                                                                                    | `true`                    |
| `auth.adminUser`                               | Admin username (auto-generated if empty, ignored when OIDC is enabled)                                                   | `""`                      |
| `auth.adminPassword`                           | Admin password (auto-generated if empty, ignored when OIDC is enabled)                                                   | `""`                      |
| `auth.adminApiKey`                             | Optional REST API key. Leave empty to disable REST API key access                                                        | `""`                      |
| `auth.adminMcpApiKey`                          | Optional MCP server API key. Leave empty to disable MCP API key access                                                   | `""`                      |
| `auth.oidc.enabled`                            | Enable OIDC login                                                                                                        | `false`                   |
| `auth.oidc.issuer`                             | OIDC issuer URL                                                                                                          | `""`                      |
| `auth.oidc.clientId`                           | OIDC client ID                                                                                                           | `""`                      |
| `auth.oidc.clientSecret`                       | OIDC client secret, stored in the admin Secret                                                                           | `""`                      |
| `auth.oidc.existingSecret`                     | Existing Secret containing the OIDC client secret                                                                        | `""`                      |
| `auth.oidc.existingSecretClientSecretKey`      | Key in the OIDC existing Secret                                                                                          | `oidc-client-secret`      |
| `auth.oidc.authorizationServer`                | Optional provider hint. Supported values: `AUTH0`, `COGNITO`, `KEYCLOAK`, `MICROSOFT`, `OKTA`, `ORACLE_CLOUD`            | `""`                      |
| `auth.oidc.endSessionEnabled`                  | Log out from the OIDC provider when logging out of Kuvasz                                                                | `true`                    |
| `auth.oidc.allowedEmails`                      | Email allowlist for OIDC users. Empty allows any authenticated OIDC user                                                 | `[]`                      |
| `auth.oidc.requireVerifiedEmail`               | Require `email_verified=true` when `allowedEmails` is configured                                                         | `true`                    |
| `postgresql.enabled`                           | Deploy PostgreSQL                                                                                                        | `true`                    |
| `externalDatabase`                             | External database configuration, check out `values.yaml` in case you would like to use your existing PostgreSQL instance |                           |
| `timezone`                                     | Timezone                                                                                                                 | `UTC`                     |
| `resources`                                    | Resource limits/requests                                                                                                 | See `values.yaml`         |

## Database Options

### Option 1: Internal Database (Default)

The chart will deploy a PostgreSQL StatefulSet:

```yaml
postgresql:
  enabled: true
  auth:
    username: kuvasz
    password: ""  # Auto-generated if not provided
    database: kuvasz
```

### Option 2: External Database

Use your existing PostgreSQL instance:

```yaml
postgresql:
  enabled: false

externalDatabase:
  host: "your-postgres-host"
  port: 5432
  database: "kuvasz-uptime"
  user: "kuvasz-uptime"
  password: "your-password"
  # Or reference existing secret:
  existingSecret: "my-secret"
  existingSecretPasswordKey: "password"
```

## Configuration File

You can provide a `kuvasz.yml` configuration file via the `config.raw` value:

```yaml
config:
  raw: |
    integrations:
      slack:
        - name: my-slack
          webhook-url: 'https://hooks.slack.com/services/...'
          global: true
    # ...
```

## Gateway API HTTPRoute

The chart can expose Kuvasz through a Gateway API `HTTPRoute` instead of, or alongside, an Ingress. The chart creates the `HTTPRoute`; the target `Gateway` must already exist in the cluster.

```yaml
httpRoute:
  enabled: true
  parentRefs:
    - name: gateway
      namespace: gateway-system
      sectionName: https
  hostnames:
    - kuvasz.example.com
  matches:
    - path:
        type: PathPrefix
        value: /
```

If `httpRoute.parentRefs` is empty, the route attaches to a Gateway named `gateway` in the release namespace. `httpRoute.filters`, `httpRoute.timeouts`, and `httpRoute.extraRules` are rendered directly into the `HTTPRoute` spec for advanced Gateway API use cases.

## Authentication

By default, authentication is enabled. You can disable it:

```yaml
auth:
  enabled: false
```

If enabled, credentials are stored in a Kubernetes Secret. You can retrieve the username and password:

```bash
kubectl get secret <release-name>-kuvasz-admin -o jsonpath='{.data.admin-user}' | base64 -d
kubectl get secret <release-name>-kuvasz-admin -o jsonpath='{.data.admin-password}' | base64 -d
```

REST API key and MCP API key authentication are disabled by default. To enable them, configure `auth.adminApiKey` and/or `auth.adminMcpApiKey`:

```yaml
auth:
  adminApiKey: "ThisShouldBeVeryVerySecureToo"
  adminMcpApiKey: "ThisShouldBeVeryVerySecureToo"
```

You can retrieve configured API keys from the admin Secret:

```bash
kubectl get secret <release-name>-kuvasz-admin -o jsonpath='{.data.admin-api-key}' | base64 -d
kubectl get secret <release-name>-kuvasz-admin -o jsonpath='{.data.admin-mcp-api-key}' | base64 -d
```

If you want to manage the admin secret externally (e.g. sealed secrets) you can disable the autogeneration with:
```yaml
externalAdminSecret: true
```

### OIDC authentication

OIDC can be used instead of the built-in username/password login form:

```yaml
auth:
  enabled: true
  oidc:
    enabled: true
    issuer: "https://keycloak.example.com/realms/kuvasz"
    clientId: "kuvasz"
    clientSecret: "your-client-secret"
    authorizationServer: "KEYCLOAK"
    allowedEmails:
      - admin@example.com
    requireVerifiedEmail: true
```

When OIDC is enabled, `auth.adminUser` and `auth.adminPassword` are ignored by the application. `auth.adminApiKey` remains independent and can still be used for REST API access.

Supported `auth.oidc.authorizationServer` values are `AUTH0`, `COGNITO`, `KEYCLOAK`, `MICROSOFT`, `OKTA`, and `ORACLE_CLOUD`.

For production, prefer storing the OIDC client secret in an existing Kubernetes Secret:

```yaml
auth:
  oidc:
    enabled: true
    issuer: "https://keycloak.example.com/realms/kuvasz"
    clientId: "kuvasz"
    existingSecret: "kuvasz-oidc"
    existingSecretClientSecretKey: "client-secret"
```

When `externalAdminSecret=true`, `auth.oidc.clientSecret` cannot be used because the chart does not create or update the admin Secret. In that mode, either put an `oidc-client-secret` key in the external admin Secret or use `auth.oidc.existingSecret`.

## Upgrading

```bash
helm upgrade my-kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz-uptime \
  --version <NEW_VERSION> \
  --values values.yaml \
  --namespace kuvasz-uptime
```

## Uninstalling

```bash
helm uninstall my-kuvasz
```

## License

[AGPL-3.0](https://github.com/kuvasz-uptime/kuvasz/blob/main/LICENSE)
