#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
BUNDLE_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
CONFIG_FILE=${SOA_CONFIG_FILE:-$BUNDLE_ROOT/config/runtime.env}

if [[ ! -r "$CONFIG_FILE" ]]; then
    printf 'Configuration file is not readable: %s\n' "$CONFIG_FILE" >&2
    exit 1
fi
# The file is part of the trusted deployment bundle and contains shell-style
# NAME=value assignments so an administrator can edit it without extra tools.
# shellcheck source=/dev/null
source "$CONFIG_FILE"

CLIENT_BIND_ADDRESS=${CLIENT_BIND_ADDRESS:-0.0.0.0}
CLIENT_HTTPS_PORT=${CLIENT_HTTPS_PORT:-3000}
PAYARA_BIND_ADDRESS=${PAYARA_BIND_ADDRESS:-0.0.0.0}
PAYARA_PORT_BASE=${PAYARA_PORT_BASE:-8100}
PAYARA_ADMIN_PORT=${PAYARA_ADMIN_PORT:-$((PAYARA_PORT_BASE + 48))}
PAYARA_HTTP_PORT=${PAYARA_HTTP_PORT:-$((PAYARA_PORT_BASE + 80))}
PAYARA_HTTPS_PORT=${PAYARA_HTTPS_PORT:-$((PAYARA_PORT_BASE + 81))}
WILDFLY_BIND_ADDRESS=${WILDFLY_BIND_ADDRESS:-0.0.0.0}
WILDFLY_HTTP_PORT=${WILDFLY_HTTP_PORT:-8080}
WILDFLY_HTTPS_PORT=${WILDFLY_HTTPS_PORT:-8443}
WILDFLY_MANAGEMENT_PORT=${WILDFLY_MANAGEMENT_PORT:-9990}
TLS_STORE_PASSWORD=${TLS_STORE_PASSWORD:-changeit}
STARTUP_TIMEOUT_SECONDS=${STARTUP_TIMEOUT_SECONDS:-90}
SHUTDOWN_TIMEOUT_SECONDS=${SHUTDOWN_TIMEOUT_SECONDS:-30}
ACTIVE_PROCESSOR_COUNT=${ACTIVE_PROCESSOR_COUNT:-2}
JAVA_THREAD_STACK_SIZE=${JAVA_THREAD_STACK_SIZE:-256k}
PAYARA_MAX_HEAP=${PAYARA_MAX_HEAP:-320m}
WILDFLY_MAX_HEAP=${WILDFLY_MAX_HEAP:-320m}
CLIENT_MAX_HEAP=${CLIENT_MAX_HEAP:-96m}
CLIENT_WORKER_THREADS=${CLIENT_WORKER_THREADS:-4}

PAYARA_HOME=$BUNDLE_ROOT/runtime/payara
WILDFLY_HOME=$BUNDLE_ROOT/runtime/wildfly
PAYARA_DOMAINS_DIR=$BUNDLE_ROOT/state/payara/domains
PAYARA_DOMAIN_NAME=soa-lab
PAYARA_DOMAIN_DIR=$PAYARA_DOMAINS_DIR/$PAYARA_DOMAIN_NAME
PAYARA_ASADMIN=$PAYARA_HOME/bin/asadmin
WILDFLY_CLI=$WILDFLY_HOME/bin/jboss-cli.sh
CLIENT_JAR=$BUNDLE_ROOT/deployments/web-client.jar
TRUSTSTORE=$BUNDLE_ROOT/config/tls/truststore.p12
SERVER_KEYSTORE=$BUNDLE_ROOT/config/tls/server.p12
SERVER_CERTIFICATE=$BUNDLE_ROOT/config/tls/server.crt
PORTABLE_COMMON_JVM_OPTIONS="-XX:ActiveProcessorCount=$ACTIVE_PROCESSOR_COUNT -Xss$JAVA_THREAD_STACK_SIZE -XX:+UseSerialGC"
PAYARA_ASADMIN_JVM_OPTIONS="-Xms16m -Xmx96m $PORTABLE_COMMON_JVM_OPTIONS"
WILDFLY_JVM_SIZING="-Xms64m -Xmx$WILDFLY_MAX_HEAP $PORTABLE_COMMON_JVM_OPTIONS -Dorg.jboss.as.server-service.core.threads=2 -Dorg.jboss.as.server-service.max.threads=80"
CLIENT_JVM_OPTIONS="-Xms16m -Xmx$CLIENT_MAX_HEAP $PORTABLE_COMMON_JVM_OPTIONS"

log() {
    printf '[soa-lab] %s\n' "$*"
}

die() {
    printf '[soa-lab] ERROR: %s\n' "$*" >&2
    exit 1
}

require_file() {
    [[ -f "$1" ]] || die "Required file is missing: $1"
}

require_executable() {
    [[ -x "$1" ]] || die "Required executable is missing: $1"
}

