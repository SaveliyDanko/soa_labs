#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KUBECONFIG=${KIND_KUBECONFIG:-"$PWD/.local/kubeconfig"}
mkdir -p "$(dirname "$KUBECONFIG")"
if ! kind get clusters | grep -Fxq soa-lab4; then
  kind create cluster --config k8s/kind.yaml --kubeconfig "$KUBECONFIG" --wait 120s
else
  kind export kubeconfig --name soa-lab4 --kubeconfig "$KUBECONFIG"
fi
./scripts/build-images.sh
for module in config-service discovery-service api-gateway study-groups-service isu-service isu-rest-adapter mule-esb client; do
  kind load docker-image --name soa-lab4 "${IMAGE_PREFIX:-soa-lab4}/$module:${IMAGE_TAG:-dev}"
done
./scripts/deploy-k8s.sh
printf '\nFor subsequent kubectl commands: export KUBECONFIG=%q\n' "$KUBECONFIG"
