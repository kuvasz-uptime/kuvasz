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

| Parameter            | Description                                                                                                              | Default                   |
|----------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------|
| `image.repository`   | Image repository                                                                                                         | `kuvaszmonitoring/kuvasz` |
| `image.tag`          | Image tag                                                                                                                | `latest`                  |
| `image.pullPolicy`   | Image pull policy                                                                                                        | `IfNotPresent`            |
| `service.type`       | Service type                                                                                                             | `ClusterIP`               |
| `service.port`       | Service port                                                                                                             | `8080`                    |
| `ingress.enabled`    | Enable ingress                                                                                                           | `false`                   |
| `auth.enabled`       | Enable authentication                                                                                                    | `true`                    |
| `auth.adminUser`     | Admin username (auto-generated if empty)                                                                                 | `""`                      |
| `auth.adminPassword` | Admin password (auto-generated if empty)                                                                                 | `""`                      |
| `auth.adminApiKey`   | Admin API key (auto-generated if empty)                                                                                  | `""`                      |
| `postgresql.enabled` | Deploy PostgreSQL                                                                                                        | `true`                    |
| `externalDatabase`   | External database configuration, check out `values.yaml` in case you would like to use your existing PostgreSQL instance |                           |
| `timezone`           | Timezone                                                                                                                 | `UTC`                     |
| `resources`          | Resource limits/requests                                                                                                 | See `values.yaml`         |

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

## Authentication

By default, authentication is enabled. You can disable it:

```yaml
auth:
  enabled: false
```

If enabled, credentials are stored in a Kubernetes Secret. You can retrieve them:

```bash
kubectl get secret <release-name>-kuvasz-admin -o jsonpath='{.data.admin-user}' | base64 -d
kubectl get secret <release-name>-kuvasz-admin -o jsonpath='{.data.admin-password}' | base64 -d
kubectl get secret <release-name>-kuvasz-admin -o jsonpath='{.data.admin-api-key}' | base64 -d
```

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

[Apache License 2.0](https://github.com/kuvasz-uptime/kuvasz/blob/main/LICENSE)
