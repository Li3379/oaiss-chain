#!/bin/bash
# Shared test helper library for OAISS CHAIN API test scripts
# Source this file: source "$(dirname "$0")/test-helpers.sh"
#
# Provides: colors, assertions, login, DB helpers, backend check, curl wrapper.
# All functions are backward-compatible with existing test scripts.
#
# Rate-limit aware helpers (1010 "请求过于频繁", HTTP 200 + body code 1010):
#   login_user / curl_get / curl_with_retry wait 60s and retry on 1010.
# Overridable via CURL_RATE_LIMIT_WAIT / CURL_RATE_LIMIT_RETRIES.
#
# Required seed data:
#   - Users: admin, enterprise001, thirdparty001 (password from TEST_PASSWORD env, default: admin123)
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

set -euo pipefail

# ============================================================
# Colors
# ============================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[..]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

# ============================================================
# Configuration (overridable via environment)
# ============================================================
detect_base_url() {
    if [ -n "${BASE_URL:-}" ]; then
        echo "$BASE_URL"
        return
    fi
    if [ -n "${API:-}" ]; then
        echo "$API"
        return
    fi
    if grep -qi microsoft /proc/version 2>/dev/null; then
        echo "http://host.docker.internal:8080/api/v1"
    else
        echo "http://localhost:8080/api/v1"
    fi
}

BASE_URL="$(detect_base_url)"
TEST_PASSWORD="${TEST_PASSWORD:-admin123}"
API_PASSWORD="${API_PASSWORD:-${TEST_PASSWORD}}"
CURL_OPTS="${CURL_OPTS:---connect-timeout 10 --max-time 30}"
 CURL_MAX_RETRIES="${CURL_MAX_RETRIES:-3}"
 CURL_RETRY_DELAY="${CURL_RETRY_DELAY:-2}"
# Backend rate limiter (RateLimitAspect / RoleBasedRateLimitInterceptor) rejects
# with business code 1010 "请求过于频繁" while the request still succeeds at the
# HTTP level (or maps to 4xx). Detection must parse the JSON body, not HTTP code.
# The limiter window is 60s, so wait 60s between retries; CURL_RATE_LIMIT_RETRIES
# is the number of retries AFTER the first 1010 response (default 2).
CURL_RATE_LIMIT_WAIT="${CURL_RATE_LIMIT_WAIT:-60}"
CURL_RATE_LIMIT_RETRIES="${CURL_RATE_LIMIT_RETRIES:-2}"

# --- Database defaults (source db-config.sh for Docker container credentials) ---
_DB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$_DB_SCRIPT_DIR/db-config.sh" ]; then
    DB_PORT=3306 source "$_DB_SCRIPT_DIR/db-config.sh"
fi
DB_USER="${DB_USERNAME:-${DB_USER:-root}}"
DB_PASS="${DB_PASSWORD:-${DB_PASS:-123456}}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-oaiss_chain}"

# ============================================================
# Test counters
# ============================================================
PASS=0
FAIL=0
SKIP=0
TEST_ID=0
TOTAL=0

# ============================================================
# Dependency checks
# ============================================================
check_dependencies() {
    local extra_cmds=("$@")
    for cmd in curl grep cut head "${extra_cmds[@]}"; do
        command -v "$cmd" >/dev/null 2>&1 || { echo "[FATAL] Required command '$cmd' not found"; exit 1; }
    done
}

# ============================================================
# Backend health check
# ============================================================
check_backend() {
    info "Checking backend availability..."
    # Use actuator health (unauthenticated) instead of a login probe:
    # login consumes the scarce auth:login rate-limit quota (5 req / 60s),
    # which serial batch runs would exhaust and misreport as "backend down".
    curl -sf $CURL_OPTS "$BASE_URL/actuator/health" -o /dev/null 2>/dev/null \
        || { fail "Backend not running. Start it first with './scripts/start-backend.sh' or 'scripts\\start-backend.bat'."; exit 1; }
    ok "Backend is reachable"
}

# ============================================================
# JSON field extraction (jq preferred, grep fallback)
# ============================================================
extract_field() {
    local json="$1" field="$2"
    if command -v jq >/dev/null 2>&1; then
        echo "$json" | jq -r ".$field" 2>/dev/null | grep -v '^null$' || true
    else
        if [[ "$field" == *.* ]]; then
            local parent_field child_field
            parent_field="${field%%.*}"
            child_field="${field#*.}"
            { echo "$json" | grep -o "\"$child_field\":[^,}]*" 2>/dev/null | head -1 | sed "s/\"$child_field\"://" | tr -d "\" "; } 2>/dev/null || true
        else
            { echo "$json" | grep -o "\"$field\":[^,}]*" 2>/dev/null | head -1 | sed "s/\"$field\"://" | tr -d '"'; } 2>/dev/null || true
        fi
    fi
}

