#!/bin/bash
# Generate Fabric crypto materials using test-network
# Prerequisites: Docker, fabric-samples cloned
#
# Usage: ./scripts/generate-fabric-crypto.sh [FABRIC_SAMPLES_PATH]
# Default FABRIC_SAMPLES_PATH: ~/fabric-samples

set -e

FABRIC_SAMPLES_PATH="${1:-$HOME/fabric-samples}"
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

echo "=== Generating Fabric Crypto Materials ==="
echo "Fabric samples path: $FABRIC_SAMPLES_PATH"
echo "Project root: $PROJECT_ROOT"

# Check fabric-samples exists
if [ ! -d "$FABRIC_SAMPLES_PATH/test-network" ]; then
    echo "ERROR: fabric-samples/test-network not found at $FABRIC_SAMPLES_PATH"
    echo "Clone it: cd ~ && curl -sSLO https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh && bash ./install-fabric.sh docker samples bin"
    exit 1
fi

# Start test-network and create channel
cd "$FABRIC_SAMPLES_PATH/test-network"
echo "Starting Fabric test-network..."
./network.sh up createChannel -c mychannel -ca

# Copy crypto materials to project
echo "Copying crypto materials to project..."
FABRIC_DIR="$PROJECT_ROOT/fabric-config"
if [ ! -d "$FABRIC_DIR" ] && [ -d "$PROJECT_ROOT/deploy/fabric" ]; then
    FABRIC_DIR="$PROJECT_ROOT/deploy/fabric"
fi
CRYPTO_DIR="$FABRIC_DIR/crypto"
rm -rf "$CRYPTO_DIR/*"
cp -r organizations/ "$CRYPTO_DIR/"

# Extract key certificates to classpath
RESOURCES_DIR="$PROJECT_ROOT/oaiss-chain-backend/src/main/resources/fabric/crypto"
mkdir -p "$RESOURCES_DIR"

# Peer TLS CA certificate
PEER_TLS_CERT="$CRYPTO_DIR/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt"
if [ -f "$PEER_TLS_CERT" ]; then
    cp "$PEER_TLS_CERT" "$RESOURCES_DIR/peer-tls-ca.crt"
    echo "Copied peer TLS CA certificate"
else
    echo "WARNING: Peer TLS CA certificate not found at $PEER_TLS_CERT"
fi

# User certificate
USER_CERT="$CRYPTO_DIR/organizations/peerOrganizations/org1.example.com/users/User1@org1.example.com/msp/signcerts/cert.pem"
if [ -f "$USER_CERT" ]; then
    cp "$USER_CERT" "$RESOURCES_DIR/user-cert.pem"
    echo "Copied user certificate"
else
    echo "WARNING: User certificate not found, searching..."
    find "$CRYPTO_DIR/organizations/peerOrganizations/org1.example.com/users/User1@org1.example.com/msp/signcerts/" -name "*.pem" -exec cp {} "$RESOURCES_DIR/user-cert.pem" \;
fi

# User private key
USER_KEY_DIR="$CRYPTO_DIR/organizations/peerOrganizations/org1.example.com/users/User1@org1.example.com/msp/keystore/"
if [ -d "$USER_KEY_DIR" ]; then
    find "$USER_KEY_DIR" -name "*_sk" -exec cp {} "$RESOURCES_DIR/user-key.pem" \;
    echo "Copied user private key"
else
    echo "WARNING: User private key directory not found"
fi

echo ""
echo "=== Crypto Materials Generated ==="
echo "Full crypto: $CRYPTO_DIR/"
echo "Classpath:   $RESOURCES_DIR/"
ls -la "$RESOURCES_DIR/"

echo ""
echo "To deploy chaincode, run: ./scripts/deploy-chaincode.sh"
