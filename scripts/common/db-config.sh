#!/bin/bash
# scripts/db-config.sh
# 数据库配置统一管理
# Database configuration management
#
# 使用方法:
#   source scripts/db-config.sh
#
# 环境变量优先级高于默认值:
#   DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD

# 默认配置（Docker MySQL，与 docker compose -f docker-compose.infra.yml 和 .env 一致）
DB_HOST_DEFAULT="localhost"
DB_PORT_DEFAULT="3306"
DB_USERNAME_DEFAULT="root"
DB_PASSWORD_DEFAULT="${DB_PASSWORD:-Oa1ssDb2026Pr0dSecureP4ss}"
DB_NAME_DEFAULT="oaiss_chain"

# Docker容器名称
MYSQL_CONTAINER="oaiss-mysql"

# 应用环境变量（如果设置）
DB_HOST="${DB_HOST:-$DB_HOST_DEFAULT}"
DB_PORT="${DB_PORT:-$DB_PORT_DEFAULT}"
DB_USERNAME="${DB_USERNAME:-$DB_USERNAME_DEFAULT}"
DB_PASSWORD="${DB_PASSWORD:-$DB_PASSWORD_DEFAULT}"
DB_NAME="${DB_NAME:-$DB_NAME_DEFAULT}"

# MySQL连接参数
MYSQL_ARGS=(-h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" -p"$DB_PASSWORD")

is_wsl() {
    grep -qi microsoft /proc/version 2>/dev/null
}

find_command_bin() {
    local name="$1"
    local candidates=()
    local candidate

    if is_wsl; then
        candidates=("${name}.exe" "$name")
    else
        candidates=("$name" "${name}.exe")
    fi

    for candidate in "${candidates[@]}"; do
        if command -v "$candidate" >/dev/null 2>&1; then
            command -v "$candidate"
            return 0
        fi
    done

    return 1
}

DOCKER_BIN="$(find_command_bin docker 2>/dev/null || true)"
MYSQL_BIN="$(find_command_bin mysql 2>/dev/null || true)"

docker_cli_available() {
    [[ -n "$DOCKER_BIN" ]]
}

mysql_cli_available() {
    [[ -n "$MYSQL_BIN" ]]
}

mysql_container_running() {
    if ! docker_cli_available; then
        return 1
    fi

    "$DOCKER_BIN" ps --format '{{.Names}}' 2>/dev/null | tr -d '\r' | grep -q "^${MYSQL_CONTAINER}$"
}

# Docker MySQL连接（如果容器运行中）
docker_mysql_exec() {
    if mysql_container_running; then
        "$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$@"
    else
        echo "Error: MySQL container '$MYSQL_CONTAINER' is not running"
        return 1
    fi
}

run_mysql_query() {
    local query="$1"
    if mysql_container_running; then
        "$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -N -e "$query" 2>/dev/null
    elif mysql_cli_available; then
        "$MYSQL_BIN" "${MYSQL_ARGS[@]}" -N -e "$query" "$DB_NAME"
    else
        echo "Error: mysql client not found and Docker MySQL container '$MYSQL_CONTAINER' is not running" >&2
        return 127
    fi
}

exec_mysql_query() {
    local query="$1"
    if mysql_container_running; then
        "$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "$query" 2>/dev/null
    elif mysql_cli_available; then
        "$MYSQL_BIN" "${MYSQL_ARGS[@]}" -e "$query" "$DB_NAME"
    else
        echo "Error: mysql client not found and Docker MySQL container '$MYSQL_CONTAINER' is not running" >&2
        return 127
    fi
}

# 检查MySQL是否健康
check_mysql_health() {
    if mysql_container_running; then
        "$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysqladmin ping -h localhost -u"$DB_USERNAME" -p"$DB_PASSWORD" --silent 2>/dev/null
    elif mysql_cli_available; then
        "$MYSQL_BIN" "${MYSQL_ARGS[@]}" -e "SELECT 1" >/dev/null 2>&1
    else
        return 1
    fi
}

# 打印配置信息（密码已脱敏）
print_db_config() {
    echo "数据库配置:"
    echo "  Host: $DB_HOST:$DB_PORT"
    echo "  User: $DB_USERNAME"
    echo "  Password: ******"
    echo "  Database: $DB_NAME"
    echo "  Container: $MYSQL_CONTAINER"
}
