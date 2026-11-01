#!/bin/bash
# Enterprise Management - Admission, Info, Quota, Contact
# Covers: EnterpriseController [/enterprise/*]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

echo "=== Enterprise Management Test ==="
echo ""

# --- Authentication ---
echo "[1/5] Authenticating..."
RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1" "enterprise001")

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")

echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- Enterprise info ---
echo "[2/5] Get enterprise info..."
RESP_INFO=$(curl -s $CURL_OPTS "$BASE_URL/enterprise/info" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "Enterprise info returns 200" "$RESP_INFO"
assert_contains "Enterprise info has data" "$RESP_INFO" '"data":'
echo ""

# --- Enterprise admission ---
echo "[3/5] Get enterprise admission status..."
RESP_ADMISSION=$(curl -s $CURL_OPTS "$BASE_URL/enterprise/admission/my" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "Enterprise admission returns 200" "$RESP_ADMISSION"
echo ""

# --- Enterprise quota ---
echo "[4/5] Get enterprise quota..."
RESP_QUOTA=$(curl -s $CURL_OPTS "$BASE_URL/enterprise/quota" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "Enterprise quota returns 200" "$RESP_QUOTA"
echo ""

# --- Update contact ---
echo "[5/5] Update enterprise contact..."
RESP_CONTACT=$(curl -s $CURL_OPTS -X PUT "$BASE_URL/enterprise/contact" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"contactPerson":"Test Manager","contactPhone":"13900139000"}')
assert_code_200 "Update contact returns 200" "$RESP_CONTACT"
echo ""

# --- Admin: view enterprise by ID ---
echo "[extra] Admin view enterprise by ID..."
RESP_ADMIN_VIEW=$(curl -s $CURL_OPTS "$BASE_URL/enterprise/2" -H "Authorization: Bearer $TOKEN_ADMIN")
assert_code_200 "Admin view enterprise returns 200" "$RESP_ADMIN_VIEW"
echo ""

print_summary