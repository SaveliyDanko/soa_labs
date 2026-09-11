#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

validate_bundle
mkdir -p "$BUNDLE_ROOT/logs" "$BUNDLE_ROOT/run" "$PAYARA_DOMAINS_DIR"

if [[ -f "$BUNDLE_ROOT/run/configured" && "${1:-}" != --force ]]; then
    log 'Runtime is already configured. Use configure.sh --force after replacing certificates.'
    exit 0
fi
if payara_is_running || pid_is_running "$BUNDLE_ROOT/run/wildfly.pid"; then
    die 'Stop the running services before configuring the runtime.'
fi

if [[ ! -f "$PAYARA_DOMAIN_DIR/config/domain.xml" ]]; then
    log "Creating isolated Payara domain with port base $PAYARA_PORT_BASE"
    run_asadmin create-domain \
        --domaindir "$PAYARA_DOMAINS_DIR" \
        --portbase "$PAYARA_PORT_BASE" \
        --nopassword=true \
        "$PAYARA_DOMAIN_NAME"
fi

log 'Installing the TLS certificate into Payara'
cp -f -- "$SERVER_KEYSTORE" "$PAYARA_DOMAIN_DIR/config/keystore.p12"
"$KEYTOOL_BIN" -delete -alias soa-lab-server \
    -keystore "$PAYARA_DOMAIN_DIR/config/cacerts.p12" \
    -storetype PKCS12 -storepass "$TLS_STORE_PASSWORD" >/dev/null 2>&1 || true
"$KEYTOOL_BIN" -importcert -noprompt -alias soa-lab-server \
    -file "$SERVER_CERTIFICATE" \
    -keystore "$PAYARA_DOMAIN_DIR/config/cacerts.p12" \
    -storetype PKCS12 -storepass "$TLS_STORE_PASSWORD" >/dev/null

log 'Applying the HTTPS-only Payara listener configuration offline'
"$JAVA_BIN" $CLIENT_JVM_OPTIONS -jar "$CLIENT_JAR" configure-payara \
    "$PAYARA_DOMAIN_DIR/config/domain.xml" "$PAYARA_BIND_ADDRESS" \
    "$PAYARA_HTTPS_PORT" "$PAYARA_HTTP_PORT" "$ACTIVE_PROCESSOR_COUNT" \
    "$JAVA_THREAD_STACK_SIZE" "$PAYARA_MAX_HEAP"

log 'Applying the HTTPS-only WildFly listener configuration'
cp -f -- "$SERVER_KEYSTORE" "$WILDFLY_HOME/standalone/configuration/server.p12"
cp -f -- "$TRUSTSTORE" "$WILDFLY_HOME/standalone/configuration/truststore.p12"
wildfly_cli=$BUNDLE_ROOT/run/configure-wildfly.cli
sed "s/{TLS_STORE_PASSWORD}/$TLS_STORE_PASSWORD/g" \
    "$BUNDLE_ROOT/config/configure-wildfly.cli.template" > "$wildfly_cli"
env JAVA_OPTS="$WILDFLY_JVM_SIZING" "$WILDFLY_CLI" --file="$wildfly_cli"
rm -f -- "$wildfly_cli"

date -u +'%Y-%m-%dT%H:%M:%SZ' > "$BUNDLE_ROOT/run/configured"
log 'Runtime configuration completed'
