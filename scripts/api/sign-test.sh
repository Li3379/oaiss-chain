#!/bin/bash
# 05-01: Digital Signatures - RSA Keypair + Sign + Verify + Revoke
# Requirements: SIGN-01, SIGN-02, SIGN-03
#
# Required seed data:
#   - Users: admin, enterprise001 (password from TEST_PASSWORD env, default: admin123)
#   - MySQL accessible with DB_USER/DB_PASS env vars
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

source "$(dirname "$0")/test-helpers.sh"

check_dependencies

echo "=== 05-01: Digital Signatures (SIGN-01~03) ==="
echo ""

# --- Authentication (WR-01: validate tokens) ---
echo "[1/7] Authenticating..."

RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1" "enterprise001")

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")

echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo "  admin token: ${TOKEN_ADMIN:0:20}..."

# Get enterprise001's userId (WR-03: use extract_field)
E1_USER_ID=$(extract_field "$RESP_E1" "data.userId")
echo "  enterprise001 userId: $E1_USER_ID"
echo ""

# Validate extracted ID is a positive integer (CR-02)
validate_integer "E1_USER_ID" "$E1_USER_ID" || exit 1

# --- Cleanup any pre-existing keypairs ---
echo "[pre] Cleanup: Removing old keypairs for enterprise001..."
run_mysql "DELETE FROM rsa_key_pair WHERE user_id=$E1_USER_ID" >/dev/null 2>&1 || true
echo ""

# --- SIGN-01: RSA Keypair Generation ---
echo "[2/7] SIGN-01: Generate RSA keypair..."

RESP_GEN=$(curl -s $CURL_OPTS -X POST "$BASE_URL/signature/keypair/generate" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Generate response: $(echo "$RESP_GEN" | head -c 300)"

assert_code_200 "Keypair generation returns 200" "$RESP_GEN"
assert_contains "Keypair has publicKey" "$RESP_GEN" '"publicKey":'
assert_contains "Keypair has id" "$RESP_GEN" '"id":'

KEY_ID=$(extract_field "$RESP_GEN" "id")
echo "  Generated keypair id: $KEY_ID"
echo ""

# --- Get keypair info ---
echo "[3/7] SIGN-01: Get keypair info..."

RESP_GET=$(curl -s $CURL_OPTS "$BASE_URL/signature/keypair" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Get keypair response: $(echo "$RESP_GET" | head -c 300)"

assert_code_200 "Get keypair returns 200" "$RESP_GET"
assert_contains "Get keypair has publicKey" "$RESP_GET" '"publicKey":'
assert_not_contains "Get keypair does NOT expose privateKey" "$RESP_GET" '"privateKey":'
echo ""

# --- DB check: rsa_key_pairs table (CR-01: use run_mysql) ---
echo "[4/7] DB check: rsa_key_pairs for enterprise001..."

DB_CHECK=$(run_mysql "SELECT COUNT(*) FROM rsa_key_pair WHERE user_id=$E1_USER_ID")
if [[ "$DB_CHECK" =~ ^[0-9]+$ ]] && [ "$DB_CHECK" -gt 0 ]; then
    TEST_ID=$((TEST_ID + 1))
    echo "  [PASS] Test $TEST_ID: rsa_key_pairs has record for enterprise001 (count=$DB_CHECK)"
    PASS=$((PASS + 1))
else
    TEST_ID=$((TEST_ID + 1))
    echo "  [FAIL] Test $TEST_ID: rsa_key_pairs has NO record for enterprise001 (got: $DB_CHECK)"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- SIGN-02: Sign report data ---
echo "[5/7] SIGN-02: Sign report data..."

REPORT_DATA='{"reportName":"test-report-2025","totalEmission":1500.50,"period":"2025-Q1"}'
RESP_SIGN=$(curl -s $CURL_OPTS -X POST "$BASE_URL/signature/sign" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d "$REPORT_DATA")
echo "  Sign response: $(echo "$RESP_SIGN" | head -c 300)"

assert_code_200 "Sign returns 200" "$RESP_SIGN"
assert_contains "Sign has signature data" "$RESP_SIGN" '"signature":'

SIGNATURE=$(extract_field "$RESP_SIGN" "data.signature")
echo "  Signature length: ${#SIGNATURE}"
echo ""

# --- SIGN-03: Verify signature (valid) ---
echo "[6/7] SIGN-03: Verify signature (valid + tampered)..."

# Verify with correct data
REPORT_DATA_STR=$(echo "$REPORT_DATA" | sed 's/"/\\"/g')
VERIFY_BODY="{\"reportId\":1,\"signatureData\":\"$SIGNATURE\",\"reportData\":\"$REPORT_DATA_STR\",\"signerId\":$E1_USER_ID}"
RESP_VERIFY=$(curl -s $CURL_OPTS -X POST "$BASE_URL/signature/verify" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d "$VERIFY_BODY")
echo "  Verify (valid) response: $(echo "$RESP_VERIFY" | head -c 300)"

assert_code_200 "Verify valid signature returns 200" "$RESP_VERIFY"
assert_contains "Valid signature returns true" "$RESP_VERIFY" '"valid":true'

# Verify with tampered data
TAMPERED_BODY="{\"reportId\":1,\"signatureData\":\"$SIGNATURE\",\"reportData\":\"{\\\"reportName\\\":\\\"TAMPERED\\\",\\\"totalEmission\\\":99999}\",\"signerId\":$E1_USER_ID}"
RESP_TAMPERED=$(curl -s $CURL_OPTS -X POST "$BASE_URL/signature/verify" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d "$TAMPERED_BODY")
echo "  Verify (tampered) response: $(echo "$RESP_TAMPERED" | head -c 300)"

assert_code_200 "Tampered data verification returns 200" "$RESP_TAMPERED"
assert_contains "Tampered data returns false" "$RESP_TAMPERED" '"valid":false'
echo ""

# --- Cleanup: Revoke keypair (WR-06: don't count cleanup as test) ---
echo "[7/7] Cleanup: Revoke keypair..."

RESP_REVOKE=$(curl -s -X DELETE "$BASE_URL/signature/keypair" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Revoke response: $(echo "$RESP_REVOKE" | head -c 300)"

if echo "$RESP_REVOKE" | grep -qF '"code":200'; then
    echo "  [OK] Keypair revoked"
else
    echo "  [WARN] Keypair revoke failed (may need manual cleanup): $RESP_REVOKE"
fi
echo ""

# --- Summary ---
print_summary
