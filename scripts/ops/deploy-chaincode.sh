#!/bin/bash
# Deploy carbon-chaincode to Fabric network
# Prerequisites: Fabric network running (docker compose -f docker-compose.fabric.yml up)
# Crypto materials generated (./scripts/generate-fabric-crypto.sh)
#
# Usage: ./scripts/deploy-chaincode.sh [VERSION] [SEQUENCE]
# Default VERSION: 1.0, SEQUENCE: 1

set -e

CC_NAME="carbon-chaincode"
CC_VERSION="${1:-1.0}"
CC_SEQUENCE="${2:-1}"
CC_PATH="/opt/gopath/src/github.com/oaiss/chain/chaincode"
CHANNEL_NAME="mychannel"
ORDERER_ADDRESS="orderer.example.com:7050"
ORDERER_TLS_CA="/var/hyperledger/orderer/tls/ca.crt"
PEER_ADDRESS="peer0.org1.example.com:7051"
PEER_TLS_CA="/var/hyperledger/peer/tls/ca.crt"
CLI_CONTAINER="fabric-cli"
ORG_ADMIN_MSP="/var/hyperledger/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp"

echo "=== Deploying ${CC_NAME} v${CC_VERSION} (sequence: ${CC_SEQUENCE}) ==="

if grep -qi microsoft /proc/version 2>/dev/null && command -v docker.exe >/dev/null 2>&1; then
  DOCKER_BIN="docker.exe"
elif command -v docker >/dev/null 2>&1; then
  DOCKER_BIN="docker"
elif command -v docker.exe >/dev/null 2>&1; then
  DOCKER_BIN="docker.exe"
else
  echo "ERROR: docker or docker.exe not found in PATH"
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
    "$@"
}

# Step 1: Package chaincode
echo "1. Packaging chaincode..."
fabric_exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=${ORG_ADMIN_MSP} \
  -e CORE_PEER_ADDRESS=${PEER_ADDRESS} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${PEER_TLS_CA} \
  ${CLI_CONTAINER} \
  peer lifecycle chaincode package ${CC_NAME}.tar.gz \
  --path ${CC_PATH} \
  --lang golang \
  --label ${CC_NAME}_${CC_VERSION}

# Step 2: Install chaincode on peer
echo "2. Installing chaincode on peer0..."
fabric_exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=${ORG_ADMIN_MSP} \
  -e CORE_PEER_ADDRESS=${PEER_ADDRESS} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${PEER_TLS_CA} \
  ${CLI_CONTAINER} \
  peer lifecycle chaincode install ${CC_NAME}.tar.gz

# Step 3: Get package ID
echo "3. Querying installed chaincode..."
QUERY_INSTALLED_OUTPUT=$(fabric_exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=${ORG_ADMIN_MSP} \
  -e CORE_PEER_ADDRESS=${PEER_ADDRESS} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${PEER_TLS_CA} \
  ${CLI_CONTAINER} \
  peer lifecycle chaincode queryinstalled)

PACKAGE_ID=$(printf '%s\n' "$QUERY_INSTALLED_OUTPUT" | grep -o "${CC_NAME}_${CC_VERSION}:[a-f0-9]*" | head -1)
echo "Package ID: ${PACKAGE_ID}"

if [ -z "${PACKAGE_ID}" ]; then
  echo "ERROR: Could not find package ID. Check queryinstalled output."
  printf '%s\n' "$QUERY_INSTALLED_OUTPUT"
  exit 1
fi

# Step 4: Approve chaincode for org
echo "4. Approving chaincode for Org1..."
fabric_exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=${ORG_ADMIN_MSP} \
  -e CORE_PEER_ADDRESS=${PEER_ADDRESS} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${PEER_TLS_CA} \
  ${CLI_CONTAINER} \
  peer lifecycle chaincode approveformyorg \
  --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} \
  --version ${CC_VERSION} \
  --package-id ${PACKAGE_ID} \
  --sequence ${CC_SEQUENCE} \
  --tls \
  --cafile ${ORDERER_TLS_CA} \
  --orderer ${ORDERER_ADDRESS}

# Step 5: Commit chaincode to channel
echo "5. Committing chaincode to channel..."
fabric_exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=${ORG_ADMIN_MSP} \
  -e CORE_PEER_ADDRESS=${PEER_ADDRESS} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${PEER_TLS_CA} \
  ${CLI_CONTAINER} \
  peer lifecycle chaincode commit \
  --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} \
  --version ${CC_VERSION} \
  --sequence ${CC_SEQUENCE} \
  --tls \
  --cafile ${ORDERER_TLS_CA} \
  --orderer ${ORDERER_ADDRESS} \
  --peerAddresses ${PEER_ADDRESS} \
  --tlsRootCertFiles ${PEER_TLS_CA}

# Step 6: Verify deployment
echo "6. Verifying deployment..."
fabric_exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=${ORG_ADMIN_MSP} \
  -e CORE_PEER_ADDRESS=${PEER_ADDRESS} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${PEER_TLS_CA} \
  ${CLI_CONTAINER} \
  peer lifecycle chaincode querycommitted \
  --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} \
  --tls \
  --cafile ${ORDERER_TLS_CA}

echo ""
echo "=== ${CC_NAME} deployed successfully ==="
echo "Chaincode: ${CC_NAME}_${CC_VERSION}"
echo "Channel:   ${CHANNEL_NAME}"
echo "Sequence:  ${CC_SEQUENCE}"
echo ""
echo "Test invocation:"
echo "docker exec ${CLI_CONTAINER} peer chaincode invoke -C ${CHANNEL_NAME} -n ${CC_NAME} -c '{\"function\":\"CreateCarbonReport\",\"Args\":[\"1\",\"test\"]}' --tls --cafile ${ORDERER_TLS_CA} --orderer ${ORDERER_ADDRESS}"
