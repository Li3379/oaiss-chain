#!/usr/bin/env bash
# Bootstrap local Fabric channel and chaincode for OAISS CHAIN.
# This is idempotent and intended for local,fabric startup mode.
#
# Chaincode version policy (synced 2026-11-02):
#   - FABRIC_CHAINCODE_VERSION is the desired version LABEL (default 1.2,
#     matching the committed network state and the V2 snapshot chaincode
#     source). It is a label only; the package is always built from the
#     live oaiss-chain-chaincode source mounted into fabric-cli.
#   - Bump it when chaincode source changes so bootstrap re-deploys.
#   - The sequence is auto-computed: fresh network -> 1; otherwise
#     committed_sequence + 1. Never pass a stale sequence.

set -euo pipefail

# Git Bash / MSYS on Windows rewrites leading-slash arguments of native
# executables (docker.exe) into "D:/Program File/Git/..." — which corrupts
# /opt/gopath and /var/hyperledger paths passed to `docker exec`. Disable it;
# the variable is a no-op on real Linux.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load .env so FABRIC_CHAINCODE_VERSION etc. from .env take effect.
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$PROJECT_ROOT/.env"
    set +a
fi

FABRIC_DIR="$PROJECT_ROOT/fabric-config"
if [ ! -d "$FABRIC_DIR" ] && [ -d "$PROJECT_ROOT/deploy/fabric" ] 2>/dev/null; then
    FABRIC_DIR="$PROJECT_ROOT/deploy/fabric"
fi

CHANNEL_NAME="${FABRIC_CHANNEL_NAME:-mychannel}"
CHAINCODE_NAME="${FABRIC_CHAINCODE_NAME:-carbon-chaincode}"
# Desired chaincode version label; sequence is computed from the network.
CHAINCODE_VERSION="${FABRIC_CHAINCODE_VERSION:-1.2}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[FABRIC]${NC} $*"; }
warn() { echo -e "${YELLOW}[FABRIC]${NC} $*"; }
err()  { echo -e "${RED}[FABRIC]${NC} $*"; }

if grep -qi microsoft /proc/version 2>/dev/null && command -v docker.exe >/dev/null 2>&1; then
    DOCKER_BIN="docker.exe"
elif command -v docker >/dev/null 2>&1; then
    DOCKER_BIN="docker"
elif command -v docker.exe >/dev/null 2>&1; then
    DOCKER_BIN="docker.exe"
else
    err "docker or docker.exe not found in PATH"
    exit 1
fi

fabric_exec() {
    "$DOCKER_BIN" exec \
        -e HTTP_PROXY= \
        -e HTTPS_PROXY= \
        -e http_proxy= \
        -e https_proxy= \
        -e ALL_PROXY= \
        -e all_proxy= \
        -e NO_PROXY=localhost,127.0.0.1,peer0.org1.example.com,orderer.example.com \
        -e no_proxy=localhost,127.0.0.1,peer0.org1.example.com,orderer.example.com \
        fabric-cli \
        "$@"
}

fabric_admin_bash() {
    fabric_exec bash -lc "
        export CORE_PEER_LOCALMSPID=Org1MSP && \
        export CORE_PEER_MSPCONFIGPATH=/var/hyperledger/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp && \
        export CORE_PEER_ADDRESS=peer0.org1.example.com:7051 && \
        export CORE_PEER_TLS_ENABLED=true && \
        export CORE_PEER_TLS_ROOTCERT_FILE=/var/hyperledger/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt && \
        $*"
}

# Parse "Version: X, Sequence: N, ..." from querycommitted output.
# Contract: echoes "<version> <sequence>" when both fields parse;
# echoes nothing otherwise. Pure function, covered by the smoke test.
parse_committed_line() {
    local line="$1"
    local version seq
    version="$(printf '%s' "$line" | sed -n 's/.*Version: \([^,]*\).*/\1/p' | head -n1)"
    seq="$(printf '%s' "$line" | sed -n 's/.*Sequence: \([0-9][0-9]*\).*/\1/p' | head -n1)"
    if [[ -n "$version" && -n "$seq" ]]; then
        printf '%s %s\n' "$version" "$seq"
    fi
}

