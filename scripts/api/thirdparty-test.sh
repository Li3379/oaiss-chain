#!/bin/bash
# 05-06: Third-Party Monitoring - OrgInfo/Statistics/CarbonReports/Contact
# Requirements: TP-01, TP-02 (partial)
#
# Required seed data:
#   - Users: thirdparty001 (password from TEST_PASSWORD env, default: admin123)
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

source "$(dirname "$0")/test-helpers.sh"

check_dependencies

echo "=== 05-06: Third-Party Monitoring (TP-01~02) ==="
echo ""

# --- Authentication (WR-01: validate tokens) ---
echo "[1/6] Authenticating..."

RESP_TP=$(login_user "thirdparty001")
TOKEN_TP=$(extract_token "$RESP_TP" "thirdparty001")
echo "  thirdparty001 token: ${TOKEN_TP:0:20}..."
echo ""

# --- TP-01: Org info ---
echo "[2/6] TP-01: Get org info..."

RESP_ORG=$(curl -s "$BASE_URL/third-party/org-info" \
    -H "Authorization: Bearer $TOKEN_TP")
echo "  Org info response: $(echo "$RESP_ORG" | head -c 400)"

assert_code_200 "Org info returns 200" "$RESP_ORG"
assert_contains "Org info has data" "$RESP_ORG" '"data":'
echo ""

# --- TP-01: Statistics ---
echo "[3/6] TP-01: Get monitoring statistics..."

RESP_STATS=$(curl -s "$BASE_URL/third-party/statistics" \
    -H "Authorization: Bearer $TOKEN_TP")
echo "  Statistics response: $(echo "$RESP_STATS" | head -c 400)"

assert_code_200 "Statistics returns 200" "$RESP_STATS"
assert_contains "Statistics has data" "$RESP_STATS" '"data":'
echo ""

# --- TP-01: Carbon reports (monitoring perspective) ---
echo "[4/6] TP-01/TP-02: Query carbon reports..."

RESP_REPORTS=$(curl -s "$BASE_URL/third-party/carbon-reports?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_TP")
echo "  Reports response: $(echo "$RESP_REPORTS" | head -c 400)"

assert_code_200 "Carbon reports returns 200" "$RESP_REPORTS"
echo ""

# --- Filter by status ---
echo "[5/6] TP-01: Filter carbon reports by status=2..."

RESP_FILTERED=$(curl -s "$BASE_URL/third-party/carbon-reports?status=2&page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_TP")
echo "  Filtered response: $(echo "$RESP_FILTERED" | head -c 400)"

assert_code_200 "Filtered reports returns 200" "$RESP_FILTERED"
echo ""

# --- Update contact ---
echo "[6/6] TP-01: Update contact info..."

RESP_CONTACT=$(curl -s -X PUT "$BASE_URL/third-party/contact?contactPerson=test_auditor&contactPhone=13800138000" \
    -H "Authorization: Bearer $TOKEN_TP")
echo "  Contact update response: $(echo "$RESP_CONTACT" | head -c 300)"

assert_code_200 "Update contact returns 200" "$RESP_CONTACT"
echo ""

# --- Gap notice ---
echo "========================================"
echo "  TP-02 (Trade Audit): PARTIAL COVERAGE"
echo "  - /carbon-reports used as proxy for monitoring"
echo "  - No dedicated trade audit endpoint exists"
echo "========================================"

# --- Summary ---
print_summary
