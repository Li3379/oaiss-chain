#!/usr/bin/env bash
# 04-03: Carbon Coin - Balance, Transactions (transfer feature removed 2026-10-31)
# Requirements: COIN-01~03 (transfer feature removed 2026-10-31)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

check_backend

echo "=== Carbon Coin Test (COIN-01~03) ==="
echo ""

# --- Setup: Login ---
info "Logging in as enterprise001..."
TOKEN_E1=$(login "enterprise001") || { fail "Cannot proceed without enterprise001 token"; exit 1; }
ok "enterprise001 logged in"

info "Logging in as enterprise002..."
TOKEN_E2=$(login "enterprise002") || { fail "Cannot proceed without enterprise002 token"; exit 1; }
ok "enterprise002 logged in"

info "Logging in as admin..."
TOKEN_ADMIN=$(login "admin") || { fail "Cannot proceed without admin token"; exit 1; }
ok "admin logged in"

# Reset balances via DB
if command -v mysql >/dev/null 2>&1 || command -v docker >/dev/null 2>&1; then
    info "Resetting carbon coin balances to seed values..."
    run_mysql "UPDATE carbon_coin_account SET balance=10000, total_recharged=10000, total_spent=0 WHERE user_id IN (2,3)" >/dev/null 2>&1 || true
    ok "Carbon coin balances reset"
fi

# --- COIN-01: View balance ---
echo ""
echo "[COIN-01] View carbon coin balance..."
COIN01_RESP=$(curl -s $CURL_OPTS "$BASE_URL/carbon-coin/account" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "COIN-01: Get balance returns 200" "$COIN01_RESP"
BALANCE=$(extract_field "$COIN01_RESP" "data.balance")
TEST_ID=$((TEST_ID + 1))
if [[ -n "$BALANCE" ]]; then
    echo "  [PASS] Test $TEST_ID: Balance retrieved: $BALANCE"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Balance field missing"
    FAIL=$((FAIL + 1))
fi

# --- COIN-02: Transaction history ---
echo ""
echo "[COIN-02] View transaction history..."
COIN02_RESP=$(curl -s $CURL_OPTS "$BASE_URL/carbon-coin/transactions?page=1&size=10" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "COIN-02: Get transactions returns 200" "$COIN02_RESP"
assert_contains "COIN-02: Has pagination content" "$COIN02_RESP" '"content":'

# --- COIN-03: Transfer endpoint removed (feature removed 2026-10-31) ---
echo ""
echo "[COIN-03] Transfer endpoint removed check..."
TRANSFER_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-coin/transfer" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"counterpartId":3,"amount":500,"remark":"transfer feature removed"}')
TRANSFER_CODE=$(extract_field "$TRANSFER_RESP" "code")
TEST_ID=$((TEST_ID + 1))
if [[ "$TRANSFER_CODE" == "404" ]]; then
    echo "  [PASS] Test $TEST_ID: Transfer endpoint removed (code=404)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Transfer endpoint should be removed (code=$TRANSFER_CODE)"
    FAIL=$((FAIL + 1))
fi

print_summary