resolve_java() {
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        JAVA_BIN=$JAVA_HOME/bin/java
        KEYTOOL_BIN=$JAVA_HOME/bin/keytool
    else
        JAVA_BIN=$(command -v java || true)
        KEYTOOL_BIN=$(command -v keytool || true)
    fi
    [[ -n "${JAVA_BIN:-}" && -x "$JAVA_BIN" ]] || die 'Java is not available. JDK 17 or newer is required.'
    [[ -n "${KEYTOOL_BIN:-}" && -x "$KEYTOOL_BIN" ]] || die 'keytool is not available. A full JDK is required.'

    local version major
    version=$($JAVA_BIN -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')
    major=${version%%.*}
    if [[ "$major" == 1 ]]; then
        major=${version#1.}
        major=${major%%.*}
    fi
    [[ "$major" =~ ^[0-9]+$ && "$major" -ge 17 ]] || die "JDK 17 or newer is required; found: $version"
}

run_asadmin() {
    env AS_EXTRA_JAVA_OPTS="${AS_EXTRA_JAVA_OPTS:-} $PAYARA_ASADMIN_JVM_OPTIONS" \
        "$PAYARA_ASADMIN" "$@"
}

pid_is_running() {
    local file=$1 pid
    [[ -r "$file" ]] || return 1
    pid=$(<"$file")
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$pid" 2>/dev/null
}

stop_pid_file() {
    local name=$1 file=$2 pid remaining
    if ! pid_is_running "$file"; then
        rm -f -- "$file"
        log "$name is not running"
        return 0
    fi
    pid=$(<"$file")
    kill -TERM "$pid" 2>/dev/null || true
    remaining=$SHUTDOWN_TIMEOUT_SECONDS
    while kill -0 "$pid" 2>/dev/null && (( remaining > 0 )); do
        sleep 1
        ((remaining--)) || true
    done
    if kill -0 "$pid" 2>/dev/null; then
        log "$name did not stop gracefully; terminating PID $pid"
        kill -KILL "$pid" 2>/dev/null || true
    fi
    rm -f -- "$file"
}

payara_is_running() {
    [[ -d "$PAYARA_DOMAIN_DIR" ]] || return 1
    run_asadmin list-domains --domaindir "$PAYARA_DOMAINS_DIR" 2>/dev/null \
        | grep -Eq "^${PAYARA_DOMAIN_NAME}[[:space:]]+running"
}

https_check() {
    local url=$1 expected=$2 timeout=${3:-$STARTUP_TIMEOUT_SECONDS}
    "$JAVA_BIN" $CLIENT_JVM_OPTIONS -jar "$CLIENT_JAR" check "$url" "$TRUSTSTORE" \
        "$TLS_STORE_PASSWORD" "$timeout" "$expected"
}

wait_for_log_message() {
    local file=$1 start_line=$2 message=$3 timeout=${4:-$STARTUP_TIMEOUT_SECONDS}
    local remaining=$timeout
    while (( remaining > 0 )); do
        if [[ -r "$file" ]] \
            && sed -n "${start_line},\$p" "$file" | grep -Fq -- "$message"; then
            return 0
        fi
        sleep 1
        ((remaining--)) || true
    done
    die "Timed out waiting for '$message' in $file"
}

validate_bundle() {
    resolve_java
    require_executable "$PAYARA_ASADMIN"
    require_executable "$WILDFLY_HOME/bin/standalone.sh"
    require_executable "$WILDFLY_CLI"
    require_file "$BUNDLE_ROOT/deployments/study-groups.war"
    require_file "$BUNDLE_ROOT/deployments/isu.war"
    require_file "$CLIENT_JAR"
    require_file "$SERVER_KEYSTORE"
    require_file "$SERVER_CERTIFICATE"
    require_file "$TRUSTSTORE"
    [[ "$TLS_STORE_PASSWORD" == changeit ]] || die 'Portable Payara setup currently requires TLS_STORE_PASSWORD=changeit.'
    [[ "$ACTIVE_PROCESSOR_COUNT" =~ ^[1-9][0-9]*$ ]] || die 'ACTIVE_PROCESSOR_COUNT must be a positive integer.'
    [[ "$JAVA_THREAD_STACK_SIZE" =~ ^[1-9][0-9]*[kKmM]$ ]] || die 'JAVA_THREAD_STACK_SIZE must look like 256k or 1m.'
    [[ "$PAYARA_MAX_HEAP" =~ ^[1-9][0-9]*[mMgG]$ ]] || die 'PAYARA_MAX_HEAP must look like 320m or 1g.'
    [[ "$WILDFLY_MAX_HEAP" =~ ^[1-9][0-9]*[mMgG]$ ]] || die 'WILDFLY_MAX_HEAP must look like 320m or 1g.'
    [[ "$CLIENT_MAX_HEAP" =~ ^[1-9][0-9]*[mMgG]$ ]] || die 'CLIENT_MAX_HEAP must look like 96m or 1g.'
    [[ "$CLIENT_WORKER_THREADS" =~ ^[1-9][0-9]*$ ]] || die 'CLIENT_WORKER_THREADS must be a positive integer.'
}
