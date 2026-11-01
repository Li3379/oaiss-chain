#!/usr/bin/env bash
# OAISS CHAIN - 统一启动脚本
# 用法: ./scripts/start-all.sh [选项]
#   --with-fabric    同时启动 Fabric 网络
#   --skip-frontend  跳过前端启动
#   --skip-ml        跳过 ML 服务启动
#   --skip-backend   跳过后端启动
#   --infra-only     仅启动基础设施（MySQL/Redis/MinIO）
#   --help           显示本帮助并退出

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 加载 .env
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    set -a; source "$PROJECT_ROOT/.env"; set +a
fi

# 默认值
WITH_FABRIC=false
SKIP_FRONTEND=false
SKIP_ML=false
SKIP_BACKEND=false
INFRA_ONLY=false

# 解析参数
for arg in "$@"; do
    case $arg in
        --with-fabric)  WITH_FABRIC=true ;;
        --skip-frontend) SKIP_FRONTEND=true ;;
        --skip-ml)      SKIP_ML=true ;;
        --skip-backend) SKIP_BACKEND=true ;;
        --infra-only)   INFRA_ONLY=true ;;
        --help|-h)      grep '^#   --' "$0" | sed 's/^#   //'; exit 0 ;;
        *) echo "未知参数: $arg (使用 --help 查看用法)"; exit 1 ;;
    esac
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[START]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*"; }

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

ENV_ARGS=()
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    ENV_ARGS=(--env-file "$PROJECT_ROOT/.env")
fi

# 健康检查：支持 HTTP 和 TCP 两种方式
check_port() {
    local host=$1 port=$2 name=$3 max_wait=${4:-30}
    local waited=0
    while (( waited < max_wait )); do
        if (echo > /dev/tcp/"$host"/"$port") 2>/dev/null; then
            log "$name 已就绪 (${host}:${port})"
            return 0
        fi
        sleep 1
        ((waited++))
    done
    warn "$name 在 ${max_wait}s 内未就绪"
    return 1
}

check_http() {
    local url=$1 name=$2 max_wait=${3:-30}
    local waited=0
    while (( waited < max_wait )); do
        if curl -sf "$url" > /dev/null 2>&1; then
            log "$name 已就绪 ($url)"
            return 0
        fi
        sleep 1
        ((waited++))
    done
    warn "$name 在 ${max_wait}s 内未就绪"
    return 1
}

wait_docker_health() {
    local container=$1 name=$2 max_wait=${3:-60}
    local waited=0
    while (( waited < max_wait )); do
        local status
        status=$("$DOCKER_BIN" inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
        if [[ "$status" == "healthy" ]]; then
            log "$name 容器健康"
            return 0
        fi
        if [[ "$status" == "missing" ]]; then
            warn "$name 容器不存在"
            return 1
        fi
        sleep 1
        ((waited++))
    done
    warn "$name 在 ${max_wait}s 内未达到 healthy 状态"
    return 1
}

# ─── 1. 基础设施 ───
log "启动基础设施（MySQL, Redis, MinIO, ML Service）..."
"$DOCKER_BIN" compose "${ENV_ARGS[@]}" -f "$PROJECT_ROOT/deploy/compose/docker-compose.infra.yml" up -d

log "等待 MySQL 就绪..."
wait_docker_health "oaiss-mysql" "MySQL" 60 || true

log "等待 Redis 就绪..."
wait_docker_health "oaiss-redis" "Redis" 30 || true

log "等待 MinIO 就绪..."
wait_docker_health "oaiss-minio" "MinIO" 60 || true

if [[ "$INFRA_ONLY" == true ]]; then
    log "仅基础设施模式，跳过应用服务"
    exit 0
fi

# ─── 2. Fabric 网络（可选）───
if [[ "$WITH_FABRIC" == true ]]; then
    log "启动 Fabric 网络..."
    "$DOCKER_BIN" compose "${ENV_ARGS[@]}" -f "$PROJECT_ROOT/deploy/compose/docker-compose.fabric.yml" up -d

    log "等待 Fabric Orderer 就绪..."
    wait_docker_health "orderer.example.com" "Orderer" 30 || true

    log "等待 Fabric Peer 就绪..."
    wait_docker_health "peer0.org1.example.com" "Peer" 30 || true

    log "等待 Fabric CA 就绪..."
    wait_docker_health "ca.org1.example.com" "Fabric CA" 30 || true

    log "引导 Fabric 通道与链码..."
    "$PROJECT_ROOT/scripts/bootstrap-fabric.sh"
else
    warn "跳过 Fabric 网络（使用 --with-fabric 启用）"
fi

# ─── 3. ML 服务（Docker 已含 ML，若需本地运行可取消 skip-ml）───
if [[ "$SKIP_ML" != true ]]; then
    # 检查 ML 是否已由 Docker 提供
    if "$DOCKER_BIN" ps --format '{{.Names}}' 2>/dev/null | grep -q "oaiss-ml-service"; then
        log "ML 服务已由 Docker 容器提供"
        wait_docker_health "oaiss-ml-service" "ML Service" 30 || true
    else
        log "启动 ML 服务（FastAPI）..."
        cd "$PROJECT_ROOT/oaiss-chain-ml-service"
        if command -v uvicorn &> /dev/null; then
            uvicorn app.main:app --host 0.0.0.0 --port 8001 &
            ML_PID=$!
            log "ML 服务 PID: $ML_PID"
            check_http "http://localhost:8001/health" "ML Service" 30 || true
        else
            warn "uvicorn 未安装，跳过 ML 服务"
        fi
    fi
else
    warn "跳过 ML 服务"
fi

# ─── 4. 后端 ───
if [[ "$SKIP_BACKEND" != true ]]; then
    log "启动后端（Spring Boot）..."
    if [[ "$WITH_FABRIC" == true ]]; then
        "$PROJECT_ROOT/scripts/start-backend.sh" --with-fabric &
    else
        "$PROJECT_ROOT/scripts/start-backend.sh" &
    fi
    BACKEND_PID=$!
    log "后端 PID: $BACKEND_PID，等待就绪..."
    # 后端 context-path=/api/v1，健康检查完整路径
    check_http "http://localhost:8080/api/v1/actuator/health" "Backend" 90 || true
else
    warn "跳过后端"
fi

# ─── 5. 前端 ───
if [[ "$SKIP_FRONTEND" != true ]]; then
    log "启动前端（Vue 3）..."
    cd "$PROJECT_ROOT/oaiss-chain-frontend"
    npm run dev &
    FRONTEND_PID=$!
    log "前端 PID: $FRONTEND_PID"
    check_http "http://localhost:5173" "Frontend" 30 || true
else
    warn "跳过前端"
fi

cd "$PROJECT_ROOT"
echo ""
log "========================================="
log "OAISS CHAIN 服务已启动"
log "  后端:   http://localhost:8080/api/v1"
log "  前端:   http://localhost:5173"
log "  ML:     http://localhost:8001"
log "  MinIO:  http://localhost:9003 (Console)"
if [[ "$WITH_FABRIC" == true ]]; then
log "  Orderer: http://localhost:7050"
log "  Peer:    http://localhost:7051"
fi
log "========================================="
log "使用 ./scripts/stop-all.sh 停止所有服务"
