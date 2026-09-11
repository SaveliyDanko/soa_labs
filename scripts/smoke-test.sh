#!/usr/bin/env sh
set -eu

payara_url=${PAYARA_URL:-https://localhost:8181}
isu_url=${ISU_URL:-https://localhost:8443}
client_url=${CLIENT_URL:-https://localhost:3000}

created=$(curl -ksS --fail-with-body -H 'Content-Type: application/json' \
  -d '{"name":"Smoke P3110","coordinates":{"x":1.5,"y":-2},"studentsCount":25,"formOfEducation":"FULL_TIME_EDUCATION","semesterEnum":"THIRD","groupAdmin":{"name":"Anna","nationality":"USA"}}' \
  "$payara_url/api/study-groups")
id=$(printf '%s' "$created" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p')
test -n "$id"

curl -ksS --fail-with-body "$payara_url/api/study-groups?sort=studentsCount,desc&filter=name:contains:Smoke" >/dev/null
curl -ksS --fail-with-body -X POST "$isu_url/isu/group/$id/change-edu-form/DISTANCE_EDUCATION" >/dev/null
curl -ksS --fail-with-body -X POST "$isu_url/isu/group/$id/expel-all" >/dev/null
curl -ksS --fail-with-body "$client_url/" >/dev/null
curl -ksS --fail-with-body "$client_url/swagger-ui.html" >/dev/null
curl -ksS --fail-with-body "$client_url/openapi.yaml" >/dev/null
curl -ksS --fail-with-body "$client_url/webjars/swagger-ui/5.18.2/swagger-ui.css" >/dev/null
curl -ksS --fail-with-body "$client_url/api/study-groups" >/dev/null

if curl -sS --max-time 2 "${PAYARA_HTTP_URL:-http://localhost:8181}/api/study-groups" >/dev/null 2>&1; then
  echo 'ERROR: Payara accepted unencrypted HTTP on its public TLS port' >&2
  exit 1
fi
if curl -sS --max-time 2 "${ISU_HTTP_URL:-http://localhost:8443}/isu/" >/dev/null 2>&1; then
  echo 'ERROR: WildFly accepted unencrypted HTTP on its public TLS port' >&2
  exit 1
fi

printf 'Smoke test passed (group %s).\n' "$id"
