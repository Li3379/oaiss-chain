#!/bin/bash
# AI Market Prediction - Trend, Price, Supply/Demand
# Covers: MarketPredictionController [/ai/market/*]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

echo "=== AI Market Prediction Test ==="
echo ""

# --- Authentication ---
echo "[1/5] Authenticating..."
RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- Market trend prediction ---
echo "[2/5] Market trend prediction..."
RESP_TREND=$(curl -s $CURL_OPTS -X POST "$BASE_URL/ai/market/trend" \
    -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json" \
    -d '{"period":"2025-Q1","region":"north_china"}')
assert_code_200 "Market trend prediction returns 200" "$RESP_TREND"
assert_contains "Trend has data" "$RESP_TREND" '"data":'
echo ""

# --- Market price prediction ---
echo "[3/5] Market price prediction..."
RESP_PRICE=$(curl -s $CURL_OPTS -X POST "$BASE_URL/ai/market/price" \
    -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json" \
    -d '{"commodity":"carbon_credit","forecastMonths":3}')
assert_code_200 "Market price prediction returns 200" "$RESP_PRICE"
assert_contains "Price prediction has data" "$RESP_PRICE" '"data":'
echo ""

# --- Supply-demand prediction ---
echo "[4/5] Supply-demand prediction..."
RESP_SD=$(curl -s $CURL_OPTS -X POST "$BASE_URL/ai/market/supply-demand" \
    -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json" \
    -d '{"market":"regional","forecastMonths":6}')
assert_code_200 "Supply-demand prediction returns 200" "$RESP_SD"
assert_contains "Supply-demand has data" "$RESP_SD" '"data":'
echo ""

# --- Enterprise inference ---
echo "[5/5] Enterprise inference..."
RESP_INF=$(curl -s $CURL_OPTS "$BASE_URL/predict/enterprise/2/inference" -H "Authorization: Bearer $TOKEN_ADMIN")
assert_code_200 "Enterprise inference returns 200" "$RESP_INF"
echo ""

print_summary