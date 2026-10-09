#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
if [ ! -f docker/tls/server.crt ] || [ ! -f docker/tls/server.key ]; then
  docker/tls/generate-certs.sh
fi
docker compose up --build --wait --wait-timeout 300
printf '\nКлиент: https://localhost:%s/\n' "${CLIENT_HTTPS_PORT:-3000}"
printf 'Swagger UI: https://localhost:%s/swagger-ui.html\n' "${CLIENT_HTTPS_PORT:-3000}"
printf 'Eureka: http://localhost:%s/\n' "${EUREKA_PORT:-8761}"
printf 'HAProxy: http://localhost:%s/stats\n' "${HAPROXY_STATS_PORT:-9001}"
