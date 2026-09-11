#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
BUNDLE_DIR=${BUNDLE_DIR:-$PROJECT_ROOT/dist/soa-lab-runtime}
DEPLOY_TARGET=${DEPLOY_TARGET:-}
REMOTE_PARENT=${REMOTE_PARENT:-.}
SSH_PORT=${SSH_PORT:-2222}

if [[ -z "$DEPLOY_TARGET" ]]; then
    printf 'Usage: DEPLOY_TARGET=<login>@helios.cs.ifmo.ru %s\n' "$0" >&2
    exit 1
fi
if [[ ! -x "$BUNDLE_DIR/bin/start.sh" ]]; then
    printf 'Portable bundle was not found at %s. Run prepare-portable-deployment.sh first.\n' "$BUNDLE_DIR" >&2
    exit 1
fi

printf 'Uploading the already unpacked runtime to %s:%s\n' "$DEPLOY_TARGET" "$REMOTE_PARENT"
scp -P "$SSH_PORT" -r "$BUNDLE_DIR" "$DEPLOY_TARGET:$REMOTE_PARENT"
printf 'Upload complete. On the server run: ~/soa-lab-runtime/bin/start.sh\n'
