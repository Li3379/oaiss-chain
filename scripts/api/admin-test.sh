#!/bin/bash
# 05-05: Admin Management - List/Filter Users, Status Toggle, Dashboard, Statistics
# Requirements: ADMIN-01, ADMIN-02 (gap), ADMIN-03 (gap), ADMIN-04, ADMIN-05
#
# Required seed data:
#   - Users: admin, enterprise001 (password from TEST_PASSWORD env, default: admin123)
#   - enterprise001 must have userType=1 (ENTERPRISE)
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

source "$(dirname "$0")/test-helpers.sh"

check_dependencies

echo "=== 05-05: Admin Management (ADMIN-01~05) ==="
echo ""

# --- Authentication (WR-01: validate tokens) ---
echo "[1/8] Authenticating..."

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- ADMIN-01: List users ---
echo "[2/8] ADMIN-01: List users (paginated)..."

RESP_USERS=$(curl -s "$BASE_URL/admin/users?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Users response: $(echo "$RESP_USERS" | head -c 400)"

assert_code_200 "List users returns 200" "$RESP_USERS"
assert_contains "Users has content" "$RESP_USERS" '"content":'

# Extract enterprise001's userId using jq if available (IN-03: replace inline python)
E1_USER_ID=""
if command -v jq >/dev/null 2>&1; then
    E1_USER_ID=$(echo "$RESP_USERS" | jq -r '.data.content[] | select(.userType == 1) | .id' 2>/dev/null | head -1)
fi
# Fallback: use filtered endpoint to get enterprise user id
if [ -z "$E1_USER_ID" ]; then
    RESP_E1=$(curl -s "$BASE_URL/admin/users?userType=1&page=1&size=1" -H "Authorization: Bearer $TOKEN_ADMIN")
    E1_USER_ID=$(echo "$RESP_E1" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://' | tr -d ' ')
fi
echo "  enterprise001 userId: $E1_USER_ID"
echo ""

# --- Filter by userType ---
echo "[3/8] ADMIN-01: Filter users by enterprise type..."

RESP_FILTER=$(curl -s "$BASE_URL/admin/users?userType=1&page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Filter response: $(echo "$RESP_FILTER" | head -c 300)"

assert_code_200 "Filter by type returns 200" "$RESP_FILTER"
assert_contains "Filter has content" "$RESP_FILTER" '"content":'
echo ""

# --- ADMIN-04: Disable user ---
echo "[4/8] ADMIN-04: Disable enterprise001..."

RESP_DISABLE=$(curl -s -X PUT "$BASE_URL/admin/users/$E1_USER_ID/status?status=0" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Disable response: $(echo "$RESP_DISABLE" | head -c 300)"

assert_code_200 "Disable user returns 200" "$RESP_DISABLE"
echo ""

# --- Verify disabled ---
echo "[5/8] ADMIN-04: Verify user is disabled..."

RESP_DISABLED=$(curl -s "$BASE_URL/admin/users?status=0&page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Disabled user appears in status=0 filter" "$RESP_DISABLED" '"id"'
echo ""

# --- ADMIN-04: Re-enable user ---
echo "[6/8] ADMIN-04: Re-enable enterprise001..."

RESP_ENABLE=$(curl -s -X PUT "$BASE_URL/admin/users/$E1_USER_ID/status?status=1" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Enable response: $(echo "$RESP_ENABLE" | head -c 300)"

assert_code_200 "Re-enable user returns 200" "$RESP_ENABLE"
echo ""

# --- ADMIN-05: Dashboard ---
echo "[7/8] ADMIN-05: Dashboard data..."

RESP_DASH=$(curl -s "$BASE_URL/admin/dashboard" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Dashboard response: $RESP_DASH"

assert_code_200 "Dashboard returns 200" "$RESP_DASH"
assert_contains "Dashboard has totalUsers" "$RESP_DASH" '"totalUsers":'
echo ""

# --- ADMIN-05: Statistics ---
echo "[8/8] ADMIN-05: Statistics..."

RESP_STATS=$(curl -s "$BASE_URL/admin/statistics" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Statistics response: $RESP_STATS"

assert_code_200 "Statistics returns 200" "$RESP_STATS"
assert_contains "Statistics has enterpriseCount" "$RESP_STATS" '"enterpriseCount":'
assert_contains "Statistics has reviewerCount" "$RESP_STATS" '"reviewerCount":'
assert_contains "Statistics has thirdPartyCount" "$RESP_STATS" '"thirdPartyCount":'
echo ""

# --- Known code gaps ---
echo "========================================"
echo "  KNOWN CODE GAPS:"
echo "  ADMIN-02 (Create User): No backend endpoint exists"
echo "  ADMIN-03 (Edit User): No backend endpoint exists"
echo "========================================"

# --- Summary ---
print_summary
