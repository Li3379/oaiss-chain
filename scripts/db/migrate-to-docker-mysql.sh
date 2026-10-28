#!/bin/bash
# scripts/migrate-to-docker-mysql.sh
# 数据迁移脚本：从本地MySQL迁移到Docker MySQL

set -e

# 加载数据库配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
if [[ -f "$SCRIPT_DIR/db-config.sh" ]]; then
    source "$SCRIPT_DIR/db-config.sh"
elif [[ -f "$PROJECT_ROOT/scripts/common/db-config.sh" ]]; then
    source "$PROJECT_ROOT/scripts/common/db-config.sh"
elif [[ -f "$PROJECT_ROOT/scripts/db-config.sh" ]]; then
    source "$PROJECT_ROOT/scripts/db-config.sh"
else
    echo "db-config.sh not found" >&2
    exit 1
fi

# WSL docker detection
_docker_cmd=""
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${MYSQL_CONTAINER}$"; then
    _docker_cmd="docker"
elif command -v docker.exe >/dev/null 2>&1 && docker.exe ps --format '{{.Names}}' 2>/dev/null | tr -d '\r' | grep -q "^${MYSQL_CONTAINER}$"; then
    _docker_cmd="docker.exe"
fi

echo "=========================================="
echo "MySQL数据迁移脚本"
echo "=========================================="
echo ""
echo "此脚本将："
echo "1. 备份本地MySQL数据"
echo "2. 停止本地MySQL服务"
echo "3. 重启Docker MySQL使用3306端口"
echo "4. 导入数据到Docker MySQL"
echo ""

# 配置
BACKUP_DIR="./backups"
BACKUP_FILE="${BACKUP_DIR}/oaiss_chain_backup_$(date +%Y%m%d_%H%M%S).sql"

# 创建备份目录
mkdir -p "$BACKUP_DIR"

echo "步骤1: 备份本地MySQL数据..."
echo "备份文件: $BACKUP_FILE"

mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" -p"$DB_PASSWORD" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    "$DB_NAME" > "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo "✓ 备份成功: $(ls -lh "$BACKUP_FILE" | awk '{print $5}')"
else
    echo "✗ 备份失败，请检查MySQL连接"
    exit 1
fi

echo ""
echo "步骤2: 停止本地MySQL服务..."
echo "请手动执行以下命令（需要管理员权限）："
echo ""
echo "Windows:"
echo "  net stop mysql"
echo "  或在服务管理器中停止MySQL服务"
echo ""
echo "Linux/Mac:"
echo "  sudo systemctl stop mysql"
echo "  或 sudo service mysql stop"
echo ""
read -p "本地MySQL已停止？(y/n): " confirm
if [ "$confirm" != "y" ]; then
    echo "迁移已取消"
    exit 0
fi

echo ""
echo "步骤3: 重启Docker MySQL..."
COMPOSE_FILE="$PROJECT_ROOT/deploy/compose/docker-compose.infra.yml"
if [[ ! -f "$COMPOSE_FILE" ]]; then
    COMPOSE_FILE="$PROJECT_ROOT/docker-compose.infra.yml"
fi
# 停止并删除现有容器
"${_docker_cmd:-docker}" compose -f "$COMPOSE_FILE" down
# 重新启动（使用3306端口）
"${_docker_cmd:-docker}" compose -f "$COMPOSE_FILE" up -d mysql

# 等待MySQL就绪
echo "等待MySQL启动..."
sleep 10

# 检查健康状态
for i in {1..30}; do
    if "$_docker_cmd" exec "$MYSQL_CONTAINER" mysqladmin ping -h localhost -u"$DB_USERNAME" -p"$DB_PASSWORD" --silent 2>/dev/null; then
        echo "✓ Docker MySQL已就绪"
        break
    fi
    sleep 1
done

echo ""
echo "步骤4: 导入数据到Docker MySQL..."
"$_docker_cmd" exec -i "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" < "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo "✓ 数据导入成功"
else
    echo "✗ 数据导入失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "迁移完成！"
echo "=========================================="
echo ""
echo "验证："
"$_docker_cmd" exec "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" -e "
    SELECT 'Tables count:' AS info, COUNT(*) AS value FROM information_schema.tables WHERE table_schema = '$DB_NAME'
    UNION ALL
    SELECT 'Carbon reports:', COUNT(*) FROM $DB_NAME.carbon_report
    UNION ALL
    SELECT 'Enterprises:', COUNT(*) FROM $DB_NAME.enterprise;
"

echo ""
echo "备份文件位置: $BACKUP_FILE"
echo "请妥善保管备份文件"
