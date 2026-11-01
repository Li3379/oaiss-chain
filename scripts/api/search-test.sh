#!/bin/bash
# 05-07: Cross-Entity Search - Reports/Trades/MarketOverview
# Requirements: SRCH-01
#
# Required seed data:
#   - Users: admin, enterprise001 (password from TEST_PASSWORD env, default: admin123)
#   - Carbon reports and trades data present
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

source "$(dirname "$0")/test-helpers.sh"

check_dependencies

echo "=== 05-07: Cross-Entity Search (SRCH-01) ==="
echo ""

# --- Authentication (WR-01: validate tokens) ---
echo "[1/7] Authenticating..."

RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1" "enterprise001")

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")

echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- SRCH-01: Search reports ---
echo "[2/7] SRCH-01: Search carbon reports..."

RESP_REPORTS=$(curl -s "$BASE_URL/search/reports?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Reports search response: $(echo "$RESP_REPORTS" | head -c 400)"

assert_code_200 "Search reports returns 200" "$RESP_REPORTS"
echo ""

# --- Filter by keyword ---
echo "[3/7] SRCH-01: Search reports by keyword..."

RESP_KW=$(curl -s "$BASE_URL/search/reports?keyword=%E6%8A%A5%E5%91%8A&page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Keyword search response: $(echo "$RESP_KW" | head -c 300)"

assert_code_200 "Keyword search returns 200" "$RESP_KW"
echo ""

# --- Filter by status ---
echo "[4/7] SRCH-01: Search reports by status..."

RESP_STATUS=$(curl -s "$BASE_URL/search/reports?status=2&page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Status filter response: $(echo "$RESP_STATUS" | head -c 300)"

assert_code_200 "Status filter returns 200" "$RESP_STATUS"
echo ""

# --- Search trades ---
echo "[5/7] SRCH-01: Search trades..."

RESP_TRADES=$(curl -s "$BASE_URL/search/trades?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Trades search response: $(echo "$RESP_TRADES" | head -c 400)"

assert_code_200 "Search trades returns 200" "$RESP_TRADES"
echo ""

# --- Filter trades by type ---
echo "[6/7] SRCH-01: Search trades by type..."

RESP_TYPE=$(curl -s "$BASE_URL/search/trades?tradeType=1&page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Type filter response: $(echo "$RESP_TYPE" | head -c 300)"

assert_code_200 "Trade type filter returns 200" "$RESP_TYPE"
echo ""

# --- Market overview ---
echo "[7/7] SRCH-01: Market overview..."

RESP_MARKET=$(curl -s "$BASE_URL/search/market-overview" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Market overview response: $(echo "$RESP_MARKET" | head -c 400)"

assert_code_200 "Market overview returns 200" "$RESP_MARKET"
assert_contains "Market overview has data" "$RESP_MARKET" '"data":'
echo ""

# --- Cross-entity: admin can see all data ---
echo "[extra] Cross-entity: Admin search (all enterprises)..."

RESP_ADMIN_SEARCH=$(curl -s "$BASE_URL/search/reports?page=1&size=20" \
    -H "Authorization: Bearer $TOKEN_ADMIN")
assert_code_200 "Admin search returns 200" "$RESP_ADMIN_SEARCH"
echo ""

# --- Summary ---
print_summary
