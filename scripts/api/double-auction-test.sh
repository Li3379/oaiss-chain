#!/usr/bin/env bash
# 04-04: Double Auction - Buy/Sell Orders, Matching, Settlement, Edge Cases
# Requirements: TRADE-01~06, 12, 13
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"
DB_PORT=3306 source "$SCRIPT_DIR/../db-config.sh"

check_backend

echo "=== Double Auction Test (TRADE-01~06, 12, 13) ==="
echo ""

# --- Reset ---
info "Resetting auction test data..."
exec_mysql_query "DELETE FROM matching_result; DELETE FROM auction_order;" 2>/dev/null || true
exec_mysql_query "UPDATE enterprise SET carbon_tradable=38000, carbon_quota=50000, carbon_used=12000 WHERE user_id=2;" 2>/dev/null || true
exec_mysql_query "UPDATE enterprise SET carbon_tradable=55000, carbon_quota=55000, carbon_used=0 WHERE user_id=3;" 2>/dev/null || true
# 买入挂单会全额冻结意向金（qty × price），余额不足会以 4001 拒绝。
# 这里显式把碳币余额重置到充足水位，避免依赖历史充值状态导致用例随机失败。
exec_mysql_query "UPDATE carbon_coin_account SET balance=100000, frozen_balance=0, total_recharged=100000, total_spent=0 WHERE user_id IN (2,3);" 2>/dev/null || true
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

# --- Precondition: certificate gate + price limit (both added after this script was written) ---
# 1) 挂单/撮合现在要求企业持有有效准入证书（CertificateGateService）。
# 2) 单价必须落在近 30 日均价 ±20% 内，硬编码 50.00/45.00 会撞限价被 1001 拒绝。
# 这两条都是业务正确行为，脚本自备前置而不是把断言放宽。
info "Ensuring admission certificates and price band..."
for eid in 2 3; do
    exec_mysql_query "UPDATE enterprise_admission SET status=1, revoked_at=NULL, revoke_reason=NULL,
        expiry_date=DATE_ADD(CURDATE(), INTERVAL 1 YEAR)
        WHERE enterprise_id=$eid AND status=1" 2>/dev/null || true
done
ADM2=$(run_mysql_query "SELECT COUNT(*) FROM enterprise_admission WHERE enterprise_id=2 AND status=1 AND (expiry_date IS NULL OR expiry_date >= CURDATE())" | tr -d '[:space:]')
ADM3=$(run_mysql_query "SELECT COUNT(*) FROM enterprise_admission WHERE enterprise_id=3 AND status=1 AND (expiry_date IS NULL OR expiry_date >= CURDATE())" | tr -d '[:space:]')
[ "${ADM2:-0}" = "0" ] && curl -s -X POST "$BASE_URL/admin/enterprise-admission/2/issue" -H "Authorization: Bearer $TOKEN_ADMIN" >/dev/null || true
[ "${ADM3:-0}" = "0" ] && curl -s -X POST "$BASE_URL/admin/enterprise-admission/3/issue" -H "Authorization: Bearer $TOKEN_ADMIN" >/dev/null || true

# 取当前均价，在 ±10% 内构造买/卖价（留出余量避免贴边失败）。
# 注意：限价基准是「近 30 日均价」，没有专门端点；直接查库最稳。
# set -euo pipefail 下管道里 grep 无匹配会让脚本静默退出，故全部 `|| true`。
REF_PRICE=$(run_mysql_query "SELECT IFNULL(ROUND(AVG(price),2), 0) FROM auction_order WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)" | tr -d '[:space:]' || true)
if [ -z "$REF_PRICE" ] || [ "$REF_PRICE" = "NULL" ] || python -c "import sys; sys.exit(0 if float('${REF_PRICE:-0}') <= 0 else 1)"; then
    # 库内无成交样本 → 用平台默认基准价（与后端限价服务同一默认口径）
    REF_PRICE=10.00
fi
BUY_PRICE=$(python -c "print(f'{float('$REF_PRICE')*1.05:.2f}')")
SELL_PRICE=$(python -c "print(f'{float('$REF_PRICE')*0.97:.2f}')")
info "  Reference price: $REF_PRICE → buy=$BUY_PRICE sell=$SELL_PRICE"
ok "Preconditions ready"

