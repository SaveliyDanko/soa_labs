#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

resolve_java
log 'Stopping the Java web client'
stop_pid_file 'Web client' "$BUNDLE_ROOT/run/web-client.pid"

if pid_is_running "$BUNDLE_ROOT/run/wildfly.pid"; then
    log 'Stopping WildFly'
    env JAVA_OPTS="$CLIENT_JVM_OPTIONS" "$WILDFLY_CLI" \
        --connect --controller="127.0.0.1:$WILDFLY_MANAGEMENT_PORT" \
        --command=':shutdown(suspend-timeout=10)' >/dev/null 2>&1 || true
    remaining=$SHUTDOWN_TIMEOUT_SECONDS
    while pid_is_running "$BUNDLE_ROOT/run/wildfly.pid" && (( remaining > 0 )); do
        sleep 1
        ((remaining--)) || true
    done
    if ! pid_is_running "$BUNDLE_ROOT/run/wildfly.pid"; then
        rm -f -- "$BUNDLE_ROOT/run/wildfly.pid"
        log 'WildFly stopped'
    else
        stop_pid_file 'WildFly' "$BUNDLE_ROOT/run/wildfly.pid"
    fi
else
    log 'WildFly is not running'
fi

if payara_is_running; then
    log 'Stopping Payara'
    run_asadmin --port "$PAYARA_ADMIN_PORT" stop-domain \
        --domaindir "$PAYARA_DOMAINS_DIR" "$PAYARA_DOMAIN_NAME"
else
    log 'Payara is not running'
fi
log 'All services are stopped'
