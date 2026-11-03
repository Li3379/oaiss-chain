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
    # 不带 --with-fabric 时没有 fabric profile，SecurityStartupValidator 会按
    # 「全真」原则拒绝启动（MockBlockchainService 曾伪造 tx_mock_ 哈希写进库）。
    #
    # 这里必须显式告诉用户两条路，而不是让他看到一个 panic 日志：
    #   1) 要真实链上存证 → 加 --with-fabric
    #   2) 只做纯业务调试   → 保持当前 profile，但需显式开逃生门
    #
    # 默认**不**替用户打开逃生门：那会让「我明明没加 --with-fabric」悄悄变成可启动，
    # 正是这次要消除的静默降级。只在用户自己 export 了环境变量时才透传。
    export SPRING_PROFILES_ACTIVE=local
    SPRING_ARGS=(-Dspring-boot.run.profiles=local)

    if [[ "${OAISS_ALLOW_MOCK_CHAIN:-false}" != "true" ]]; then
        echo
        echo "⚠  未指定 --with-fabric：本次启动将没有 'fabric' profile。"
        echo "   后端会拒绝启动（SecurityStartupValidator 防止伪造链上存证）。"
        echo
        echo "   选择一（推荐，真实存证）："
        echo "     ./scripts/start-backend.sh --with-fabric"
        echo "   选择二（纯业务调试，不上链）："
        echo "     OAISS_ALLOW_MOCK_CHAIN=true ./scripts/start-backend.sh"
        echo "     （该模式不产出任何 txHash，报告停留「审核通过」）"
        echo
        echo "   3 秒后继续，以便你确认或 Ctrl-C 中止..."
        sleep 3
    fi
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
