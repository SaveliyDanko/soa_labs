#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

validate_bundle
failed=0

if payara_is_running; then
    printf 'Payara:     RUNNING  https://localhost:%s/api/study-groups\n' "$PAYARA_HTTPS_PORT"
else
    printf 'Payara:     STOPPED\n'
    failed=1
fi
if pid_is_running "$BUNDLE_ROOT/run/wildfly.pid"; then
    printf 'WildFly:    RUNNING  https://localhost:%s/isu\n' "$WILDFLY_HTTPS_PORT"
else
    printf 'WildFly:    STOPPED\n'
    failed=1
fi
if pid_is_running "$BUNDLE_ROOT/run/web-client.pid"; then
    printf 'Web client: RUNNING  https://localhost:%s/\n' "$CLIENT_HTTPS_PORT"
else
    printf 'Web client: STOPPED\n'
    failed=1
fi

exit "$failed"
