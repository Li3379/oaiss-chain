#!/usr/bin/env bash
# OAISS CHAIN - 后端启动脚本
# 用法: ./scripts/start-backend.sh [选项]
#   --with-fabric    使用 local,fabric profiles
#   --port <端口>    覆盖默认 server.port，便于并行验证不同 profile

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

WITH_FABRIC=false
SERVER_PORT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --with-fabric)
            WITH_FABRIC=true
            shift
            ;;
        --port)
            if [[ $# -lt 2 ]]; then
                echo "缺少 --port 的端口值"
                exit 1
            fi
            SERVER_PORT="$2"
            shift 2
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

if [[ -f "$PROJECT_ROOT/.env" ]]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

if [[ "$WITH_FABRIC" == true ]]; then
    export SPRING_PROFILES_ACTIVE=local,fabric
    SPRING_ARGS=(-Dspring-boot.run.profiles=local,fabric)
else
    export SPRING_PROFILES_ACTIVE=local
    SPRING_ARGS=(-Dspring-boot.run.profiles=local)
fi

if [[ -n "$SERVER_PORT" ]]; then
    SPRING_ARGS+=("-Dspring-boot.run.arguments=--server.port=${SERVER_PORT}")
fi

echo
echo "========================================"
echo " OAISS CHAIN Backend"
echo " Profiles: $SPRING_PROFILES_ACTIVE"
if [[ -n "$SERVER_PORT" ]]; then
    echo " Port: $SERVER_PORT"
fi
echo "========================================"
echo

cd "$PROJECT_ROOT/oaiss-chain-backend"
exec mvn spring-boot:run "${SPRING_ARGS[@]}"
