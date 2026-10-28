#!/bin/bash
# User Profile - View, Update, Password, Username/Email Check
# Covers: UserController [/user/*]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

echo "=== User Profile Test ==="
echo ""

# --- Authentication ---
echo "[1/7] Authenticating..."
RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1" "enterprise001")
echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo ""

# --- Get profile ---
echo "[2/7] Get user profile..."
RESP_PROFILE=$(curl -s $CURL_OPTS "$BASE_URL/user/profile" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "Get profile returns 200" "$RESP_PROFILE"
assert_contains "Profile has data" "$RESP_PROFILE" '"data":'
echo ""

# --- Get user by ID ---
USER_ID=$(extract_field "$RESP_E1" "data.userId")
echo "[3/7] Get user by ID ($USER_ID)..."
RESP_USER=$(curl -s $CURL_OPTS "$BASE_URL/user/$USER_ID" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "Get user by ID returns 200" "$RESP_USER"
echo ""

# --- Update profile ---
echo "[4/7] Update user profile..."
RESP_UPDATE=$(curl -s $CURL_OPTS -X PUT "$BASE_URL/user/profile" \
    -H "Authorization: Bearer $TOKEN_E1" -H "Content-Type: application/json" \
    -d '{"nickname":"Test User Updated"}')
assert_code_200 "Update profile returns 200" "$RESP_UPDATE"
echo ""

# --- Check username ---
echo "[5/7] Check username availability..."
RESP_CHECK_USER=$(curl -s $CURL_OPTS "$BASE_URL/user/check-username?username=nonexistent_user_999" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "Check username returns 200" "$RESP_CHECK_USER"
echo ""

# --- Check email ---
echo "[6/7] Check email availability..."
RESP_CHECK_EMAIL=$(curl -s $CURL_OPTS "$BASE_URL/user/check-email?email=test999@example.com" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "Check email returns 200" "$RESP_CHECK_EMAIL"
echo ""

# --- Change password (negative test) ---
echo "[7/7] Change password (wrong old password)..."
RESP_PWD=$(curl -s $CURL_OPTS -X PUT "$BASE_URL/user/password" \
    -H "Authorization: Bearer $TOKEN_E1" -H "Content-Type: application/json" \
    -d '{"oldPassword":"wrongpassword","newPassword":"newpass123"}')
TEST_ID=$((TEST_ID + 1))
PWD_CODE=$(extract_field "$RESP_PWD" "code")
if [[ "$PWD_CODE" != "200" ]]; then
    echo "  [PASS] Test $TEST_ID: Wrong old password correctly rejected (code=$PWD_CODE)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Should have rejected wrong password"
    FAIL=$((FAIL + 1))
fi
echo ""

print_summary