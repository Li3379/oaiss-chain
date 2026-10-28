#!/usr/bin/env bash
set -euo pipefail

# Load database configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/db-config.sh"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

SKIP_FRONTEND_CHECK="${SKIP_FRONTEND_CHECK:-false}"
if is_wsl; then
  BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://host.docker.internal:8080/api/v1}"
  FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://host.docker.internal:5173}"
else
  BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080/api/v1}"
  FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:5173}"
fi

ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }
info() { echo -e "${YELLOW}[..]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

http_ok() {
    local url=$1
    if curl -sf "$url" >/dev/null 2>&1; then
        return 0
    fi

    if is_wsl && command -v curl.exe >/dev/null 2>&1; then
        curl.exe -sf "$url" >/dev/null 2>&1
        return $?
    fi

    return 1
}

# 1. Docker Desktop running (D-09)
info "Checking Docker Desktop..."
[[ -n "${DOCKER_BIN:-}" ]] || fail "Docker CLI not found. Ensure Docker Desktop is installed and available in PATH."
"$DOCKER_BIN" info &>/dev/null || fail "Docker Desktop is not running. Start Docker Desktop and retry."
ok "Docker Desktop is running"

# 2. MySQL healthy on port 3306 (D-14)
info "Checking MySQL ($MYSQL_CONTAINER)..."
"$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysqladmin ping -h localhost -u"$DB_USERNAME" -p"$DB_PASSWORD" --silent &>/dev/null \
  || fail "MySQL not responding. Run: docker compose -f docker-compose.infra.yml up -d"
ok "MySQL is healthy on :$DB_PORT"

# 3. Flyway tables count (D-16)
info "Checking Flyway migrations (21 tables)..."
TABLE_COUNT=$("$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'" -sN 2>/dev/null)
[[ "$TABLE_COUNT" -ge 21 ]] || fail "Expected 21+ tables, found $TABLE_COUNT. Flyway migration may have failed."
ok "Database has $TABLE_COUNT tables (>= 21 expected)"

# 4. V3 seed data present (D-16)
info "Checking V3 seed data..."
ENT003_COUNT=$("$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" -e \
  "SELECT COUNT(*) FROM $DB_NAME.user WHERE username='enterprise003'" -sN 2>/dev/null)
[[ "$ENT003_COUNT" -ge 1 ]] || fail "enterprise003 user not found. V3 migration may not have run."
ok "V3 seed data verified (enterprise003)"

# 5. Legacy authenticator table retired by V8
info "Checking V8 legacy table retirement..."
AUTH_TABLE_COUNT=$("$DOCKER_BIN" exec "$MYSQL_CONTAINER" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='authenticator'" -sN 2>/dev/null)
[[ "$AUTH_TABLE_COUNT" -eq 0 ]] || fail "Legacy authenticator table still exists. Ensure Flyway V8__drop_legacy_authenticator.sql has been applied."
ok "Legacy authenticator table is absent"

# 6. Redis PING (D-14)
info "Checking Redis (oaiss-redis)..."
"$DOCKER_BIN" exec oaiss-redis redis-cli -a "${REDIS_PASSWORD:-oaiss_redis_dev_2026}" ping 2>/dev/null | grep -q PONG \
  || fail "Redis not responding. Run: docker compose -f docker-compose.infra.yml up -d"
ok "Redis is healthy on :6379"

# 7. MinIO console (D-14)
info "Checking MinIO console..."
http_ok "http://localhost:9003" \
  || fail "MinIO console not responding on :9003. Check oaiss-minio container."
ok "MinIO console accessible on :9003"

# 8. Backend API reachable (ENV-04, updated after BUG-02: Swagger now requires auth)
info "Checking backend API..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BACKEND_BASE_URL/auth/login" -H "Content-Type: application/json" 2>/dev/null)
[[ "$HTTP_CODE" =~ ^(200|400|405)$ ]] || fail "Backend API at $BACKEND_BASE_URL returned HTTP $HTTP_CODE (expected 200/400/405). Start it with './scripts/start-backend.sh' or 'scripts\\start-backend.bat'."
ok "Backend API reachable at $BACKEND_BASE_URL (HTTP $HTTP_CODE)"

# 9. Frontend (ENV-05)
if [[ "$SKIP_FRONTEND_CHECK" == "true" ]]; then
  warn "Skipping frontend dev server check because SKIP_FRONTEND_CHECK=true"
else
  info "Checking frontend dev server..."
  if ! http_ok "$FRONTEND_BASE_URL"; then
    if is_wsl && http_ok "http://localhost:5173"; then
      FRONTEND_BASE_URL="http://localhost:5173"
    else
      fail "Frontend not responding at $FRONTEND_BASE_URL. Is frontend running via 'npm run dev'? Set SKIP_FRONTEND_CHECK=true to skip this probe."
    fi
  fi
  ok "Frontend accessible at $FRONTEND_BASE_URL"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN} All health checks passed!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Services verified:"
echo "  - MySQL    : $DB_HOST:$DB_PORT ($TABLE_COUNT tables, V3 data present)"
echo "  - Redis    : localhost:6379"
echo "  - MinIO    : localhost:9002 (console :9003)"
echo "  - Backend  : $BACKEND_BASE_URL"
if [[ "$SKIP_FRONTEND_CHECK" == "true" ]]; then
  echo "  - Frontend : skipped"
else
  echo "  - Frontend : $FRONTEND_BASE_URL"
fi
echo ""
echo "Seed accounts (password: admin123):"
echo "  admin, enterprise001, enterprise002, enterprise003"
echo "  reviewer001, thirdparty001"
