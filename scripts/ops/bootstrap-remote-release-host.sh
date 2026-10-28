#!/usr/bin/env bash
# Prepare a remote Linux host for OAISS CHAIN release deployments.
# Creates the directory structure expected by deploy-release.yml and
# docker-compose.release.yml so staging/production can mount logs and Fabric secrets.

set -euo pipefail

TARGET_DIR="/opt/oaiss-chain"
FABRIC_SECRETS_DIR=""
DEPLOY_USER=""

usage() {
    cat <<'EOF'
Usage:
  ./scripts/bootstrap-remote-release-host.sh [options]

Options:
  --target-dir <path>           Remote deployment root. Default: /opt/oaiss-chain
  --fabric-secrets-dir <path>   Fabric secrets directory. Default: <target-dir>/secrets/fabric
  --deploy-user <user>          User that should own the deployment directories
  -h, --help                    Show this help message

Example:
  sudo ./scripts/bootstrap-remote-release-host.sh \
    --target-dir /opt/oaiss-chain-staging \
    --deploy-user deploy
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target-dir)
            TARGET_DIR="$2"
            shift 2
            ;;
        --fabric-secrets-dir)
            FABRIC_SECRETS_DIR="$2"
            shift 2
            ;;
        --deploy-user)
            DEPLOY_USER="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
done

if [[ -z "$FABRIC_SECRETS_DIR" ]]; then
    FABRIC_SECRETS_DIR="$TARGET_DIR/secrets/fabric"
fi

LOG_ROOT="$TARGET_DIR/runtime-logs"
BACKEND_LOG_DIR="$LOG_ROOT/backend"
FRONTEND_LOG_DIR="$LOG_ROOT/frontend"
ML_LOG_DIR="$LOG_ROOT/ml-service"
BACKUP_DIR="$TARGET_DIR/backups"
SCRIPT_DIR="$TARGET_DIR/scripts"

echo "[INFO] Preparing OAISS CHAIN release host"
echo "[INFO] Target dir: $TARGET_DIR"
echo "[INFO] Fabric secrets dir: $FABRIC_SECRETS_DIR"

mkdir -p \
    "$TARGET_DIR" \
    "$BACKUP_DIR" \
    "$SCRIPT_DIR" \
    "$BACKEND_LOG_DIR" \
    "$FRONTEND_LOG_DIR" \
    "$ML_LOG_DIR" \
    "$FABRIC_SECRETS_DIR"

chmod 750 "$TARGET_DIR" "$BACKUP_DIR" "$SCRIPT_DIR" "$LOG_ROOT" \
    "$BACKEND_LOG_DIR" "$FRONTEND_LOG_DIR" "$ML_LOG_DIR"
chmod 700 "$FABRIC_SECRETS_DIR"

if [[ -n "$DEPLOY_USER" ]]; then
    chown -R "$DEPLOY_USER":"$DEPLOY_USER" "$TARGET_DIR"
fi

echo "[INFO] Directory layout ready"
printf '%s\n' \
    "$TARGET_DIR" \
    "$BACKUP_DIR" \
    "$SCRIPT_DIR" \
    "$LOG_ROOT" \
    "$BACKEND_LOG_DIR" \
    "$FRONTEND_LOG_DIR" \
    "$ML_LOG_DIR" \
    "$TARGET_DIR/secrets" \
    "$FABRIC_SECRETS_DIR"

echo "[INFO] Checking Docker runtime"
if command -v docker >/dev/null 2>&1; then
    docker --version
    docker compose version
elif command -v docker.exe >/dev/null 2>&1; then
    docker.exe --version
    docker.exe compose version
else
    echo "[WARN] Docker not found in PATH"
fi

cat <<EOF

[NEXT]
1. Put Fabric files into:
   $FABRIC_SECRETS_DIR
   - peer-tls-ca.crt
   - user-cert.pem
   - user-key.pem
2. Fill GitHub Environment secret DEPLOY_TARGET_DIR with:
   $TARGET_DIR
3. Fill DEPLOY_ENV_FILE with:
   BACKEND_LOG_DIR=./runtime-logs/backend
   FRONTEND_LOG_DIR=./runtime-logs/frontend
   ML_LOG_DIR=./runtime-logs/ml-service
   FABRIC_SECRETS_DIR=./secrets/fabric
   FABRIC_SECRETS_MOUNT_PATH=/run/secrets/fabric
4. Run deploy-release.yml against this host after images are published.
EOF
