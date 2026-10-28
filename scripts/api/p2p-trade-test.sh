#!/usr/bin/env bash
# 04-04: P2P Trade - Create, Confirm, Verify Settlement, Cancel
# Requirements: TRADE-07~11
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"
DB_PORT=3306 source "$SCRIPT_DIR/db-config.sh"

check_backend

echo "=== P2P Trade Test (TRADE-07~11) ==="
echo ""

# --- Setup ---
info "Logging in as enterprise001 (seller)..."
TOKEN_E1=$(login "enterprise001") || { fail "Cannot proceed"; exit 1; }
ok "enterprise001 logged in"

info "Logging in as enterprise002 (buyer)..."
TOKEN_E2=$(login "enterprise002") || { fail "Cannot proceed"; exit 1; }
ok "enterprise002 logged in"

info "Logging in as admin..."
TOKEN_ADMIN=$(login "admin") || { fail "Cannot proceed"; exit 1; }
ok "admin logged in"

# --- Reset state ---
info "Resetting P2P trade state..."
exec_mysql_query "DELETE FROM transaction WHERE trade_type=2" 2>/dev/null || true
exec_mysql_query "UPDATE enterprise SET carbon_tradable=38000, carbon_quota=50000, carbon_used=12000 WHERE user_id=2" 2>/dev/null || true
exec_mysql_query "UPDATE enterprise SET carbon_tradable=55000, carbon_quota=55000, carbon_used=0 WHERE user_id=3" 2>/dev/null || true
ok "P2P trade state reset"

# Record pre-trade quotas
SELLER_Q_BEFORE=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
BUYER_T_BEFORE=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)
BUYER_Q_BEFORE=$(run_mysql_query "SELECT carbon_quota FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)
info "Before: seller=$SELLER_Q_BEFORE, buyer_t=$BUYER_T_BEFORE, buyer_q=$BUYER_Q_BEFORE"

# --- TRADE-07: Create P2P trade ---
echo ""
echo "[TRADE-07] Create P2P trade (qty=50, price=10.00)..."
TRADE07_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/trade/p2p" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"tradeType":2,"sellerId":2,"buyerId":3,"quantity":50,"unitPrice":10.00,"remark":"P2P test trade 1"}')
assert_code_200 "TRADE-07: P2P trade created" "$TRADE07_RESP"
P2P_ID=$(extract_data_field "$TRADE07_RESP" "id")
P2P_STATUS=$(extract_data_field "$TRADE07_RESP" "status")
assert_contains "TRADE-07: Status is PENDING" "$TRADE07_RESP" '"status":0'
info "  Trade ID: $P2P_ID"

# --- TRADE-08: Confirm P2P trade ---
echo ""
echo "[TRADE-08] Confirm P2P trade..."
TRADE08_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/trade/$P2P_ID/confirm" \
    -H "Authorization: Bearer $TOKEN_E2")
assert_code_200 "TRADE-08: P2P confirm succeeds" "$TRADE08_RESP"
assert_contains "TRADE-08: Status COMPLETED" "$TRADE08_RESP" '"status":2'

# --- TRADE-10: Verify settlement ---
echo ""
echo "[TRADE-10] Verify settlement after confirmation..."
SELLER_Q_AFTER=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
BUYER_T_AFTER=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)
BUYER_Q_AFTER=$(run_mysql_query "SELECT carbon_quota FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)
info "After: seller=$SELLER_Q_AFTER, buyer_t=$BUYER_T_AFTER, buyer_q=$BUYER_Q_AFTER"

EXPECTED_S=$((SELLER_Q_BEFORE - 50))
EXPECTED_BT=$((BUYER_T_BEFORE + 50))
EXPECTED_BQ=$((BUYER_Q_BEFORE + 50))

TEST_ID=$((TEST_ID + 1))
if [[ "$SELLER_Q_AFTER" -eq "$EXPECTED_S" && "$BUYER_T_AFTER" -eq "$EXPECTED_BT" && "$BUYER_Q_AFTER" -eq "$EXPECTED_BQ" ]]; then
    echo "  [PASS] Test $TEST_ID: Settlement verified: seller -50, buyer +50/+50"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Settlement mismatch"
    FAIL=$((FAIL + 1))
fi

# Verify in my-trades
MY_TRADES=$(curl -s $CURL_OPTS "$BASE_URL/trade/my-trades?page=1&size=10" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "TRADE-10: my-trades returns 200" "$MY_TRADES"

# --- TRADE-09: Cancel P2P trade ---
echo ""
echo "[TRADE-09] Cancel P2P trade..."
TRADE09_CREATE=$(curl -s $CURL_OPTS -X POST "$BASE_URL/trade/p2p" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"tradeType":2,"sellerId":2,"buyerId":3,"quantity":30,"unitPrice":8.00,"remark":"to cancel"}')
P2P2_ID=$(extract_data_field "$TRADE09_CREATE" "id")
info "  Second trade ID: $P2P2_ID"

CANCEL_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/trade/$P2P2_ID/cancel" \
    -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "TRADE-09: Cancel succeeds" "$CANCEL_RESP"
assert_contains "TRADE-09: Status CANCELLED" "$CANCEL_RESP" '"status":3'

echo ""
print_summary