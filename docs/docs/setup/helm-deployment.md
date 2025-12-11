# Deploy Kuvasz via Helm Chart

Kuvasz provides an official Helm chart for deploying to Kubernetes clusters. This guide will walk you through the deployment process.

## Prerequisites

- A running Kubernetes cluster
- [Helm 4.x](https://helm.sh/docs/intro/install/) installed
- `kubectl` configured to access your cluster

## Quick start

### 1. Add the official Helm repository

The official Helm chart is published to GitHub's OCI registry. To install it, you can pull it directly:

```bash
# Install with default values
helm install my-kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz-uptime --version <VERSION>

# Or install with custom values
helm install my-kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz-uptime --version <VERSION> -f my-values.yaml
```

Replace `<VERSION>` with the desired version (e.g., `3.3.0`). You can find the available versions on the [GitHub releases page](https://github.com/kuvasz-uptime/kuvasz/releases){ target="_blank" }.

### 2. Create a values File

Create a file called `values.yaml` to customize your deployment. Here's a minimal example:

```yaml
# Database configuration
postgresql:
  enabled: true
  auth:
    username: kuvasz-uptime
    password: "YourSuperSecretDbPassword"  # Change this!
    database: kuvasz-uptime

# Authentication configuration
auth:
  adminUser: "YourSuperSecretUsername"      # Change this!
  adminPassword: "YourSuperSecretPassword"  # Change this!
  adminApiKey: "ThisShouldBeVeryVerySecureToo"  # Change this!

# Ingress configuration (optional)
ingress:
  enabled: false
  # Uncomment and configure if you want to expose Kuvasz externally
  # className: "nginx"
  # hosts:
  #   - host: kuvasz-uptime.example.com
  #     paths:
  #       - path: /
  #         pathType: Prefix
```

!!! note "Credential Requirements"
    - `adminPassword` must be at least 12 characters and must not be equal to `adminUser`
    - `adminApiKey` must be at least 16 characters

### 3. Install the Chart

Install Kuvasz using your custom values:

```bash
helm install kuvasz-uptime oci://ghcr.io/kuvasz-uptime/kuvasz-uptime \
  --version <VERSION> \
  --values values.yaml \
  --namespace kuvasz-uptime \
  --create-namespace
```

### 4. Verify the installation

Check that all pods are running:

```bash
kubectl get pods -n kuvasz-uptime
```

You should see the Kuvasz application pod and PostgreSQL pod (if enabled) in a `Running` state.

### 5. Access Kuvasz

By default, Kuvasz is exposed via a ClusterIP service. To access it locally, you can use port-forwarding:

```bash
kubectl port-forward -n kuvasz-uptime svc/kuvasz-uptime 8080:8080
```

Then open your browser to [http://localhost:8080](http://localhost:8080).

## Configuration options

### Using an external PostgreSQL database

If you already have a PostgreSQL database, you can disable the bundled PostgreSQL and configure an external connection:

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
  # existingSecret: "my-secret"
  # existingSecretPasswordKey: "password"
```

### Persistent storage

By default, the chart creates PersistentVolumeClaims for the PostgreSQL database. You can customize the storage:

```yaml
postgresql:
  enabled: true
  persistence:
    enabled: true
    storageClass: "your-storage-class"
    size: 10Gi
```

### Resource limits

Configure resource requests and limits for Kuvasz (these are the recommended minimums):

```yaml
resources:
  limits:
    cpu: 1000m
    memory: 768Mi
  requests:
    cpu: 100m
    memory: 256Mi
```

### Ingress configuration

To expose Kuvasz externally with an Ingress controller:

```yaml
ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
  hosts:
    - host: kuvasz-uptime.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: kuvasz-uptime-tls
      hosts:
        - kuvasz-uptime.example.com
```

### Configuring Kuvasz

You can provide a custom [YAML configuration](configuration.md) by including it in your values file:

```yaml
config:
  raw: |
    integrations:
      slack:
        - name: team-notifications
          webhook-url: 'https://hooks.slack.com/services/XXX/YYY/ZZZ'
          global: true
    
    http-monitors:
      - name: example-monitor
        url: https://example.com
        uptime-check-interval: 60
```

## Upgrading

To upgrade to a new version of Kuvasz:

```bash
helm upgrade kuvasz-uptime oci://ghcr.io/kuvasz-uptime/kuvasz-uptime \
  --version <NEW_VERSION> \
  --values values.yaml \
  --namespace kuvasz-uptime
```

## Uninstalling

To remove Kuvasz from your cluster:

```bash
helm uninstall kuvasz-uptime --namespace kuvasz-uptime
```

!!! warning "Data Persistence"
This will not delete the PersistentVolumeClaims by default. To also delete the stored data, run:

```bash
kubectl delete pvc -n kuvasz-uptime --all
```

## Configuration reference

For a complete list of all available configuration options, you can inspect the chart's values:

```bash
helm show values oci://ghcr.io/kuvasz-uptime/kuvasz-uptime --version <VERSION>
```

## Troubleshooting

### Checking logs

```bash
kubectl logs -n kuvasz-uptime deployment/kuvasz-uptime -f
```

### Database connection issues

If your Kuvasz instance can't connect to the database, verify:

1. PostgreSQL pod is running: `kubectl get pods -n kuvasz-uptime`
2. Database credentials are correct in your values file
3. Network policies allow communication between pods

### Health check

Check if Kuvasz is healthy using the health endpoint:

```bash
kubectl exec -n kuvasz-uptime deployment/kuvasz-uptime -- wget -q -O- http://localhost:8080/api/v2/health
```

## Additional resources

- [Helm Documentation](https://helm.sh/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
