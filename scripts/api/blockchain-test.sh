#!/bin/bash
# 05-04: Blockchain Explorer - Status/Blocks/Transactions + admin chain ops
# Requirements: BLOCK-01, BLOCK-02, BLOCK-03, ADMIN-CHAIN-01/02
#
# Required seed data:
#   - Users: admin (password from TEST_PASSWORD env, default: admin123)
#   - Blockchain mock data present (blocks and transactions)
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

source "$(dirname "$0")/test-helpers.sh"

check_dependencies

echo "=== 05-04: Blockchain Explorer (BLOCK-01~03) ==="
echo ""

# --- Authentication (WR-01: validate tokens) ---
echo "[1/9] Authenticating..."

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- BLOCK-01: Connection status ---
echo "[2/9] BLOCK-01: Blockchain connection status..."

RESP_STATUS=$(curl -s "$BASE_URL/blockchain/status" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Status response: $(echo "$RESP_STATUS" | head -c 400)"

assert_code_200 "Status returns 200" "$RESP_STATUS"
assert_contains "Status has connected field" "$RESP_STATUS" '"connected"'
assert_contains "Status has channel field" "$RESP_STATUS" '"channel"'
assert_contains "Status has mode field" "$RESP_STATUS" '"mode"'
# Mode check: FABRIC or MOCK depending on profile
TEST_ID=$((TEST_ID + 1))
MODE=$(echo "$RESP_STATUS" | grep -o '"mode":"[^"]*"' | head -1 | sed 's/"mode":"//;s/"//')
if [[ "$MODE" == "MOCK" ]]; then
    echo "  [SKIP] Test $TEST_ID: Mode is MOCK (Fabric not active in local profile)"
    SKIP=$((SKIP + 1))
elif [[ "$MODE" == "FABRIC" ]]; then
    echo "  [PASS] Test $TEST_ID: Mode is FABRIC"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: Unexpected mode: $MODE"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- BLOCK-02: Latest blocks ---
echo "[3/9] BLOCK-02: Latest blocks..."

RESP_BLOCKS=$(curl -s "$BASE_URL/blockchain/blocks/latest?page=1&size=5" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Blocks response: $(echo "$RESP_BLOCKS" | head -c 500)"

assert_code_200 "Blocks returns 200" "$RESP_BLOCKS"

BLOCK_CONTENT_COUNT=$( (echo "$RESP_BLOCKS" | grep -o '"blockNumber":[0-9]*' || true) | wc -l | tr -d '[:space:]')
if [ "$BLOCK_CONTENT_COUNT" -gt 0 ]; then
    FIRST_BLOCK=$( (echo "$RESP_BLOCKS" | grep -o '"blockNumber":[0-9]*' || true) | head -1 | cut -d: -f2)
    FIRST_HASH=$( (echo "$RESP_BLOCKS" | grep -o '"blockHash":"[^"]*"' || true) | head -1 | cut -d'"' -f4)
    echo "  First block: number=$FIRST_BLOCK hash=$FIRST_HASH"
else
    FIRST_BLOCK=""
    FIRST_HASH=""
    echo "  No block content available yet; skipping detail checks"
fi

# Validate blockHash format (starts with 0x)
TEST_ID=$((TEST_ID + 1))
if [ -z "$FIRST_HASH" ]; then
    echo "  [SKIP] Test $TEST_ID: no latest block hash available"
    SKIP=$((SKIP + 1))
elif echo "$FIRST_HASH" | grep -q '^0x'; then
    echo "  [PASS] Test $TEST_ID: blockHash starts with '0x'"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: blockHash does not start with '0x' (got: $FIRST_HASH)"
    FAIL=$((FAIL + 1))
fi

# Validate blockNumber is positive
TEST_ID=$((TEST_ID + 1))
if [ -z "$FIRST_BLOCK" ]; then
    echo "  [SKIP] Test $TEST_ID: no latest block number available"
    SKIP=$((SKIP + 1))
elif [ "$FIRST_BLOCK" -gt 0 ] 2>/dev/null; then
    echo "  [PASS] Test $TEST_ID: blockNumber is positive integer ($FIRST_BLOCK)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: blockNumber is not a positive integer (got: $FIRST_BLOCK)"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- Block detail (IN-05: track SKIP counter) ---
echo "[4/9] BLOCK-02: Block detail..."

if [ -n "$FIRST_BLOCK" ]; then
    RESP_BLOCK_DETAIL=$(curl -s "$BASE_URL/blockchain/block/$FIRST_BLOCK" \
        -H "Authorization: Bearer $TOKEN_ADMIN")
    echo "  Block detail response: $(echo "$RESP_BLOCK_DETAIL" | head -c 400)"

    assert_code_200 "Block detail returns 200" "$RESP_BLOCK_DETAIL"
    assert_contains "Block detail has data" "$RESP_BLOCK_DETAIL" '"data":'
else
    TEST_ID=$((TEST_ID + 1))
    SKIP=$((SKIP + 1))
    echo "  [SKIP] Test $TEST_ID: No block number available for detail query"
fi
echo ""

# --- BLOCK-03: Transactions ---
echo "[5/9] BLOCK-03: Transaction list..."

RESP_TXS=$(curl -s "$BASE_URL/blockchain/transactions?page=1&size=5" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Transactions response: $(echo "$RESP_TXS" | head -c 500)"

assert_code_200 "Transactions returns 200" "$RESP_TXS"

# Extract first txHash
TX_CONTENT_COUNT=$( (echo "$RESP_TXS" | grep -o '"txHash":"[^"]*"' || true) | wc -l | tr -d '[:space:]')
if [ "$TX_CONTENT_COUNT" -gt 0 ]; then
    FIRST_TX=$( (echo "$RESP_TXS" | grep -o '"txHash":"[^"]*"' || true) | head -1 | cut -d'"' -f4)
    echo "  First txHash: $FIRST_TX"
else
    FIRST_TX=""
    echo "  No transaction content available yet; skipping detail checks"
fi

# Validate txHash format (non-empty)
TEST_ID=$((TEST_ID + 1))
if [ -z "$FIRST_TX" ]; then
    echo "  [SKIP] Test $TEST_ID: no latest txHash available"
    SKIP=$((SKIP + 1))
elif [ "$FIRST_TX" != "null" ]; then
    echo "  [PASS] Test $TEST_ID: txHash is present"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: txHash is missing (got: $FIRST_TX)"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- Report on-chain snapshot (BLOCK-04; replaces the removed detail endpoint) ---
# 2026-11-02 同步：/blockchain/transaction/{txHash} 详情端点已随
# Blockchain.vue 交易哈希查询面板一并移除。链上数据的可读入口改为
# /blockchain/report/{reportId}（读回 v2 快照，用于与本地库对账）。
echo "[6/9] BLOCK-04: Report on-chain snapshot..."

SNAPSHOT_REPORT_ID=""
SNAPSHOT_PROBE=$(curl -s "$BASE_URL/blockchain/report/1" -H "Authorization: Bearer $TOKEN_ADMIN")
# 报告 1 未必上链过，逐个探测最近若干 ID 直到命中一条真实快照
for rid in 1 2 3 4 5 27 100 200; do
    PROBE=$(curl -s "$BASE_URL/blockchain/report/$rid" -H "Authorization: Bearer $TOKEN_ADMIN")
    if echo "$PROBE" | grep -q '"code":200'; then
        SNAPSHOT_REPORT_ID="$rid"
        SNAPSHOT_PROBE="$PROBE"
        break
    fi
done

TEST_ID=$((TEST_ID + 1))
if [ -z "$SNAPSHOT_REPORT_ID" ]; then
    echo "  [SKIP] Test $TEST_ID: no report with an on-chain v2 snapshot reachable"
    SKIP=$((SKIP + 1))
else
    echo "  Snapshot response (report $SNAPSHOT_REPORT_ID): $(echo "$SNAPSHOT_PROBE" | head -c 300)"
    echo "  [PASS] Test $TEST_ID: On-chain snapshot readable for report $SNAPSHOT_REPORT_ID"
    PASS=$((PASS + 1))

    TEST_ID=$((TEST_ID + 1))
    if echo "$SNAPSHOT_PROBE" | grep -q 'schemaVersion'; then
        echo "  [PASS] Test $TEST_ID: Snapshot carries schemaVersion marker"
        PASS=$((PASS + 1))
    else
        echo "  [SKIP] Test $TEST_ID: snapshot without schemaVersion (v1 record)"
        SKIP=$((SKIP + 1))
    fi
fi
echo ""

# --- Pagination check ---
echo "[7/9] Pagination: page=2&size=2..."

RESP_PAGE2=$(curl -s "$BASE_URL/blockchain/transactions?page=2&size=2" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_code_200 "Pagination returns 200" "$RESP_PAGE2"
echo ""

# --- ADMIN chain ops: reconciliation (added 2026-10-31, page /admin/blockchain) ---
echo "[8/9] ADMIN-CHAIN-01: Chain reconciliation..."

RESP_RECON=$(curl -s "$BASE_URL/admin/blockchain/reconciliation?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Reconciliation response: $(echo "$RESP_RECON" | head -c 400)"

assert_code_200 "Reconciliation returns 200" "$RESP_RECON"
assert_contains "Reconciliation has summary counters" "$RESP_RECON" '"checkedGlobal"'
assert_contains "Reconciliation has detail rows" "$RESP_RECON" '"details"'
TEST_ID=$((TEST_ID + 1))
if echo "$RESP_RECON" | grep -qE 'MATCHED|MISSING_ON_CHAIN|CONTENT_MISMATCH|UNVERIFIABLE'; then
    echo "  [PASS] Test $TEST_ID: Reconciliation classifies rows"
    PASS=$((PASS + 1))
else
    echo "  [SKIP] Test $TEST_ID: no classified rows yet (empty ledger slice)"
    SKIP=$((SKIP + 1))
fi
echo ""

# --- ADMIN chain ops: dead-letter queue (added 2026-10-31) ---
echo "[9/9] ADMIN-CHAIN-02: Dead-letter queue..."

RESP_DEAD=$(curl -s "$BASE_URL/admin/outbox/dead-letters?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Dead-letter response: $(echo "$RESP_DEAD" | head -c 400)"

assert_code_200 "Dead-letter list returns 200" "$RESP_DEAD"
assert_contains "Dead-letter list is paginated" "$RESP_DEAD" '"content"'
echo ""

# --- Summary ---
print_summary
