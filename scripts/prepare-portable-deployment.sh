#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PAYARA_SOURCE=${PAYARA_SOURCE:-}
WILDFLY_SOURCE=${WILDFLY_SOURCE:-}
OUTPUT_DIR=${OUTPUT_DIR:-$PROJECT_ROOT/dist/soa-lab-runtime}
SKIP_BUILD=${SKIP_BUILD:-0}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

[[ -n "$PAYARA_SOURCE" ]] || die 'Set PAYARA_SOURCE to an already unpacked Payara directory.'
[[ -n "$WILDFLY_SOURCE" ]] || die 'Set WILDFLY_SOURCE to an already unpacked WildFly directory.'
PAYARA_SOURCE=$(CDPATH= cd -- "$PAYARA_SOURCE" && pwd)
WILDFLY_SOURCE=$(CDPATH= cd -- "$WILDFLY_SOURCE" && pwd)
[[ -x "$PAYARA_SOURCE/bin/asadmin" ]] || die "Payara asadmin was not found under $PAYARA_SOURCE/bin"
[[ -x "$WILDFLY_SOURCE/bin/standalone.sh" ]] || die "WildFly standalone.sh was not found under $WILDFLY_SOURCE/bin"
[[ "${TLS_STORE_PASSWORD:-changeit}" == changeit ]] || die 'Portable Payara bundle currently requires TLS_STORE_PASSWORD=changeit.'

if [[ "$SKIP_BUILD" != 1 ]]; then
    command -v mvn >/dev/null || die 'Maven is required on the build machine.'
    printf 'Building WAR and JAR artifacts...\n'
    (cd "$PROJECT_ROOT" && mvn clean package)
fi

if [[ ! -f "$PROJECT_ROOT/docker/tls/server.p12" || ! -f "$PROJECT_ROOT/docker/tls/truststore.p12" ]]; then
    printf 'Generating the self-signed TLS certificate...\n'
    "$PROJECT_ROOT/docker/tls/generate-certs.sh"
fi

for artifact in \
    "$PROJECT_ROOT/study-groups-service/target/study-groups.war" \
    "$PROJECT_ROOT/isu-service/target/isu.war" \
    "$PROJECT_ROOT/web-client-server/target/web-client.jar"; do
    [[ -f "$artifact" ]] || die "Build artifact is missing: $artifact"
done

output_parent=$(dirname -- "$OUTPUT_DIR")
mkdir -p "$output_parent"
stage=$(mktemp -d "$output_parent/.soa-lab-runtime.XXXXXX")
cleanup() {
    [[ -d "$stage" ]] && rm -rf -- "$stage"
}
trap cleanup EXIT

mkdir -p "$stage/runtime/payara" "$stage/runtime/wildfly" \
    "$stage/deployments" "$stage/config/tls" "$stage/bin" \
    "$stage/logs" "$stage/run" "$stage/state/payara/domains"
printf 'Copying unpacked Payara...\n'
cp -a "$PAYARA_SOURCE"/. "$stage/runtime/payara"/
# The supplied distribution may have been started before. Its built-in domains
# are not used: configure.sh creates a clean relocatable domain under state/.
rm -rf -- "$stage/runtime/payara/glassfish/domains"
mkdir -p "$stage/runtime/payara/glassfish/domains"
printf 'Copying unpacked WildFly...\n'
cp -a "$WILDFLY_SOURCE"/. "$stage/runtime/wildfly"/
# A previously started WildFly installation contains machine-specific absolute
# paths and transient deployment state. The bundle must remain relocatable.
rm -rf -- "$stage/runtime/wildfly/standalone/data" \
    "$stage/runtime/wildfly/standalone/log" \
    "$stage/runtime/wildfly/standalone/tmp" \
    "$stage/runtime/wildfly/standalone/deployments" \
    "$stage/runtime/wildfly/standalone/configuration/standalone_xml_history"
rm -f -- "$stage/runtime/wildfly/standalone/configuration/logging.properties"
mkdir -p "$stage/runtime/wildfly/standalone/data" \
    "$stage/runtime/wildfly/standalone/log" \
    "$stage/runtime/wildfly/standalone/tmp" \
    "$stage/runtime/wildfly/standalone/deployments"
cp -f "$PROJECT_ROOT/study-groups-service/target/study-groups.war" "$stage/deployments/"
cp -f "$PROJECT_ROOT/isu-service/target/isu.war" "$stage/deployments/"
cp -f "$PROJECT_ROOT/web-client-server/target/web-client.jar" "$stage/deployments/"
cp -f "$PROJECT_ROOT/docker/tls/server.p12" "$stage/config/tls/"
cp -f "$PROJECT_ROOT/docker/tls/truststore.p12" "$stage/config/tls/"
cp -f "$PROJECT_ROOT/docker/tls/server.crt" "$stage/config/tls/"
cp -f "$PROJECT_ROOT/portable/config/runtime.env" "$stage/config/"
cp -f "$PROJECT_ROOT/portable/config/configure-wildfly.cli.template" "$stage/config/"
cp -f "$PROJECT_ROOT/portable/bin/"*.sh "$stage/bin/"
chmod 700 "$stage/bin/"*.sh
chmod 600 "$stage/config/tls/server.p12" "$stage/config/tls/truststore.p12"

if [[ -L "$OUTPUT_DIR" ]]; then
    die "Refusing to replace a symbolic link: $OUTPUT_DIR"
fi
if [[ -e "$OUTPUT_DIR" ]]; then
    backup="$OUTPUT_DIR.backup.$(date +%Y%m%d-%H%M%S)"
    printf 'Moving the previous bundle to %s\n' "$backup"
    mv -- "$OUTPUT_DIR" "$backup"
fi
mv -- "$stage" "$OUTPUT_DIR"
stage=
trap - EXIT

printf '\nPortable deployment is ready: %s\n' "$OUTPUT_DIR"
printf 'Edit config/runtime.env, copy the entire directory to the server, then run bin/start.sh.\n'