# Extract from nested .data object (e.g., extract_field_data "$resp" "id" => .data.id)
extract_data_field() {
    local json="$1" field="$2"
    if command -v jq >/dev/null 2>&1; then
        echo "$json" | jq -r ".data.$field // empty" 2>/dev/null || true
    else
        # Fallback: search the entire response for the field (works for flat nesting)
        { echo "$json" | grep -o "\"$field\":[^,}]*" 2>/dev/null | head -1 | sed "s/\"$field\"://" | tr -d '"'; } 2>/dev/null || true
    fi
}

# ============================================================
# Login helpers
# ============================================================
login_user() {
    local username="$1"
    local password="${2:-$TEST_PASSWORD}"
    local attempt=0
    local body
    while :; do
        body=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auth/login" \
            -H "Content-Type: application/json" \
            -d "{\"username\":\"$username\",\"password\":\"$password\"}")
        if ! is_rate_limited_1010 "$body"; then
            echo "$body"
            return 0
        fi
        attempt=$((attempt + 1))
        if [ "$attempt" -le "$CURL_RATE_LIMIT_RETRIES" ]; then
            warn "Login for $username rate limited (code 1010), waiting ${CURL_RATE_LIMIT_WAIT}s and retrying (attempt $attempt/$CURL_RATE_LIMIT_RETRIES)..."
            sleep "$CURL_RATE_LIMIT_WAIT"
        else
            break
        fi
    done
    # All retries exhausted: return the last 1010 response (exit 0, curl-like)
    # so callers (login / extract_token) report the real backend error.
    echo "$body"
    return 0
}

# Returns 0 when the response body carries backend business code 1010
# ("请求过于频繁" / rate limited) — usually with HTTP 200.
is_rate_limited_1010() {
    local response="$1"
    [[ -z "$response" ]] && return 1
    if command -v jq >/dev/null 2>&1; then
        [ "$(echo "$response" | jq -r '.code // empty' 2>/dev/null)" = "1010" ]
    else
        echo "$response" | grep -qE '"code"\s*:\s*1010'
    fi
}

# Login and return just the token
login() {
    local username="$1"
    local resp
    resp=$(login_user "$username")
    local code
    code=$(extract_field "$resp" "code")
    if [[ "$code" != "200" ]]; then
        fail "$username: Login failed (code=$code)"
        echo "$resp" >&2
        return 1
    fi
    local token
    token=$(extract_field "$resp" "data.accessToken")
    if [ -z "$token" ]; then
        token=$(echo "$resp" | grep -o '"accessToken":"[^"]*"' 2>/dev/null | head -1 | cut -d'"' -f4)
    fi
    if [ -z "$token" ]; then
        fail "$username: No accessToken in response"
        return 1
    fi
    echo "$token"
}

# Login and extract token, or exit on failure
extract_token() {
    local resp="$1" username="$2"
    local token
    token=$(extract_field "$resp" "data.accessToken")
    if [ -z "$token" ]; then
        token=$(echo "$resp" | grep -o '"accessToken":"[^"]*"' 2>/dev/null | head -1 | cut -d'"' -f4)
    fi
    if [ -z "$token" ]; then
        echo "  [FATAL] Login failed for $username. Response: $(echo "$resp" | head -c 300)"
        exit 1
    fi
    echo "$token"
}

# ============================================================
# MySQL helpers
# ============================================================
# Detect docker binary (WSL may need docker.exe)
_docker_cmd=""
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^oaiss-mysql$"; then
    _docker_cmd="docker"
elif command -v docker.exe >/dev/null 2>&1 && docker.exe ps --format '{{.Names}}' 2>/dev/null | tr -d '\r' | grep -q "^oaiss-mysql$"; then
    _docker_cmd="docker.exe"
fi

run_mysql() {
    local query="$1"
    local result=""

    # Try Docker exec first (works in WSL without mysql client)
    if [ -n "$_docker_cmd" ]; then
        result=$($_docker_cmd exec oaiss-mysql mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -sNe "$query" 2>/dev/null) || {
            echo "MYSQL_ERROR:$result"
            return 1
        }
        echo "$result"
        return 0
    fi

    # Fallback: local mysql client
    if command -v mysql >/dev/null 2>&1; then
        local mysql_cnf
        mysql_cnf=$(mktemp)
        cat > "$mysql_cnf" <<EOF
[client]
user=$DB_USER
password=$DB_PASS
host=$DB_HOST
port=$DB_PORT
EOF
        result=$(mysql --defaults-extra-file="$mysql_cnf" "$DB_NAME" -sNe "$query" 2>&1) || {
            rm -f "$mysql_cnf"
            echo "MYSQL_ERROR:$result"
            return 1
        }
        rm -f "$mysql_cnf"
        echo "$result"
        return 0
    fi

    echo "MYSQL_ERROR: neither docker nor mysql client available"
    return 1
}

validate_integer() {
    local var_name="$1" var_value="$2"
    if ! [[ "$var_value" =~ ^[0-9]+$ ]]; then
        echo "  [ERROR] Invalid $var_name: '$var_value' (must be a positive integer)"
        return 1
    fi
}

