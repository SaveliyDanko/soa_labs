#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
image_prefix=${IMAGE_PREFIX:-soa-lab4}
image_tag=${IMAGE_TAG:-dev}
for module in config-service discovery-service api-gateway study-groups-service isu-rest-adapter; do
  docker build -f docker/spring/Dockerfile --build-arg "MODULE=$module" -t "$image_prefix/$module:$image_tag" .
done
docker build -f docker/soap/Dockerfile -t "$image_prefix/isu-service:$image_tag" .
docker build -f docker/mule/Dockerfile -t "$image_prefix/mule-esb:$image_tag" .
docker build -f web-client/Dockerfile -t "$image_prefix/client:$image_tag" .
