#!/usr/bin/env bash
# 04-04: Double Auction - Buy/Sell Orders, Matching, Settlement, Edge Cases
# Requirements: TRADE-01~06, 12, 13
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"
DB_PORT=3306 source "$SCRIPT_DIR/db-config.sh"

check_backend

echo "=== Double Auction Test (TRADE-01~06, 12, 13) ==="
echo ""

# --- Reset ---
info "Resetting auction test data..."
exec_mysql_query "DELETE FROM matching_result; DELETE FROM auction_order;" 2>/dev/null || true
exec_mysql_query "UPDATE enterprise SET carbon_tradable=38000, carbon_quota=50000, carbon_used=12000 WHERE user_id=2;" 2>/dev/null || true
exec_mysql_query "UPDATE enterprise SET carbon_tradable=55000, carbon_quota=55000, carbon_used=0 WHERE user_id=3;" 2>/dev/null || true
ok "Auction data reset"

# --- Setup ---
info "Logging in as enterprise001 (buyer)..."
TOKEN_E1=$(login "enterprise001") || { fail "Cannot proceed"; exit 1; }
ok "enterprise001 logged in"

info "Logging in as enterprise002 (seller)..."
TOKEN_E2=$(login "enterprise002") || { fail "Cannot proceed"; exit 1; }
ok "enterprise002 logged in"

info "Logging in as admin..."
TOKEN_ADMIN=$(login "admin") || { fail "Cannot proceed"; exit 1; }
ok "admin logged in"

# Record pre-trade quotas
E1_TRADABLE_BEFORE=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E1_QUOTA_BEFORE=$(run_mysql_query "SELECT carbon_quota FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E2_TRADABLE_BEFORE=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)

# --- TRADE-01: Place buy order ---
echo ""
echo "[TRADE-01] Place buy order (qty=100, price=50.00)..."
TRADE01_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auction/buy" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"direction":1,"quantity":100,"price":50.00}')
assert_code_200 "TRADE-01: Buy order placed" "$TRADE01_RESP"
BUY_ORDER_ID=$(extract_data_field "$TRADE01_RESP" "id")
info "  Buy order ID: $BUY_ORDER_ID"

# --- TRADE-02: Place sell order ---
echo ""
echo "[TRADE-02] Place sell order (qty=80, price=45.00)..."
TRADE02_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auction/sell" \
    -H "Authorization: Bearer $TOKEN_E2" \
    -H "Content-Type: application/json" \
    -d '{"direction":2,"quantity":80,"price":45.00}')
assert_code_200 "TRADE-02: Sell order placed" "$TRADE02_RESP"
SELL_ORDER_ID=$(extract_data_field "$TRADE02_RESP" "id")
info "  Sell order ID: $SELL_ORDER_ID"

# --- TRADE-03: View orders ---
echo ""
echo "[TRADE-03] View orders..."
ORDERS_RESP=$(curl -s $CURL_OPTS "$BASE_URL/auction/orders?page=1&size=10" -H "Authorization: Bearer $TOKEN_ADMIN")
assert_code_200 "TRADE-03: View all orders" "$ORDERS_RESP"
assert_contains "TRADE-03: Orders has content" "$ORDERS_RESP" '"content":'

MY_ORDERS=$(curl -s $CURL_OPTS "$BASE_URL/auction/my-orders" -H "Authorization: Bearer $TOKEN_E1")
assert_code_200 "TRADE-03: View my orders" "$MY_ORDERS"

# --- TRADE-04: Trigger matching ---
echo ""
echo "[TRADE-04] Trigger matching..."
MATCH_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auction/match" -H "Authorization: Bearer $TOKEN_ADMIN")
assert_code_200 "TRADE-04: Match trigger" "$MATCH_RESP"
assert_contains "TRADE-04: Match has results" "$MATCH_RESP" '"data":'

# --- TRADE-05: Verify settlement ---
echo ""
echo "[TRADE-05] Verify settlement..."
E1_TRADABLE_AFTER=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E1_QUOTA_AFTER=$(run_mysql_query "SELECT carbon_quota FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E2_TRADABLE_AFTER=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)

EXPECTED_E1_T=$((E1_TRADABLE_BEFORE + 80))
EXPECTED_E1_Q=$((E1_QUOTA_BEFORE + 80))
EXPECTED_E2_T=$((E2_TRADABLE_BEFORE - 80))

TEST_ID=$((TEST_ID + 1))
if [[ "$E1_TRADABLE_AFTER" -eq "$EXPECTED_E1_T" && "$E1_QUOTA_AFTER" -eq "$EXPECTED_E1_Q" && "$E2_TRADABLE_AFTER" -eq "$EXPECTED_E2_T" ]]; then
    echo "  [PASS] Test $TEST_ID: Settlement verified: buyer +80, seller -80"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Settlement mismatch"
    FAIL=$((FAIL + 1))
fi

# --- TRADE-06: Order status transitions ---
echo ""
echo "[TRADE-06] Order status transitions..."
E1_ORDERS=$(curl -s $CURL_OPTS "$BASE_URL/auction/my-orders" -H "Authorization: Bearer $TOKEN_E1")
E2_ORDERS=$(curl -s $CURL_OPTS "$BASE_URL/auction/my-orders" -H "Authorization: Bearer $TOKEN_E2")

TEST_ID=$((TEST_ID + 1))
BUY_STATUS=$(echo "$E1_ORDERS" | grep -o "\"id\":$BUY_ORDER_ID[^}]*" 2>/dev/null | grep -o '"status":[0-9]*' | head -1 | cut -d: -f2)
SELL_STATUS=$(echo "$E2_ORDERS" | grep -o "\"id\":$SELL_ORDER_ID[^}]*" 2>/dev/null | grep -o '"status":[0-9]*' | head -1 | cut -d: -f2)
if [[ "$BUY_STATUS" == "1" && "$SELL_STATUS" == "2" ]]; then
    echo "  [PASS] Test $TEST_ID: Buy=PARTIALLY_MATCHED(1), Sell=FULLY_MATCHED(2)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Status mismatch: buy=$BUY_STATUS, sell=$SELL_STATUS"
    FAIL=$((FAIL + 1))
fi

# --- TRADE-12: Insufficient quota ---
echo ""
echo "[TRADE-12] Insufficient quota order..."
TRADE12_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auction/buy" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"direction":1,"quantity":999999,"price":10.00}')
TEST_ID=$((TEST_ID + 1))
TRADE12_CODE=$(extract_field "$TRADE12_RESP" "code")
if [[ "$TRADE12_CODE" != "200" ]]; then
    echo "  [PASS] Test $TEST_ID: Insufficient quota rejected (code=$TRADE12_CODE)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Should have been rejected"
    FAIL=$((FAIL + 1))
fi

# --- TRADE-13: @Transactional check ---
echo ""
echo "[TRADE-13] DoubleAuctionService @Transactional..."
TEST_ID=$((TEST_ID + 1))
SERVICE_FILE="$SCRIPT_DIR/../oaiss-chain-backend/src/main/java/com/oaiss/chain/service/DoubleAuctionService.java"
if [[ -f "$SERVICE_FILE" ]] && grep -q "@Transactional" "$SERVICE_FILE"; then
    echo "  [PASS] Test $TEST_ID: @Transactional annotation present"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: @Transactional missing"
    FAIL=$((FAIL + 1))
fi

echo ""
print_summary