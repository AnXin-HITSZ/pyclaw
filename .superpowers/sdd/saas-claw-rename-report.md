# claw-saas -> saas-claw Rename Report

**Date:** 2026-07-20
**Status:** Complete
**Files changed:** 31

## Scope

Comprehensive rename of K8s resource names, Helm chart names, labels, secrets, service DNS names,
Helm template function names, GitHub Actions release names, and chart directory paths.

## Categories changed

| Category | Pattern | New |
|---|---|---|
| Chart names | `claw-saas`, `claw-saas-mysql` | `saas-claw`, `saas-claw-mysql` |
| K8s secret names | `claw-saas-secret`, `claw-saas-mysql-secret` | `saas-claw-secret`, `saas-claw-mysql-secret` |
| K8s service/statefulset DNS | `claw-saas-mysql` | `saas-claw-mysql` |
| K8s TLS secret | `claw-saas-tls` (already saas-claw-tls) | no change needed |
| K8s labels | `part-of: claw-saas` | `part-of: saas-claw` |
| Helm template functions | `claw-saas.*`, `claw-saas-mysql.*` | `saas-claw.*`, `saas-claw-mysql.*` |
| Datasource URLs | `jdbc:mysql://claw-saas-mysql:3306/` | `jdbc:mysql://saas-claw-mysql:3306/` |
| MySQL config file | `claw-saas.cnf` | `saas-claw.cnf` |
| GitHub Actions release names | `claw-saas`, `claw-saas-mysql` | `saas-claw`, `saas-claw-mysql` |
| GitHub Actions chart paths | `./deploy/helm/claw-saas`, `./deploy/helm/claw-saas-mysql` | `./deploy/helm/saas-claw`, `./deploy/helm/saas-claw-mysql` |
| ECS deploy path | `/opt/claw-saas` | `/opt/saas-claw` |
| Values file names | `claw-saas-values-k3s.yaml`, `claw-saas-mysql-values-k3s.yaml` | `saas-claw-values-k3s.yaml`, `saas-claw-mysql-values-k3s.yaml` |

## Files changed

### Helm chart: claw-saas (17 files)
- `deploy/helm/claw-saas/Chart.yaml`
- `deploy/helm/claw-saas/values.yaml`
- `deploy/helm/claw-saas/templates/_helpers.tpl`
- `deploy/helm/claw-saas/templates/secret.yaml`
- `deploy/helm/claw-saas/templates/configmap.yaml`
- `deploy/helm/claw-saas/templates/ingress.yaml`
- `deploy/helm/claw-saas/templates/serviceaccount.yaml`
- `deploy/helm/claw-saas/templates/gateway-deployment.yaml`
- `deploy/helm/claw-saas/templates/backend-for-frontend-deployment.yaml`
- `deploy/helm/claw-saas/templates/claw-service-deployment.yaml`
- `deploy/helm/claw-saas/templates/runtime-service-deployment.yaml`
- `deploy/helm/claw-saas/templates/agent-marketplace-service-deployment.yaml`
- `deploy/helm/claw-saas/templates/billing-service-deployment.yaml`
- `deploy/helm/claw-saas/templates/skill-marketplace-service-deployment.yaml`
- `deploy/helm/claw-saas/templates/pyclaw-runtime-api-deployment.yaml`
- `deploy/helm/claw-saas/templates/redis-deployment.yaml`
- `deploy/helm/claw-saas/templates/redis-service.yaml`

### Helm chart: claw-saas-mysql (8 files)
- `deploy/helm/claw-saas-mysql/Chart.yaml`
- `deploy/helm/claw-saas-mysql/values.yaml`
- `deploy/helm/claw-saas-mysql/templates/_helpers.tpl`
- `deploy/helm/claw-saas-mysql/templates/statefulset.yaml`
- `deploy/helm/claw-saas-mysql/templates/service.yaml`
- `deploy/helm/claw-saas-mysql/templates/secret.yaml`
- `deploy/helm/claw-saas-mysql/templates/configmap.yaml`
- `deploy/helm/claw-saas-mysql/templates/bootstrap-databases-job.yaml`
- `deploy/helm/claw-saas-mysql/templates/NOTES.txt`

### GitHub Actions (2 files)
- `.github/workflows/deploy.yml`
- `.github/workflows/deploy-pyclaw-mysql.yml`

### Example values files (3 files)
- `claw-saas-values-k3s.example.yaml`
- `claw-saas-mysql-values-k3s.example.yaml`
- `pyclaw-mysql-values-k3s.example.yaml`

## Not changed (intentionally excluded)

- Docker image names: `clawsaas/gateway` etc.
- OSS bucket: `claw-saas-artifacts`
- Database names: `claw_saas_control`, `claw_saas_runtime`
- Maven artifact IDs: `claw-saas-backend`
- Java package names
- Documentation prose in `docs/` and `CLAUDE.md`

## Verification

```
grep -rn "claw-saas" deploy/ .github/ *.example.yaml
```
Returns: **0 matches** in K8s/Helm/GHA files.

Remaining `claw-saas` occurrences are only in:
- Maven pom.xml files (`claw-saas-backend` artifactId)
- OSS bucket references (`claw-saas-artifacts`)
- Documentation files (`docs/`, `CLAUDE.md`)
