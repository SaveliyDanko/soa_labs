#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${DB_PASSWORD:?Set DB_PASSWORD before deploying; reuse the same value for an existing database.}"
namespace=soa-lab4
image_prefix=${IMAGE_PREFIX:-soa-lab4}
image_tag=${IMAGE_TAG:-dev}
kubectl apply -f k8s/namespace.yaml
kubectl -n "$namespace" create secret generic lab-secrets \
  --from-literal="db-password=$DB_PASSWORD" --from-literal="sentry-dsn=${SENTRY_DSN:-}" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k .
for module in config-service discovery-service api-gateway study-groups-service isu-service isu-rest-adapter mule-esb client; do
  kubectl -n "$namespace" set image "deployment/$module" "$module=$image_prefix/$module:$image_tag"
done
public_base=${PUBLIC_BASE_URL:-http://localhost:3000}
kubectl -n "$namespace" set env deployment/isu-service "SOAP_PUBLISHED_URL=${public_base%/}/soap/isu"
# Wait for the new configuration before restarting its consumers.
kubectl -n "$namespace" rollout restart deployment/config-service deployment/discovery-service
kubectl -n "$namespace" rollout status deployment/config-service --timeout=480s
kubectl -n "$namespace" rollout status deployment/discovery-service --timeout=480s
# Also refresh pods when reloading the local :dev tag into kind.
kubectl -n "$namespace" rollout restart deployment/study-groups-service deployment/isu-service \
  deployment/isu-rest-adapter deployment/api-gateway deployment/mule-esb deployment/client
kubectl -n "$namespace" rollout status statefulset/postgres --timeout=300s
for module in study-groups-service mule-esb isu-service isu-rest-adapter api-gateway client; do
  kubectl -n "$namespace" rollout status "deployment/$module" --timeout=480s
done
printf '\nOpen the UI with: kubectl -n %s port-forward service/client 3000:8080\n' "$namespace"
