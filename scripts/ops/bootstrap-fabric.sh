#!/usr/bin/env bash
# Bootstrap local Fabric channel and chaincode for OAISS CHAIN.
# This is idempotent and intended for local,fabric startup mode.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

FABRIC_DIR="$PROJECT_ROOT/fabric-config"
if [ ! -d "$FABRIC_DIR" ] && [ -d "$PROJECT_ROOT/deploy/fabric" ]; then
    FABRIC_DIR="$PROJECT_ROOT/deploy/fabric"
fi

CHANNEL_NAME="${FABRIC_CHANNEL_NAME:-mychannel}"
CHAINCODE_NAME="${FABRIC_CHAINCODE_NAME:-carbon-chaincode}"
CHAINCODE_VERSION="${FABRIC_CHAINCODE_VERSION:-1.0}"
CHAINCODE_SEQUENCE="${FABRIC_CHAINCODE_SEQUENCE:-1}"

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
    if fabric_admin_bash "peer lifecycle chaincode querycommitted --channelID ${CHANNEL_NAME} --name ${CHAINCODE_NAME} | grep -q 'Version: ${CHAINCODE_VERSION}'"; then
        log "Chaincode ${CHAINCODE_NAME} already committed on ${CHANNEL_NAME}"
        return 0
    fi

    log "Deploying ${CHAINCODE_NAME} to ${CHANNEL_NAME}"
    bash "$PROJECT_ROOT/scripts/deploy-chaincode.sh" "$CHAINCODE_VERSION" "$CHAINCODE_SEQUENCE"
}

ensure_prereqs
ensure_channel
ensure_chaincode
log "Fabric bootstrap complete"
