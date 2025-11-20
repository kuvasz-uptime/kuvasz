# Helm Chart Deployment

Kuvasz provides an official Helm chart for deploying to Kubernetes clusters. This guide will walk you through the deployment process using Helm.

## Prerequisites

- A running Kubernetes cluster
- [Helm 4.x](https://helm.sh/docs/intro/install/) installed
- `kubectl` configured to access your cluster

## Quick Start

### 1. Add the Kuvasz Helm Repository

The Kuvasz Helm chart is published to GitHub's OCI registry. To install it, you can pull it directly:

```bash
helm install kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz --version <VERSION>
```

Replace `<VERSION>` with the desired version (e.g., `3.1.0`). You can find available versions on the [GitHub releases page](https://github.com/kuvasz-uptime/kuvasz/releases).

### 2. Create a Values File

Create a file called `values.yaml` to customize your deployment. Here's a minimal example:

```yaml
# Database configuration
postgresql:
  enabled: true
  auth:
    username: kuvasz
    password: "YourSuperSecretDbPassword"  # Change this!
    database: kuvasz

# Kuvasz configuration
config:
  adminUser: "YourSuperSecretUsername"      # Change this!
  adminPassword: "YourSuperSecretPassword"  # Change this!
  adminApiKey: "ThisShouldBeVeryVerySecureToo"  # Change this!

# Ingress configuration (optional)
ingress:
  enabled: false
  # Uncomment and configure if you want to expose Kuvasz externally
  # className: "nginx"
  # hosts:
  #   - host: kuvasz.example.com
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
helm install kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz \
  --version <VERSION> \
  --values values.yaml \
  --namespace kuvasz \
  --create-namespace
```

### 4. Verify the Installation

Check that all pods are running:

```bash
kubectl get pods -n kuvasz
```

You should see the Kuvasz application pod and PostgreSQL pod (if enabled) in a `Running` state.

### 5. Access Kuvasz

By default, Kuvasz is exposed via a ClusterIP service. To access it locally, you can use port-forwarding:

```bash
kubectl port-forward -n kuvasz svc/kuvasz 8080:8080
```

Then open your browser to [http://localhost:8080](http://localhost:8080).

## Configuration Options

### Using an External PostgreSQL Database

If you already have a PostgreSQL database, you can disable the bundled PostgreSQL and configure an external connection:

```yaml
postgresql:
  enabled: false

config:
  database:
    host: "your-postgres-host.example.com"
    port: 5432
    name: "kuvasz"
    user: "kuvasz"
    password: "YourExternalDbPassword"
```

### Persistent Storage

By default, the chart creates PersistentVolumeClaims for the PostgreSQL database. You can customize the storage:

```yaml
postgresql:
  enabled: true
  primary:
    persistence:
      enabled: true
      storageClass: "your-storage-class"
      size: 10Gi
```

### Resource Limits

Configure resource requests and limits for Kuvasz:

```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "384Mi"
    cpu: "500m"
```

### Ingress Configuration

To expose Kuvasz externally with an Ingress controller:

```yaml
ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
  hosts:
    - host: kuvasz.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: kuvasz-tls
      hosts:
        - kuvasz.example.com
```

### Kuvasz Configuration File

You can provide a custom Kuvasz YAML configuration by including it in your values file:

```yaml
configFile: |
  integrations:
    slack:
      - name: team-notifications
        webhook-url: 'https://hooks.slack.com/services/XXX/YYY/ZZZ'
        global: true
  
  monitors:
    - name: example-monitor
      url: https://example.com
      interval: 60
```

## Upgrading

To upgrade to a new version of Kuvasz:

```bash
helm upgrade kuvasz oci://ghcr.io/kuvasz-uptime/kuvasz \
  --version <NEW_VERSION> \
  --values values.yaml \
  --namespace kuvasz
```

## Uninstalling

To remove Kuvasz from your cluster:

```bash
helm uninstall kuvasz --namespace kuvasz
```

!!! warning "Data Persistence"
    This will not delete the PersistentVolumeClaims by default. To also delete the stored data, run:
    
    ```bash
    kubectl delete pvc -n kuvasz --all
    ```

## Configuration Reference

For a complete list of all available configuration options, you can inspect the chart's values:

```bash
helm show values oci://ghcr.io/kuvasz-uptime/kuvasz --version <VERSION>
```

## Troubleshooting

### Checking Logs

To view Kuvasz logs:

```bash
kubectl logs -n kuvasz deployment/kuvasz -f
```

### Database Connection Issues

If Kuvasz can't connect to the database, verify:

1. PostgreSQL pod is running: `kubectl get pods -n kuvasz`
2. Database credentials are correct in your values file
3. Network policies allow communication between pods

### Health Check

Check if Kuvasz is healthy using the health endpoint:

```bash
kubectl exec -n kuvasz deployment/kuvasz -- wget -q -O- http://localhost:8080/api/v2/health
```

## Additional Resources

- [Helm Documentation](https://helm.sh/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Kuvasz Configuration Guide](../configuration/)
- [GitHub Repository](https://github.com/kuvasz-uptime/kuvasz)