# Indirection point: the smoke test overrides this to record deploy calls.
run_deploy() {
    bash "$PROJECT_ROOT/scripts/deploy-chaincode.sh" "$1" "$2"
}

ensure_prereqs() {
    local missing=0
    for path in \
        "$FABRIC_DIR/configtx.yaml" \
        "$FABRIC_DIR/crypto/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp" \
        "$FABRIC_DIR/crypto/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/tls/server.crt" \
        "$PROJECT_ROOT/oaiss-chain-chaincode/go.mod"
    do
        if [[ ! -e "$path" ]]; then
            err "Missing required Fabric asset: $path"
            missing=1
        fi
    done

    if [[ $missing -ne 0 ]]; then
        err "Fabric bootstrap prerequisites are incomplete."
        err "Run ./scripts/generate-fabric-crypto.sh first if crypto material is missing."
        exit 1
    fi
}

ensure_channel() {
    if fabric_admin_bash "peer channel list | grep -q '^${CHANNEL_NAME}$'"; then
        log "Channel ${CHANNEL_NAME} already joined on peer0"
        return 0
    fi

    log "Generating channel block for ${CHANNEL_NAME}"
    fabric_admin_bash "configtxgen -configPath /var/hyperledger/config -profile OAISSSingleOrgChannel -channelID ${CHANNEL_NAME} -outputBlock /tmp/${CHANNEL_NAME}.block"

    log "Joining orderer admin endpoint to ${CHANNEL_NAME}"
    fabric_admin_bash "osnadmin channel join \
        --channelID ${CHANNEL_NAME} \
        --config-block /tmp/${CHANNEL_NAME}.block \
        -o orderer.example.com:7053 \
        --ca-file /var/hyperledger/orderer/tls/ca.crt \
        --client-cert /var/hyperledger/orderer/tls/server.crt \
        --client-key /var/hyperledger/orderer/tls/server.key || true"

    log "Joining peer0 to ${CHANNEL_NAME}"
    fabric_admin_bash "peer channel join -b /tmp/${CHANNEL_NAME}.block"
}

ensure_chaincode() {
    local committed_line committed_version committed_seq next_seq
    committed_line="$(fabric_admin_bash "peer lifecycle chaincode querycommitted --channelID ${CHANNEL_NAME} --name ${CHAINCODE_NAME}" 2>/dev/null || true)"

    read -r committed_version committed_seq < <(parse_committed_line "$committed_line")

    if [[ -n "$committed_line" && -z "$committed_version" ]]; then
        err "已检测到链码定义但无法解析版本/序号，请人工检查: peer lifecycle chaincode querycommitted"
        exit 1
    fi

    if [[ -z "$committed_version" ]]; then
        log "Deploying ${CHAINCODE_NAME} v${CHAINCODE_VERSION} (fresh, sequence 1)"
        run_deploy "$CHAINCODE_VERSION" "1"
        return 0
    fi

    if [[ "$committed_version" == "$CHAINCODE_VERSION" ]]; then
        log "Chaincode ${CHAINCODE_NAME} v${committed_version} (sequence ${committed_seq}) already committed on ${CHANNEL_NAME} - skip"
        return 0
    else
        next_seq=$((committed_seq + 1))
        log "Upgrading ${CHAINCODE_NAME}: committed v${committed_version}/seq${committed_seq} != desired v${CHAINCODE_VERSION} -> deploy v${CHAINCODE_VERSION}/seq${next_seq}"
        run_deploy "$CHAINCODE_VERSION" "$next_seq"
    fi
}

main() {
    ensure_prereqs
    ensure_channel
    ensure_chaincode
    log "Fabric bootstrap complete"
}

# main-guard: sourcing this file (smoke test) must not execute the flow.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
