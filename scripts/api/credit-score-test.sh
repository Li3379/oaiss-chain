#!/bin/bash
# 04-02: Credit Score + Level Evaluation + Trade Restrictions
# Requirements: CRED-01~05

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

echo "=== 04-02: Credit Score + Level Evaluation + Trade Restrictions ==="
echo ""

# --- Compute expected level from score ---
expected_level() {
    local score=$1
    if [ "$score" -ge 80 ]; then echo "EXCELLENT"
    elif [ "$score" -ge 60 ]; then echo "GOOD"
    elif [ "$score" -ge 40 ]; then echo "WARNING"
    elif [ "$score" -ge 20 ]; then echo "DANGER"
    else echo "FROZEN"
    fi
}

# --- Authentication ---
echo "[1/5] Authenticating..."

RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1" "enterprise001")

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")

echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- CRED-01: View credit score and history ---
echo "[2/5] CRED-01: View credit score + history..."

RESP_SCORE=$(curl -s $CURL_OPTS "$BASE_URL/credit/my-score" \
    -H "Authorization: Bearer $TOKEN_E1")
INITIAL_SCORE=$(extract_field "$RESP_SCORE" "data.score")
INITIAL_LEVEL=$(extract_field "$RESP_SCORE" "data.level")
assert_contains "View my credit score" "$RESP_SCORE" '"score":'
assert_contains "Credit level present" "$RESP_SCORE" '"level":'
echo "  Initial score: $INITIAL_SCORE, level: $INITIAL_LEVEL"

RESP_HISTORY=$(curl -s $CURL_OPTS "$BASE_URL/credit/history?page=1&size=20" \
    -H "Authorization: Bearer $TOKEN_E1")
assert_contains "View credit history (paginated)" "$RESP_HISTORY" '"content":'

E1_ENTERPRISE_ID=$(extract_field "$RESP_SCORE" "data.enterpriseId")
echo "  Enterprise ID: $E1_ENTERPRISE_ID"
echo ""

# --- CRED-02: Level transitions ---
echo "[3/5] CRED-02: Credit level transitions..."

deduct_verify() {
    local event_type=$1 desc=$2
    RESP=$(curl -s $CURL_OPTS -X POST "$BASE_URL/credit/deduct" \
        -H "Authorization: Bearer $TOKEN_ADMIN" \
        -H "Content-Type: application/json" \
        -d "{\"enterpriseId\": $E1_ENTERPRISE_ID, \"eventType\": $event_type, \"description\": \"$desc\"}")

    local score=$(extract_field "$RESP" "data.score")
    local level=$(extract_field "$RESP" "data.level")
    local restricted=$(extract_field "$RESP" "data.tradeRestricted")
    local frozen=$(extract_field "$RESP" "data.accountFrozen")
    local exp_level=$(expected_level "$score")
    local exp_restricted="false"
    local exp_frozen="false"
    [ "$score" -lt 40 ] && exp_restricted="true"
    [ "$score" -lt 20 ] && exp_frozen="true"

    echo "  -> score=$score level=$level restricted=$restricted frozen=$frozen"

    TEST_ID=$((TEST_ID + 1))
    if [ "$level" = "$exp_level" ]; then
        echo "  [PASS] Test $TEST_ID: Level=$level correct for score=$score"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: Expected level=$exp_level got=$level at score=$score"
        FAIL=$((FAIL + 1))
    fi

    TEST_ID=$((TEST_ID + 1))
    if [ "$restricted" = "$exp_restricted" ]; then
        echo "  [PASS] Test $TEST_ID: tradeRestricted=$restricted correct"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: Expected tradeRestricted=$exp_restricted got=$restricted"
        FAIL=$((FAIL + 1))
    fi

    TEST_ID=$((TEST_ID + 1))
    if [ "$frozen" = "$exp_frozen" ]; then
        echo "  [PASS] Test $TEST_ID: accountFrozen=$frozen correct"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] Test $TEST_ID: Expected accountFrozen=$exp_frozen got=$frozen"
        FAIL=$((FAIL + 1))
    fi

    CURRENT_SCORE=$score
}

CURRENT_SCORE=$INITIAL_SCORE

echo "  Deducting to WARNING zone (score < 60)..."
while [ "$CURRENT_SCORE" -ge 60 ]; do
    deduct_verify 3 "Deduction toward WARNING"
done

echo "  Deducting to DANGER zone (score < 40, trade restriction activates)..."
while [ "$CURRENT_SCORE" -ge 40 ]; do
    deduct_verify 3 "Deduction toward DANGER"
done

echo "  Deducting to FROZEN zone (score < 20, account freezes)..."
while [ "$CURRENT_SCORE" -ge 20 ]; do
    deduct_verify 3 "Deduction toward FROZEN"
done

RESP_FINAL=$(curl -s $CURL_OPTS "$BASE_URL/credit/$E1_ENTERPRISE_ID" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Final state: account frozen" "$RESP_FINAL" '"accountFrozen":true'
echo ""

# --- CRED-03/04: Trade restriction + frozen verification ---
echo "[4/5] CRED-03/04: Trade permission + frozen verification..."

RESP_PERM=$(curl -s $CURL_OPTS "$BASE_URL/credit/check-permission/$E1_ENTERPRISE_ID" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Trade permission=false when score<40" "$RESP_PERM" '"data":false'

RESP_FROZEN=$(curl -s $CURL_OPTS "$BASE_URL/credit/frozen" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Enterprise in frozen list" "$RESP_FROZEN" "$E1_ENTERPRISE_ID"

RESP_RESTRICTED=$(curl -s $CURL_OPTS "$BASE_URL/credit/restricted" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Enterprise in restricted list" "$RESP_RESTRICTED" "$E1_ENTERPRISE_ID"
echo ""

# --- CRED-05: Admin bonus recovery ---
echo "[5/5] CRED-05: Admin bonus recovery + re-evaluate..."

RESP_BONUS=$(curl -s $CURL_OPTS -X POST "$BASE_URL/credit/bonus?enterpriseId=$E1_ENTERPRISE_ID&points=100&description=Score%20recovery%20test" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Bonus restores score above 80" "$RESP_BONUS" '"level":"EXCELLENT"'
assert_contains "Trade restriction cleared" "$RESP_BONUS" '"tradeRestricted":false'
assert_contains "Account unfrozen" "$RESP_BONUS" '"accountFrozen":false'

RESP_PERM_AFTER=$(curl -s $CURL_OPTS "$BASE_URL/credit/check-permission/$E1_ENTERPRISE_ID" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Trade permission restored=true" "$RESP_PERM_AFTER" '"data":true'

RESP_EVAL=$(curl -s $CURL_OPTS -X POST "$BASE_URL/credit/evaluate/$E1_ENTERPRISE_ID" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_contains "Re-evaluation confirms EXCELLENT" "$RESP_EVAL" '"level":"EXCELLENT"'
echo ""

# --- Summary ---
print_summary