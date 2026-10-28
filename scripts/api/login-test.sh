#!/usr/bin/env bash
# Login & Authentication Test
# Tests: login for all 6 roles, token validation, logout, blacklist
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

check_backend

echo "=== Login & Authentication Test ==="
echo ""

ACCOUNTS=(
  "admin:${TEST_PASSWORD}:4:ADMIN"
  "enterprise001:${TEST_PASSWORD}:1:ENTERPRISE"
  "enterprise002:${TEST_PASSWORD}:1:ENTERPRISE"
  "enterprise003:${TEST_PASSWORD}:1:ENTERPRISE"
  "reviewer001:${TEST_PASSWORD}:2:REVIEWER"
  "thirdparty001:${TEST_PASSWORD}:3:THIRD_PARTY"
)

TOTAL=0
PASSED=0
FAILED=0

for entry in "${ACCOUNTS[@]}"; do
  IFS=':' read -r username password expected_type expected_role <<< "$entry"
  TOTAL=$((TOTAL + 1))

  echo ""
  info "Testing account: $username (expected userType=$expected_type, role=$expected_role)"

  # Step 1: Login
  LOGIN_RESP=$(login_user "$username" "$password")
  LOGIN_CODE=$(extract_field "$LOGIN_RESP" "code")
  if [[ "$LOGIN_CODE" != "200" ]]; then
    fail "$username: Login failed (code=$LOGIN_CODE)"
    FAILED=$((FAILED + 1))
    continue
  fi

  TOKEN=$(extract_field "$LOGIN_RESP" "data.accessToken")
  ACTUAL_TYPE=$(extract_field "$LOGIN_RESP" "data.userType")
  if [[ -z "$TOKEN" ]]; then
    fail "$username: No accessToken in response"
    FAILED=$((FAILED + 1))
    continue
  fi
  ok "$username: Login successful (token received, userType=$ACTUAL_TYPE)"

  # Step 2: Verify userType
  if [[ "$ACTUAL_TYPE" != "$expected_type" ]]; then
    fail "$username: userType mismatch (expected=$expected_type, got=$ACTUAL_TYPE)"
    FAILED=$((FAILED + 1))
    continue
  fi
  ok "$username: userType matches expected ($expected_type)"

  # Step 3: Access protected endpoint
  ME_RESP=$(curl -s $CURL_OPTS "$BASE_URL/auth/me" -H "Authorization: Bearer $TOKEN")
  ME_CODE=$(extract_field "$ME_RESP" "code")
  if [[ "$ME_CODE" != "200" ]]; then
    fail "$username: /auth/me failed (code=$ME_CODE)"
    FAILED=$((FAILED + 1))
    continue
  fi
  ok "$username: Bearer token works on /auth/me"

  # Step 4: Logout
  LOGOUT_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auth/logout" -H "Authorization: Bearer $TOKEN")
  LOGOUT_CODE=$(extract_field "$LOGOUT_RESP" "code")
  if [[ "$LOGOUT_CODE" != "200" ]]; then
    fail "$username: Logout failed (code=$LOGOUT_CODE)"
    FAILED=$((FAILED + 1))
    continue
  fi
  ok "$username: Logout successful"

  # Step 5: Verify token is blacklisted
  BLACKLIST_RESP=$(curl -s $CURL_OPTS "$BASE_URL/auth/me" -H "Authorization: Bearer $TOKEN")
  BLACKLIST_CODE=$(extract_field "$BLACKLIST_RESP" "code")
  if [[ "$BLACKLIST_CODE" == "200" ]]; then
    fail "$username: Token still valid after logout (blacklist not working)"
    FAILED=$((FAILED + 1))
    continue
  fi
  ok "$username: Token blacklisted after logout (code=$BLACKLIST_CODE)"

  PASSED=$((PASSED + 1))
done

echo ""
echo -e "========================================"
echo -e " Login Test Results"
echo -e "========================================"
echo -e " Total:  $TOTAL"
echo -e " Passed: ${GREEN}$PASSED${NC}"
if [[ $FAILED -gt 0 ]]; then
  echo -e " Failed: ${RED}$FAILED${NC}"
else
  echo -e " Failed: 0"
fi
echo -e "========================================"

if [[ $FAILED -gt 0 ]]; then
  exit 1
fi