#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"

if [ ! -f docker/tls/server.p12 ]; then
  docker/tls/generate-certs.sh
fi

mvn clean package
docker compose up --build -d

printf '\nClient:     https://localhost:3000/\n'
printf 'Swagger UI: https://localhost:3000/swagger-ui.html\n'
printf 'Payara API: https://localhost:8181/api/study-groups\n'
printf 'WildFly:    https://localhost:8443/isu/\n'
