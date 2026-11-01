#!/usr/bin/env bash
# OAISS CHAIN - 统一停止脚本
# 用法: ./scripts/stop-all.sh [选项]
#   --with-fabric    同时停止 Fabric 网络
#   --infra-only     仅停止基础设施
#   --help           显示本帮助并退出

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

WITH_FABRIC=false
INFRA_ONLY=false

for arg in "$@"; do
    case $arg in
        --with-fabric) WITH_FABRIC=true ;;
        --infra-only)  INFRA_ONLY=true ;;
        --help|-h)     grep '^#   --' "$0" | sed 's/^#   //'; exit 0 ;;
        *) echo "未知参数: $arg (使用 --help 查看用法)"; exit 1 ;;
    esac
done

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[STOP]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

# WSL docker detection
if grep -qi microsoft /proc/version 2>/dev/null && command -v docker.exe >/dev/null 2>&1; then
    DOCKER_BIN="docker.exe"
elif command -v docker >/dev/null 2>&1; then
    DOCKER_BIN="docker"
elif command -v docker.exe >/dev/null 2>&1; then
    DOCKER_BIN="docker.exe"
else
    warn "docker not found, skipping container cleanup"
    DOCKER_BIN=""
fi

ENV_ARGS=()
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    ENV_ARGS=(--env-file "$PROJECT_ROOT/.env")
fi

# 停止本地进程
log "停止本地进程..."
if [[ -x "$PROJECT_ROOT/scripts/stop-backend.sh" ]]; then
    "$PROJECT_ROOT/scripts/stop-backend.sh" >/dev/null 2>&1 || true
fi

for pid_file in /tmp/oaiss-ml.pid /tmp/oaiss-frontend.pid; do
    if [[ -f "$pid_file" ]]; then
        pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            log "停止 PID $pid..."
            kill "$pid" 2>/dev/null || true
        fi
        rm -f "$pid_file"
    fi
done

# 停止 Fabric（在销毁基础设施网络前停止）
if [[ "$WITH_FABRIC" == true ]] || ("$DOCKER_BIN" ps -a --format '{{.Names}}' 2>/dev/null | grep -qE "orderer\.example\.com|peer0\.org1\.example\.com"); then
    log "停止 Fabric 网络..."
    if [[ -n "$DOCKER_BIN" ]]; then "$DOCKER_BIN" compose "${ENV_ARGS[@]}" -f "$PROJECT_ROOT/deploy/compose/docker-compose.fabric.yml" down 2>/dev/null || true; fi
fi

# 停止基础设施
log "停止基础设施容器..."
if [[ -n "$DOCKER_BIN" ]]; then "$DOCKER_BIN" compose "${ENV_ARGS[@]}" -f "$PROJECT_ROOT/deploy/compose/docker-compose.infra.yml" down; fi

if [[ "$INFRA_ONLY" != true ]]; then
    # 停止全栈 compose（如果之前用 docker compose -f docker-compose.yml 启动）
    log "停止全栈容器（如有）..."
    if [[ -n "$DOCKER_BIN" ]]; then "$DOCKER_BIN" compose "${ENV_ARGS[@]}" -f "$PROJECT_ROOT/docker-compose.yml" down 2>/dev/null || true; fi
fi

log "所有服务已停止"