# ============================================================
# Curl wrapper that returns: HTTP_CODE BODY
# Retries on backend rate limiting (body code 1010, HTTP 200/4xx).
# ============================================================
curl_get() {
    local url="$1"
    shift
    local attempt=0 http_code body
    while :; do
        body=$(curl -s $CURL_OPTS -w "\n%{http_code}" "$url" "$@" 2>/dev/null)
        http_code=$(echo "$body" | tail -1)
        body=$(echo "$body" | sed '$d')
        # Rate limited with HTTP 429 or backend code 1010 in the JSON body
        if [[ "$http_code" == "429" ]] || is_rate_limited_1010 "$body"; then
            attempt=$((attempt + 1))
            if [ "$attempt" -le "$CURL_RATE_LIMIT_RETRIES" ]; then
                warn "Rate limited (HTTP $http_code, code 1010), waiting ${CURL_RATE_LIMIT_WAIT}s and retrying... (attempt $attempt/$CURL_RATE_LIMIT_RETRIES)"
                sleep "$CURL_RATE_LIMIT_WAIT"
                continue
            fi
            warn "Rate limited for $url after $CURL_RATE_LIMIT_RETRIES retries"
        fi
        echo "$http_code"
        echo "$body"
        return 0
    done
}

# ============================================================
# Curl wrapper with retry logic for rate limiting
# Retries on HTTP 429 / 5xx (short backoff) AND on backend code 1010
# (long 60s wait — the backend rate-limit window).
# ============================================================
curl_with_retry() {
    local url="$1"
    shift
    local attempt=0
    local http_code
    local body; local delay=$CURL_RETRY_DELAY

    while [ $attempt -lt $CURL_MAX_RETRIES ]; do
        body=$(curl -s $CURL_OPTS -w "\n%{http_code}" "$url" "$@" 2>/dev/null)
        http_code=$(echo "$body" | tail -1)
        body=$(echo "$body" | sed '$d')

        # Rate limited (HTTP 429 or body code 1010) or server error (5xx): retry
        if [[ "$http_code" == "429" ]] || is_rate_limited_1010 "$body" || [[ "$http_code" =~ ^5[0-9][0-9]$ ]]; then
            attempt=$((attempt + 1))
            if [ $attempt -lt $CURL_MAX_RETRIES ]; then
                if is_rate_limited_1010 "$body" || [[ "$http_code" == "429" ]]; then
                    # 1010 => backend rate-limit window is 60s; wait the full window
                    warn "Rate limited (HTTP $http_code, code 1010), waiting ${CURL_RATE_LIMIT_WAIT}s... (attempt $attempt/$CURL_MAX_RETRIES)"
                    sleep "$CURL_RATE_LIMIT_WAIT"
                else
                    warn "Server error ($http_code), retrying in $delay seconds... (attempt $attempt/$CURL_MAX_RETRIES)"
                    sleep $delay
                    # Exponential backoff
                    delay=$(echo "$delay * 2" | bc)
                fi
                continue
            fi
        else
            echo "$http_code"
            echo "$body"
            return 0
        fi
    done

    # If all retries failed, return the last response
    warn "All $CURL_MAX_RETRIES attempts failed for $url"
    echo "$http_code"
    echo "$body"
    return 1
}
  
  # ============================================================
  # Assertions
  # ============================================================
assert_code_200() {
    local test_name="$1" response="$2"
    TEST_ID=$((TEST_ID + 1))
    if echo "$response" | grep -qE '"code"\s*:\s*200[^0-9]'; then
        echo "  [PASS] Test $TEST_ID: $test_name"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: $test_name -- expected code 200"
        echo "    Response: $(echo "$response" | head -c 500)"
        FAIL=$((FAIL + 1))
    fi
}

assert_contains() {
    local test_name="$1" response="$2" expected="$3"
    TEST_ID=$((TEST_ID + 1))
    if echo "$response" | grep -qF "$expected"; then
        echo "  [PASS] Test $TEST_ID: $test_name"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: $test_name -- expected '$expected' in response"
        echo "    Response: $(echo "$response" | head -c 500)"
        FAIL=$((FAIL + 1))
    fi
}

assert_not_contains() {
    local test_name="$1" response="$2" expected="$3"
    TEST_ID=$((TEST_ID + 1))
    if ! echo "$response" | grep -qF "$expected"; then
        echo "  [PASS] Test $TEST_ID: $test_name"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: $test_name -- did NOT expect '$expected' in response"
        echo "    Response: $(echo "$response" | head -c 500)"
        FAIL=$((FAIL + 1))
    fi
}

assert_http_code() {
    local test_name="$1" actual="$2" expected="$3"
    TEST_ID=$((TEST_ID + 1))
    if [ "$actual" = "$expected" ]; then
        echo "  [PASS] Test $TEST_ID: $test_name (HTTP $actual)"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: $test_name -- expected HTTP $expected, got $actual"
        FAIL=$((FAIL + 1))
    fi
}

# ============================================================
# Summary printer
# ============================================================
print_summary() {
    echo "========================================"
    echo "Results: $PASS passed, $FAIL failed, $SKIP skipped (total: $TEST_ID tests)"
    echo "========================================"
    if [ "$FAIL" -gt 0 ]; then
        exit 1
    fi
}
