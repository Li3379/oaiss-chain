#!/usr/bin/env bash
# 04-03: Carbon Coin - Balance, Transactions, Transfer, Insufficient Balance
# Requirements: COIN-01~05
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

check_backend

echo "=== Carbon Coin Test (COIN-01~05) ==="
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

# --- COIN-03: Transfer coins ---
echo ""
echo "[COIN-03] Transfer 500 coins E1->E2..."
BALANCE_E1_BEFORE=$(extract_field "$(curl -s $CURL_OPTS "$BASE_URL/carbon-coin/account" -H "Authorization: Bearer $TOKEN_E1")" "data.balance" | cut -d. -f1)
BALANCE_E2_BEFORE=$(extract_field "$(curl -s $CURL_OPTS "$BASE_URL/carbon-coin/account?userId=3" -H "Authorization: Bearer $TOKEN_ADMIN")" "data.balance" | cut -d. -f1)

TRANSFER_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-coin/transfer" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"counterpartId":3,"amount":500,"remark":"Phase3 test transfer"}')
assert_code_200 "COIN-03: Transfer succeeds" "$TRANSFER_RESP"

# --- COIN-04: Verify atomic balance update ---
echo ""
echo "[COIN-04] Verify atomic balance updates..."
BALANCE_E1_AFTER=$(extract_field "$(curl -s $CURL_OPTS "$BASE_URL/carbon-coin/account" -H "Authorization: Bearer $TOKEN_E1")" "data.balance" | cut -d. -f1)
BALANCE_E2_AFTER=$(extract_field "$(curl -s $CURL_OPTS "$BASE_URL/carbon-coin/account?userId=3" -H "Authorization: Bearer $TOKEN_ADMIN")" "data.balance" | cut -d. -f1)

EXPECTED_E1=$((BALANCE_E1_BEFORE - 500))
EXPECTED_E2=$((BALANCE_E2_BEFORE + 500))

TEST_ID=$((TEST_ID + 1))
if [[ "$BALANCE_E1_AFTER" -eq "$EXPECTED_E1" && "$BALANCE_E2_AFTER" -eq "$EXPECTED_E2" ]]; then
    echo "  [PASS] Test $TEST_ID: Atomic update verified: E1=$BALANCE_E1_AFTER, E2=$BALANCE_E2_AFTER"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Balance mismatch: E1=$BALANCE_E1_AFTER (exp $EXPECTED_E1), E2=$BALANCE_E2_AFTER (exp $EXPECTED_E2)"
    FAIL=$((FAIL + 1))
fi

# --- COIN-05: Insufficient balance rejected ---
echo ""
echo "[COIN-05] Insufficient balance transfer..."
INSUFF_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-coin/transfer" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"counterpartId":3,"amount":999999,"remark":"should fail"}')
INSUFF_CODE=$(extract_field "$INSUFF_RESP" "code")
TEST_ID=$((TEST_ID + 1))
if [[ "$INSUFF_CODE" != "200" ]]; then
    echo "  [PASS] Test $TEST_ID: Insufficient balance correctly rejected (code=$INSUFF_CODE)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Insufficient balance should have been rejected"
    FAIL=$((FAIL + 1))
fi

echo ""
print_summary