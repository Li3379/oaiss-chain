#!/usr/bin/env bash
# Safe production compose wrapper.
# Clears common local/dev process env vars so an explicit production env file
# is the only source of runtime values for docker-compose.prod.yml.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

ENV_FILE=".env.prod.example"
COMPOSE_FILE="docker-compose.prod.yml"
COMPOSE_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --env-file" >&2
                exit 1
            fi
            ENV_FILE="$2"
            shift 2
            ;;
        --compose-file)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --compose-file" >&2
                exit 1
            fi
            COMPOSE_FILE="$2"
            shift 2
            ;;
        --)
            shift
            COMPOSE_ARGS+=("$@")
            break
            ;;
        *)
            COMPOSE_ARGS+=("$1")
            shift
            ;;
    esac
done

if [[ ! "$ENV_FILE" = /* ]]; then
    ENV_FILE="$PROJECT_ROOT/$ENV_FILE"
fi

if [[ ! "$COMPOSE_FILE" = /* ]]; then
    if [[ -f "$PROJECT_ROOT/$COMPOSE_FILE" ]]; then
        COMPOSE_FILE="$PROJECT_ROOT/$COMPOSE_FILE"
    elif [[ -f "$PROJECT_ROOT/deploy/compose/$COMPOSE_FILE" ]]; then
        COMPOSE_FILE="$PROJECT_ROOT/deploy/compose/$COMPOSE_FILE"
    else
        COMPOSE_FILE="$PROJECT_ROOT/$COMPOSE_FILE"
    fi
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Env file not found: $ENV_FILE" >&2
    exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
    echo "Compose file not found: $COMPOSE_FILE" >&2
    exit 1
fi

vars_to_clear=(
    SPRING_PROFILES_ACTIVE
    LOG_LEVEL
    APP_LOG_LEVEL
    CORS_ALLOWED_ORIGINS
    DB_URL
    DB_HOST
    DB_PORT
    DB_NAME
    DB_USERNAME
    DB_PASSWORD
    DB_POOL_MIN_IDLE
    DB_POOL_MAX_SIZE
    DB_POOL_CONNECTION_TIMEOUT_MS
    DB_POOL_LEAK_DETECTION_MS
    REDIS_HOST
    REDIS_PORT
    REDIS_DATABASE
    REDIS_PASSWORD
    JWT_SECRET
    JWT_EXPIRATION_MS
    JWT_REFRESH_EXPIRATION_MS
    RSA_KEK
    MINIO_ENDPOINT
    MINIO_ACCESS_KEY
    MINIO_SECRET_KEY
    MINIO_BUCKET
    MINIO_PRESIGNED_URL_EXPIRY_SECONDS
    ML_SERVICE_URL
    ML_SERVICE_SECRET
    ML_SERVICE_CONNECT_TIMEOUT
    ML_SERVICE_READ_TIMEOUT
    REQUIRE_OPS_SECRETS
    GRAFANA_ADMIN_PASSWORD
    FABRIC_ENABLED
    FABRIC_MSP_ID
    FABRIC_CHANNEL_NAME
    FABRIC_CHAINCODE_NAME
    FABRIC_PEER_ENDPOINT
    FABRIC_TLS_ENABLED
    FABRIC_PEER_TLS_CERT_PATH
    FABRIC_CERT_PATH
    FABRIC_KEY_PATH
    FABRIC_CA_ENABLED
    FABRIC_CA_ENDPOINT
    FABRIC_CA_ADMIN_NAME
    FABRIC_CA_ADMIN_PASSWORD
)

for name in "${vars_to_clear[@]}"; do
    unset "$name" || true
done

export COMPOSE_DISABLE_ENV_FILE=1

# WSL docker detection
_docker_cmd="docker"
if grep -qi microsoft /proc/version 2>/dev/null && command -v docker.exe >/dev/null 2>&1; then
    _docker_cmd="docker.exe"
elif command -v docker.exe >/dev/null 2>&1 && ! command -v docker >/dev/null 2>&1; then
    _docker_cmd="docker.exe"
fi

cd "$PROJECT_ROOT"

# Convert paths for docker.exe if running in WSL
if [[ "$_docker_cmd" == "docker.exe" && "$ENV_FILE" == /mnt/* ]]; then
    ENV_FILE=$(wslpath -w "$ENV_FILE" 2>/dev/null || echo "$ENV_FILE")
fi
if [[ "$_docker_cmd" == "docker.exe" && "$COMPOSE_FILE" == /mnt/* ]]; then
    COMPOSE_FILE=$(wslpath -w "$COMPOSE_FILE" 2>/dev/null || echo "$COMPOSE_FILE")
fi

exec "$_docker_cmd" compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "${COMPOSE_ARGS[@]}"
