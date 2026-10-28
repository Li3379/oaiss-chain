# OAISS CHAIN - Kubernetes Manifests

Production-ready K8s deployment for the OAISS CHAIN dual-carbon platform.

## Prerequisites

- Kubernetes cluster (1.25+)
- [nginx ingress controller](https://kubernetes.github.io/ingress-nginx/deploy/)
- [metrics-server](https://github.com/kubernetes-sigs/metrics-server) (for HPA)
- [cert-manager](https://cert-manager.io/) (optional, for TLS)
- Container images pushed to your registry

## Quick Deploy

```bash
# 1. Create namespace first
kubectl apply -f k8s/namespace.yaml

# 2. Apply all manifests
kubectl apply -f k8s/

# 3. Verify
kubectl -n oaiss-chain get pods -w
```

## File Overview

| File | Description |
|------|-------------|
| `namespace.yaml` | Namespace `oaiss-chain` |
| `configmap.yaml` | Non-secret environment variables |
| `secret.yaml` | Secret template (replace placeholders before deploy) |
| `backend-deployment.yaml` | Spring Boot backend — 2 replicas |
| `backend-service.yaml` | ClusterIP service for backend |
| `frontend-deployment.yaml` | Nginx + Vue 3 SPA — 2 replicas |
| `frontend-service.yaml` | ClusterIP service for frontend |
| `ml-service-deployment.yaml` | Python FastAPI ML service — 1 replica |
| `ml-service-service.yaml` | ClusterIP service for ML service |
| `hpa.yaml` | HorizontalPodAutoscalers (backend 2-5, frontend 2-3) |
| `ingress.yaml` | Nginx Ingress with TLS, CORS, rate-limiting |
| `pdb.yaml` | PodDisruptionBudgets (minAvailable: 1) |

## Updating Secrets

Secrets use base64-encoded placeholder values. Replace before deploying:

```bash
# Generate base64 value
echo -n 'your-real-password' | base64

# Or apply directly from literal values
kubectl -n oaiss-chain create secret generic oaiss-chain-secret \
  --from-literal=DB_USERNAME=oaiss \
  --from-literal=DB_PASSWORD='your-db-password' \
  --from-literal=JWT_SECRET='your-jwt-secret-min-32-chars' \
  --from-literal=REDIS_PASSWORD='your-redis-password' \
  --from-literal=MINIO_ACCESS_KEY='your-minio-access-key' \
  --from-literal=MINIO_SECRET_KEY='your-minio-secret-key' \
  --from-literal=ML_SERVICE_SECRET='your-ml-secret' \
  --from-literal=RSA_KEK='your-rsa-kek' \
  --dry-run=client -o yaml | kubectl apply -f -
```

## Updating ConfigMap

```bash
# Edit in place
kubectl -n oaiss-chain edit configmap oaiss-chain-config

# Then restart pods to pick up changes
kubectl -n oaiss-chain rollout restart deployment/backend
```

## Scaling

```bash
# Manual scale (HPA must be removed or disabled first)
kubectl -n oaiss-chain scale deployment/backend --replicas=3

# Check HPA status
kubectl -n oaiss-chain get hpa

# View HPA events
kubectl -n oaiss-chain describe hpa backend-hpa
```

## Monitoring

```bash
# Pod status
kubectl -n oaiss-chain get pods -o wide

# Service endpoints
kubectl -n oaiss-chain get endpoints

# Logs (tail)
kubectl -n oaiss-chain logs -l component=backend --tail=100 -f

# Health check
kubectl -n oaiss-chain exec deploy/backend -- curl -s localhost:8080/api/v1/actuator/health

# Ingress status
kubectl -n oaiss-chain get ingress
```

## Rolling Updates

```bash
# Update image
kubectl -n oaiss-chain set image deployment/backend \
  backend=ghcr.io/your-org/oaiss-chain-backend:v1.1.0

# Watch rollout
kubectl -n oaiss-chain rollout status deployment/backend

# Rollback if needed
kubectl -n oaiss-chain rollout undo deployment/backend
```

## Production Checklist

- [ ] Replace all secret placeholder values
- [ ] Update `DB_URL` in configmap to point to production MySQL
- [ ] Update `CORS_ALLOWED_ORIGINS` to production domain
- [ ] Update ingress `host` and TLS `secretName`
- [ ] Update image tags from `:latest` to specific version
- [ ] Set up cert-manager or manually create TLS secret
- [ ] Enable monitoring (ServiceMonitor for Prometheus Operator)
- [ ] Review and adjust resource limits based on actual usage
- [ ] Configure network policies if required
