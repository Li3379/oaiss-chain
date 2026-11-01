#!/bin/bash
# Reviewer Management - Qualification, Info, Pending Reports, History, Statistics
# Covers: ReviewerController [/reviewer/*]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

echo "=== Reviewer Management Test ==="
echo ""

# --- Authentication ---
echo "[1/6] Authenticating..."
RESP_REV=$(login_user "reviewer001")
TOKEN_REV=$(extract_token "$RESP_REV" "reviewer001")

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")

echo "  reviewer001 token: ${TOKEN_REV:0:20}..."
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- Reviewer qualification ---
echo "[2/6] Get reviewer qualification..."
RESP_QUAL=$(curl -s $CURL_OPTS "$BASE_URL/reviewer/qualification/my" -H "Authorization: Bearer $TOKEN_REV")
assert_code_200 "Reviewer qualification returns 200" "$RESP_QUAL"
echo ""

# --- Reviewer info ---
echo "[3/6] Get reviewer info..."
RESP_INFO=$(curl -s $CURL_OPTS "$BASE_URL/reviewer/info" -H "Authorization: Bearer $TOKEN_REV")
assert_code_200 "Reviewer info returns 200" "$RESP_INFO"
echo ""

# --- Pending reports ---
echo "[4/6] Get pending reports for review..."
RESP_PENDING=$(curl -s $CURL_OPTS "$BASE_URL/reviewer/reports/pending?page=1&size=10" -H "Authorization: Bearer $TOKEN_REV")
assert_code_200 "Pending reports returns 200" "$RESP_PENDING"
assert_contains "Pending reports has content" "$RESP_PENDING" '"content":'
echo ""

# --- Review history ---
echo "[5/6] Get review history..."
RESP_HISTORY=$(curl -s $CURL_OPTS "$BASE_URL/reviewer/history?page=1&size=10" -H "Authorization: Bearer $TOKEN_REV")
assert_code_200 "Review history returns 200" "$RESP_HISTORY"
assert_contains "Review history has content" "$RESP_HISTORY" '"content":'
echo ""

# --- Reviewer statistics ---
echo "[6/6] Get reviewer statistics..."
RESP_STATS=$(curl -s $CURL_OPTS "$BASE_URL/reviewer/statistics" -H "Authorization: Bearer $TOKEN_REV")
assert_code_200 "Reviewer statistics returns 200" "$RESP_STATS"
echo ""

print_summary