#!/usr/bin/env bash

set -Eeuo pipefail
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

mkdir -p "$BUNDLE_ROOT/logs"
touch "$BUNDLE_ROOT/logs/wildfly.log" "$BUNDLE_ROOT/logs/web-client.log"
payara_log=$PAYARA_DOMAIN_DIR/logs/server.log
if [[ -f "$payara_log" ]]; then
    exec tail -n 100 -F "$payara_log" "$BUNDLE_ROOT/logs/wildfly.log" "$BUNDLE_ROOT/logs/web-client.log"
fi
exec tail -n 100 -F "$BUNDLE_ROOT/logs/wildfly.log" "$BUNDLE_ROOT/logs/web-client.log"
