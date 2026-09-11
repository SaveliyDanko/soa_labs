#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

validate_bundle
mkdir -p "$BUNDLE_ROOT/logs" "$BUNDLE_ROOT/run"
if [[ ! -f "$BUNDLE_ROOT/run/configured" ]]; then
    "$SCRIPT_DIR/configure.sh"
fi

payara_log=$PAYARA_DOMAIN_DIR/logs/server.log
if payara_is_running; then
    log 'Payara is already running'
else
    log 'Starting Payara'
    if [[ -r "$payara_log" ]]; then
        payara_log_start=$(( $(wc -l < "$payara_log") + 1 ))
    else
        payara_log_start=1
    fi
    run_asadmin start-domain --domaindir "$PAYARA_DOMAINS_DIR" "$PAYARA_DOMAIN_NAME"
    wait_for_log_message "$payara_log" "$payara_log_start" \
        'JMXStartupService has started JMXConnector'
fi

log 'Deploying the Study Groups service to Payara'
run_asadmin --port "$PAYARA_ADMIN_PORT" deploy --force=true \
    --name ROOT --contextroot / "$BUNDLE_ROOT/deployments/study-groups.war"
https_check "https://127.0.0.1:$PAYARA_HTTPS_PORT/api/study-groups?page=0&size=1" 200

if pid_is_running "$BUNDLE_ROOT/run/wildfly.pid"; then
    log 'WildFly is already running'
else
    log 'Deploying the ISU service and starting WildFly'
    cp -f -- "$BUNDLE_ROOT/deployments/isu.war" \
        "$WILDFLY_HOME/standalone/deployments/ROOT.war"
    rm -f -- "$WILDFLY_HOME/standalone/deployments/ROOT.war.failed" \
        "$WILDFLY_HOME/standalone/deployments/ROOT.war.deployed" \
        "$WILDFLY_HOME/standalone/deployments/ROOT.war.isdeploying" \
        "$WILDFLY_HOME/standalone/deployments/ROOT.war.pending" \
        "$WILDFLY_HOME/standalone/deployments/ROOT.war.undeployed"
    : > "$WILDFLY_HOME/standalone/deployments/ROOT.war.dodeploy"
    nohup env \
        MALLOC_ARENA_MAX=2 \
        JBOSS_JAVA_SIZING="$WILDFLY_JVM_SIZING" \
        STUDY_GROUPS_BASE_URL="https://127.0.0.1:$PAYARA_HTTPS_PORT" \
        STUDY_GROUPS_TRUSTSTORE="$TRUSTSTORE" \
        STUDY_GROUPS_TRUSTSTORE_PASSWORD="$TLS_STORE_PASSWORD" \
        "$WILDFLY_HOME/bin/standalone.sh" \
        -b "$WILDFLY_BIND_ADDRESS" -bmanagement 127.0.0.1 \
        "-Djboss.http.port=$WILDFLY_HTTP_PORT" \
        "-Djboss.https.port=$WILDFLY_HTTPS_PORT" \
        "-Djboss.management.http.port=$WILDFLY_MANAGEMENT_PORT" \
        >> "$BUNDLE_ROOT/logs/wildfly.log" 2>&1 &
    echo $! > "$BUNDLE_ROOT/run/wildfly.pid"
fi
https_check "https://127.0.0.1:$WILDFLY_HTTPS_PORT/isu/group/0/expel-all" 405

if pid_is_running "$BUNDLE_ROOT/run/web-client.pid"; then
    log 'Web client is already running'
else
    log 'Starting the Java HTTPS web client'
    nohup env \
        CLIENT_BIND_ADDRESS="$CLIENT_BIND_ADDRESS" \
        CLIENT_HTTPS_PORT="$CLIENT_HTTPS_PORT" \
        SERVER_KEYSTORE="$SERVER_KEYSTORE" \
        SERVER_TRUSTSTORE="$TRUSTSTORE" \
        TLS_STORE_PASSWORD="$TLS_STORE_PASSWORD" \
        CLIENT_WORKER_THREADS="$CLIENT_WORKER_THREADS" \
        STUDY_GROUPS_BASE_URL="https://127.0.0.1:$PAYARA_HTTPS_PORT" \
        ISU_BASE_URL="https://127.0.0.1:$WILDFLY_HTTPS_PORT" \
        "$JAVA_BIN" $CLIENT_JVM_OPTIONS -jar "$CLIENT_JAR" \
        >> "$BUNDLE_ROOT/logs/web-client.log" 2>&1 &
    echo $! > "$BUNDLE_ROOT/run/web-client.pid"
fi
https_check "https://127.0.0.1:$CLIENT_HTTPS_PORT/health" 200

log 'All services are running'
log "Client:       https://localhost:$CLIENT_HTTPS_PORT/"
log "Study Groups: https://localhost:$PAYARA_HTTPS_PORT/api/study-groups"
log "ISU:          https://localhost:$WILDFLY_HTTPS_PORT/isu"