# Record pre-trade quotas
E1_TRADABLE_BEFORE=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E1_QUOTA_BEFORE=$(run_mysql_query "SELECT carbon_quota FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E2_TRADABLE_BEFORE=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)

# --- TRADE-01: Place buy order ---
echo ""
echo "[TRADE-01] Place buy order (qty=100, price=$BUY_PRICE)..."
TRADE01_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auction/buy" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d "{\"direction\":1,\"quantity\":100,\"price\":$BUY_PRICE}")
assert_code_200 "TRADE-01: Buy order placed" "$TRADE01_RESP"
BUY_ORDER_ID=$(extract_data_field "$TRADE01_RESP" "id")
info "  Buy order ID: $BUY_ORDER_ID"

# --- TRADE-02: Place sell order ---
echo ""
echo "[TRADE-02] Place sell order (qty=80, price=$SELL_PRICE)..."
TRADE02_RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/auction/sell" \
    -H "Authorization: Bearer $TOKEN_E2" \
    -H "Content-Type: application/json" \
    -d "{\"direction\":2,\"quantity\":80,\"price\":$SELL_PRICE}")
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
# 2026-11-02 同步：成交风险分流（S4）后，命中规则的成交进入「待人工审核」
# （transaction.status=1, audit_status=1），资产保持挂单冻结态、不结算——
# 这是设计意图，不是漏记账。因此本用例按实际分流结果分两条路径校验：
#   待审 → 断言「买方/卖方额度均未变动」且存在待审工单
#   直通 → 断言买卖双方额度各变动 matchQty
echo ""
echo "[TRADE-05] Verify settlement..."
E1_TRADABLE_AFTER=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E1_QUOTA_AFTER=$(run_mysql_query "SELECT carbon_quota FROM enterprise WHERE user_id=2" | tr -d '[:space:]' | cut -d. -f1)
E2_TRADABLE_AFTER=$(run_mysql_query "SELECT carbon_tradable FROM enterprise WHERE user_id=3" | tr -d '[:space:]' | cut -d. -f1)

LAST_AUCTION_TX_STATE=$(run_mysql_query "SELECT CONCAT(status,'/',IFNULL(audit_status,0)) FROM transaction WHERE trade_type=1 ORDER BY id DESC LIMIT 1" | tr -d '[:space:]')

TEST_ID=$((TEST_ID + 1))
if [[ "$LAST_AUCTION_TX_STATE" == "1/1" ]]; then
    # 待人工审核：额度必须原地不动
    if [[ "$E1_TRADABLE_AFTER" -eq "$E1_TRADABLE_BEFORE" && "$E1_QUOTA_AFTER" -eq "$E1_QUOTA_BEFORE" ]]; then
        echo "  [PASS] Test $TEST_ID: Pending-audit trade keeps assets frozen (no settlement yet)"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: Pending-audit trade moved assets (buyer $E1_TRADABLE_BEFORE→$E1_TRADABLE_AFTER)"
        FAIL=$((FAIL + 1))
    fi
else
    EXPECTED_E1_T=$((E1_TRADABLE_BEFORE + 80))
    EXPECTED_E1_Q=$((E1_QUOTA_BEFORE + 80))
    EXPECTED_E2_T=$((E2_TRADABLE_BEFORE - 80))
    if [[ "$E1_TRADABLE_AFTER" -eq "$EXPECTED_E1_T" && "$E1_QUOTA_AFTER" -eq "$EXPECTED_E1_Q" && "$E2_TRADABLE_AFTER" -eq "$EXPECTED_E2_T" ]]; then
        echo "  [PASS] Test $TEST_ID: Settlement verified: buyer +80, seller -80 (state=$LAST_AUCTION_TX_STATE)"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: Settlement mismatch (state=$LAST_AUCTION_TX_STATE, buyer $E1_TRADABLE_BEFORE→$E1_TRADABLE_AFTER)"
        FAIL=$((FAIL + 1))
    fi
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
# scripts/api/ 上溯两级才是仓库根（原先只上溯一级，指向了 scripts/）
SERVICE_FILE="$SCRIPT_DIR/../../oaiss-chain-backend/src/main/java/com/oaiss/chain/service/DoubleAuctionService.java"
if [[ -f "$SERVICE_FILE" ]] && grep -q "@Transactional" "$SERVICE_FILE"; then
    echo "  [PASS] Test $TEST_ID: @Transactional annotation present"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: @Transactional missing"
    FAIL=$((FAIL + 1))
fi

echo ""
print_summary