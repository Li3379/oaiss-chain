#!/bin/bash
# 06-01: Bug Fix Verification -- BUG-01/02/03
# Requirements: BUG-01, BUG-02, BUG-03

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Source shared test helpers (provides assert_contains, assert_not_contains,
# login_user, run_mysql, print_summary, and test counters)
source "$SCRIPT_DIR/test-helpers.sh"

# BASE_URL auto-detected by test-helpers.sh

# Cleanup temp files on exit
cleanup() { rm -f /tmp/swagger_resp_body.json /tmp/swagger_auth_resp_body.json; }
trap cleanup EXIT

echo "=== 06-01: Bug Fix Verification (BUG-01~03) ==="
echo ""

# --- Authentication ---
echo "[Setup] Authenticating..."

RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(echo "$RESP_E1" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(echo "$RESP_ADMIN" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)

echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo "  admin token: ${TOKEN_ADMIN:0:20}..."

E1_USER_ID=$(echo "$RESP_E1" | grep -o '"userId":[0-9]*' | head -1 | cut -d: -f2)
echo "  enterprise001 userId: $E1_USER_ID"
echo ""

# ======================================================================
# BUG-01: NonUniqueResultException when user has multiple keypairs
# Fix: DigitalSignatureService uses findLatestByUserId (ORDER BY + LIMIT 1)
# ======================================================================
echo "=== BUG-01: NonUniqueResultException fix ==="
echo ""

# Clean up old keypairs
echo "[BUG-01] Cleanup: Removing old keypairs for enterprise001..."
run_mysql "DELETE FROM rsa_key_pair WHERE user_id=$E1_USER_ID" || true
echo ""

# Generate keypair #1
echo "[BUG-01] Generate keypair #1..."
RESP_GEN1=$(curl -s -X POST "$BASE_URL/signature/keypair/generate" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Gen1 response: $(echo "$RESP_GEN1" | head -c 200)"
assert_contains "Generate keypair #1 returns 200" "$RESP_GEN1" '"code":200'
echo ""

# Generate keypair #2 (creates second non-deleted row -- this caused NonUniqueResultException before fix)
echo "[BUG-01] Generate keypair #2 (creates multiple keypairs)..."
RESP_GEN2=$(curl -s -X POST "$BASE_URL/signature/keypair/generate" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Gen2 response: $(echo "$RESP_GEN2" | head -c 200)"
assert_contains "Generate keypair #2 returns 200" "$RESP_GEN2" '"code":200'
echo ""

# DB check: confirm multiple non-deleted rows exist
KP_COUNT=$(run_mysql "SELECT COUNT(*) FROM rsa_key_pair WHERE user_id=$E1_USER_ID AND is_deleted=0" 2>/dev/null || echo "0")
echo "  Keypair count for user (non-deleted): $KP_COUNT"
if [ "$KP_COUNT" -ge 2 ]; then
    TEST_ID=$((TEST_ID + 1))
    echo "  [PASS] Test $TEST_ID: Multiple keypairs exist (count=$KP_COUNT)"
    PASS=$((PASS + 1))
else
    TEST_ID=$((TEST_ID + 1))
    echo "  [FAIL] Test $TEST_ID: Expected >= 2 keypairs, got $KP_COUNT"
    FAIL=$((FAIL + 1))
fi
echo ""

# The critical test: GET /signature/keypair must NOT throw NonUniqueResultException
echo "[BUG-01] Get keypair (with multiple non-deleted rows)..."
RESP_GET=$(curl -s "$BASE_URL/signature/keypair" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Get keypair response: $(echo "$RESP_GET" | head -c 300)"

assert_contains "Get keypair returns 200 (no NonUniqueResultException)" "$RESP_GET" '"code":200'
assert_contains "Get keypair has publicKey" "$RESP_GET" '"publicKey":'
assert_not_contains "Get keypair does not return error 500" "$RESP_GET" '"code":500'
echo ""

# Sign with multiple keypairs
echo "[BUG-01] Sign with multiple keypairs..."
RESP_SIGN=$(curl -s -X POST "$BASE_URL/signature/sign" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"reportName":"bugfix-test","totalEmission":100}')
echo "  Sign response: $(echo "$RESP_SIGN" | head -c 300)"

assert_contains "Sign returns 200 (no NonUniqueResultException)" "$RESP_SIGN" '"code":200'
assert_contains "Sign has signature data" "$RESP_SIGN" '"signature":'
echo ""

# Cleanup keypairs
echo "[BUG-01] Cleanup: Removing keypairs..."
run_mysql "DELETE FROM rsa_key_pair WHERE user_id=$E1_USER_ID" || true
echo ""

# ======================================================================
# BUG-02: Swagger UI endpoints require authentication
# Fix: SecurityConfig changed .permitAll() to .authenticated()
# ======================================================================
echo "=== BUG-02: Swagger authentication fix ==="
echo ""

# Test unauthenticated access to swagger-ui.html
echo "[BUG-02] Unauthenticated access to swagger-ui.html..."
SWAGGER_RESP=$(curl -s -w "\n%{http_code}" -o /tmp/swagger_resp_body.json \
    "$BASE_URL/swagger-ui.html" 2>/dev/null || true)
SWAGGER_HTTP=$(echo "$SWAGGER_RESP" | tail -1)
SWAGGER_BODY=$(cat /tmp/swagger_resp_body.json 2>/dev/null || echo "")

echo "  HTTP status: $SWAGGER_HTTP"
echo "  Response body (first 200 chars): $(echo "$SWAGGER_BODY" | head -c 200)"

TEST_ID=$((TEST_ID + 1))
# Unauthenticated swagger should NOT return 200 with actual swagger content
# It could be: 302 redirect, 401, 403, or 200 with code:2000 (custom auth entry point)
if echo "$SWAGGER_BODY" | grep -q '"code":2000'; then
    echo "  [PASS] Test $TEST_ID: Unauthenticated swagger returns auth error (code 2000)"
    PASS=$((PASS + 1))
elif [ "$SWAGGER_HTTP" != "200" ]; then
    echo "  [PASS] Test $TEST_ID: Unauthenticated swagger returns non-200 status ($SWAGGER_HTTP)"
    PASS=$((PASS + 1))
elif echo "$SWAGGER_BODY" | grep -q '"code":2004'; then
    echo "  [PASS] Test $TEST_ID: Unauthenticated swagger returns permission denied (code 2004)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Unauthenticated swagger returned 200 with content (should require auth)"
    echo "    HTTP: $SWAGGER_HTTP, Body: $(echo "$SWAGGER_BODY" | head -c 300)"
    FAIL=$((FAIL + 1))
fi
echo ""

# Test authenticated access to swagger-ui.html
echo "[BUG-02] Authenticated access to swagger-ui.html..."
SWAGGER_AUTH_RESP=$(curl -s -w "\n%{http_code}" -o /tmp/swagger_auth_resp_body.json \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    "$BASE_URL/swagger-ui.html" 2>/dev/null || true)
SWAGGER_AUTH_HTTP=$(echo "$SWAGGER_AUTH_RESP" | tail -1)
SWAGGER_AUTH_BODY=$(cat /tmp/swagger_auth_resp_body.json 2>/dev/null || echo "")

echo "  HTTP status: $SWAGGER_AUTH_HTTP"
echo "  Response body (first 200 chars): $(echo "$SWAGGER_AUTH_BODY" | head -c 200)"

TEST_ID=$((TEST_ID + 1))
# Authenticated swagger should return 200 or 302 redirect to actual swagger UI
# Note: swagger-ui.html may redirect to /swagger-ui/index.html
if [ "$SWAGGER_AUTH_HTTP" = "200" ] || [ "$SWAGGER_AUTH_HTTP" = "302" ]; then
    echo "  [PASS] Test $TEST_ID: Authenticated swagger access succeeds (HTTP $SWAGGER_AUTH_HTTP)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Authenticated swagger access failed (HTTP $SWAGGER_AUTH_HTTP)"
    echo "    Body: $(echo "$SWAGGER_AUTH_BODY" | head -c 300)"
    FAIL=$((FAIL + 1))
fi
echo ""

# ======================================================================
# BUG-03: CORS does not allow unauthorized origins
# Fix: Removed @Value default, YAML-level default is intentional
# ======================================================================
echo "=== BUG-03: CORS default origin fix ==="
echo ""

# Test CORS for unauthorized origin
echo "[BUG-03] CORS preflight from unauthorized origin (evil.example.com)..."
CORS_EVIL_RESP=$(curl -s -I -X OPTIONS \
    -H "Origin: http://evil.example.com" \
    -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: Authorization,Content-Type" \
    "$BASE_URL/auth/login" 2>/dev/null || true)

echo "  Response headers:"
echo "$CORS_EVIL_RESP" | head -10

TEST_ID=$((TEST_ID + 1))
if echo "$CORS_EVIL_RESP" | grep -qi "Access-Control-Allow-Origin: http://evil.example.com"; then
    echo "  [FAIL] Test $TEST_ID: CORS allows unauthorized origin evil.example.com"
    FAIL=$((FAIL + 1))
else
    echo "  [PASS] Test $TEST_ID: CORS blocks unauthorized origin evil.example.com"
    PASS=$((PASS + 1))
fi
echo ""

# Test CORS for allowed origin (localhost:5173 via application.yml default)
echo "[BUG-03] CORS preflight from allowed origin (localhost:5173)..."
CORS_GOOD_RESP=$(curl -s -I -X OPTIONS \
    -H "Origin: http://localhost:5173" \
    -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: Authorization,Content-Type" \
    "$BASE_URL/auth/login" 2>/dev/null || true)

echo "  Response headers:"
echo "$CORS_GOOD_RESP" | head -10

TEST_ID=$((TEST_ID + 1))
if echo "$CORS_GOOD_RESP" | grep -qi "Access-Control-Allow-Origin: http://localhost:5173"; then
    echo "  [PASS] Test $TEST_ID: CORS allows localhost:5173"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: CORS does NOT allow localhost:5173"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- Summary ---
print_summary
