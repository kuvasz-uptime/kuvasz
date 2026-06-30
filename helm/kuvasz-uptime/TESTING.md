# Testing the Kuvasz Helm Chart

This document provides commands to test the Helm chart rendering with custom values.

## Prerequisites

- Helm 3.2.0+ installed
- Access to a Kubernetes cluster (optional, for full deployment testing)

## Basic Template Rendering

### 1. Render with default values

```bash
cd helm/kuvasz-uptime
helm template test-release . > rendered-default.yaml
```

### 2. Render with custom values (external database)

```bash
helm template test-release . -f test-values.yaml > rendered-external-db.yaml
```

This uses the `test-values.yaml` file which configures:

- External PostgreSQL database
- Custom authentication credentials
- Ingress configuration
- Custom resource limits
- Configuration file with Slack integration

### 3. Render with internal database

```bash
helm template test-release . -f test-values-internal-db.yaml > rendered-internal-db.yaml
```

This uses the `test-values-internal-db.yaml` file which configures:

- Internal PostgreSQL database (deployed as StatefulSet)
- Custom authentication credentials
- Default service configuration

### 4. Render with OIDC authentication

```bash
helm template test-release . -f test-values-oidc.yaml > rendered-oidc.yaml
```

This uses the `test-values-oidc.yaml` file which configures:

- External PostgreSQL database
- OIDC authentication
- Existing Secret for the OIDC client secret

### 5. Render with Gateway API HTTPRoute

```bash
helm template test-release . -f test-values-httproute.yaml > rendered-httproute.yaml
```

This uses the `test-values-httproute.yaml` file which configures:

- External PostgreSQL database
- Gateway API HTTPRoute
- Existing Gateway parent reference

## Validate Chart

### Lint the chart

```bash
helm lint .
```

### Validate against Kubernetes schema

```bash
helm template test-release . -f test-values.yaml | kubectl apply --dry-run=client -f -
```

## Test Specific Scenarios

### Test with authentication disabled

```bash
helm template test-release . \
  --set auth.enabled=false \
  --set postgresql.enabled=false \
  --set externalDatabase.host=postgres.example.com \
  --set externalDatabase.user=kuvasz \
  --set externalDatabase.password=secret \
  --set externalDatabase.database=kuvasz
```

### Test with auto-generated user/password and disabled API keys

```bash
helm template test-release . \
  --set auth.adminUser="" \
  --set auth.adminPassword="" \
  --set auth.adminApiKey="" \
  --set auth.adminMcpApiKey="" \
  --set postgresql.enabled=true \
  --set postgresql.auth.password=""
```

### Test with existing secrets

```bash
helm template test-release . \
  --set postgresql.enabled=false \
  --set externalDatabase.existingSecret=my-postgres-secret \
  --set externalDatabase.existingSecretPasswordKey=password
```

### Test with OIDC

```bash
helm template test-release . \
  --set postgresql.enabled=false \
  --set externalDatabase.host=postgres.example.com \
  --set externalDatabase.user=kuvasz \
  --set externalDatabase.password=secret \
  --set externalDatabase.database=kuvasz \
  --set auth.oidc.enabled=true \
  --set auth.oidc.issuer=https://keycloak.example.com/realms/kuvasz \
  --set auth.oidc.clientId=kuvasz \
  --set auth.oidc.clientSecret=oidc-client-secret
```

### Test with Gateway API HTTPRoute

```bash
helm template test-release . \
  --set httpRoute.enabled=true \
  --set httpRoute.parentRefs[0].name=gateway \
  --set httpRoute.parentRefs[0].namespace=gateway-system \
  --set httpRoute.hostnames[0]=kuvasz.example.com
```

## Verify Generated Resources

### Check all generated resources

```bash
helm template test-release . -f test-values.yaml | grep "^kind:" | sort | uniq -c
```

Expected output should include:
- ServiceAccount
- Secret (admin credentials)
- Secret (database credentials, if external)
- ConfigMap (kuvasz.yml)
- Service
- Deployment
- Ingress (if enabled)
- HTTPRoute (if enabled)

### Verify database configuration

```bash
# Check database host
helm template test-release . -f test-values.yaml | grep -A 1 "DATABASE_HOST"

# Check database credentials secret reference
helm template test-release . -f test-values.yaml | grep -A 3 "DATABASE_PASSWORD"
```

### Verify authentication configuration

```bash
# Check if auth is enabled
helm template test-release . -f test-values.yaml | grep "ENABLE_AUTH"

# Check admin credentials secret reference
helm template test-release . -f test-values.yaml | grep -A 2 "ADMIN_USER"

# Check optional API key configuration
helm template test-release . -f test-values.yaml | grep -A 2 "ADMIN_API_KEY"
helm template test-release . -f test-values.yaml | grep -A 2 "ADMIN_MCP_API_KEY"

# Check OIDC configuration
helm template test-release . -f test-values-oidc.yaml | grep -A 20 "ENABLE_OIDC"

# Check Gateway API HTTPRoute configuration
helm template test-release . -f test-values-httproute.yaml | grep -A 20 "kind: HTTPRoute"
```

## Test with Different Namespaces

```bash
helm template test-release . -f test-values.yaml --namespace production
```

## Dry-run Installation (requires cluster access)

```bash
helm install test-release . -f test-values.yaml --dry-run --debug
```

## Full Installation Test (requires cluster access)

```bash
# Install
helm install test-release . -f test-values.yaml

# Check status
helm status test-release

# Get admin credentials
kubectl get secret test-release-kuvasz-admin -o jsonpath='{.data.admin-user}' | base64 -d
kubectl get secret test-release-kuvasz-admin -o jsonpath='{.data.admin-password}' | base64 -d

# Uninstall
helm uninstall test-release
```

## Troubleshooting

### Template rendering errors

If you encounter template errors, use debug mode:

```bash
helm template test-release . -f test-values.yaml --debug
```

### Validate YAML syntax

```bash
helm template test-release . -f test-values.yaml | yamllint -
```

### Check for missing values

```bash
helm template test-release . -f test-values.yaml 2>&1 | grep -i "error\|missing\|undefined"
```
