#!/bin/bash
# 05-03: Emission Ratings - View/Create/Recalculate + Rankings + AI Predict
# Requirements: EMIT-01, EMIT-02, EMIT-03
#
# Required seed data:
#   - Users: admin, enterprise001 (password from TEST_PASSWORD env, default: admin123)
#   - MySQL accessible with DB_USER/DB_PASS env vars
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

source "$(dirname "$0")/test-helpers.sh"

check_dependencies

echo "=== 05-03: Emission Ratings (EMIT-01~03) ==="
echo ""

# --- Authentication (WR-01: validate tokens) ---
echo "[1/6] Authenticating..."

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")
echo "  admin token: ${TOKEN_ADMIN:0:20}..."

# Get enterprise001's enterprise ID (WR-03: use extract_field)
RESP_E1_LOGIN=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1_LOGIN" "enterprise001")

RESP_SCORE=$(curl -s $CURL_OPTS "$BASE_URL/credit/my-score" \
    -H "Authorization: Bearer $TOKEN_E1")
E1_ENTERPRISE_ID=$(extract_field "$RESP_SCORE" "data.enterpriseId")
echo "  enterprise001 enterpriseId: $E1_ENTERPRISE_ID"
echo ""

# Validate extracted ID is a positive integer (CR-02)
validate_integer "E1_ENTERPRISE_ID" "$E1_ENTERPRISE_ID" || exit 1

# --- EMIT-01: View emission ratings ---
echo "[2/6] EMIT-01: View emission ratings for enterprise001..."

RESP_RATINGS=$(curl -s $CURL_OPTS "$BASE_URL/emission/ratings/$E1_ENTERPRISE_ID" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Ratings response: $(echo "$RESP_RATINGS" | head -c 400)"

assert_code_200 "Get ratings returns 200" "$RESP_RATINGS"
echo ""

# --- EMIT-01: Create/recalculate rating ---
echo "[3/6] EMIT-01: Create emission rating..."

RESP_CREATE=$(curl -s $CURL_OPTS -X POST "$BASE_URL/emission/ratings" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d "{\"enterpriseId\":$E1_ENTERPRISE_ID,\"year\":\"2025\",\"totalEmission\":1500.50,\"revenue\":50000,\"ratedBy\":1}")
echo "  Create rating response: $(echo "$RESP_CREATE" | head -c 400)"

assert_code_200 "Create rating returns 200" "$RESP_CREATE"
assert_contains "Rating has ratingLevel" "$RESP_CREATE" '"ratingLevel":'
assert_contains "Rating has totalEmission" "$RESP_CREATE" '"totalEmission":'
echo ""

# --- DB verification (CR-01/WR-07: use run_mysql, show errors) ---
echo "[4/6] DB check: emission_rating table..."

DB_COUNT=$(run_mysql "SELECT COUNT(*) FROM emission_rating WHERE enterprise_id=$E1_ENTERPRISE_ID")
if [[ "$DB_COUNT" == MYSQL_ERROR:* ]]; then
    echo "  [ERROR] MySQL query failed: ${DB_COUNT#MYSQL_ERROR:}"
    DB_COUNT="0"
elif ! [[ "$DB_COUNT" =~ ^[0-9]+$ ]]; then
    echo "  [ERROR] Unexpected DB response: $DB_COUNT"
    DB_COUNT="0"
fi

TEST_ID=$((TEST_ID + 1))
if [ "$DB_COUNT" -gt 0 ]; then
    echo "  [PASS] Test $TEST_ID: emission_rating has records for enterprise001 (count=$DB_COUNT)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: emission_rating has NO records for enterprise001"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- EMIT-02: Industry rankings ---
echo "[5/6] EMIT-02: Industry rankings for 2025..."

RESP_RANKINGS=$(curl -s $CURL_OPTS "$BASE_URL/emission/rankings/2025" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
echo "  Rankings response: $(echo "$RESP_RANKINGS" | head -c 400)"

assert_code_200 "Rankings returns 200" "$RESP_RANKINGS"
assert_contains "Rankings has list data" "$RESP_RANKINGS" '"data":'
echo ""

# --- EMIT-03: AI Prediction ---
echo "[6/6] EMIT-03: AI emission prediction..."

RESP_PREDICT=$(curl -s $CURL_OPTS -X POST "$BASE_URL/emission/predict" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d "{\"enterpriseId\":$E1_ENTERPRISE_ID,\"predictMonths\":6}")
echo "  Predict response: $(echo "$RESP_PREDICT" | head -c 400)"

assert_code_200 "Prediction returns 200" "$RESP_PREDICT"
assert_contains "Prediction has data" "$RESP_PREDICT" '"data":'
echo ""

# --- Summary ---
print_summary
