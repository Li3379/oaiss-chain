#!/bin/bash
# 04-01: Carbon Neutral Project Lifecycle Test
# Requirements: PROJ-01~05

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

echo "=== 04-01: Carbon Neutral Project Lifecycle ==="
echo ""

# --- Authentication ---
echo "[1/6] Authenticating..."

RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1" "enterprise001")
E1_USER_ID=$(extract_field "$RESP_E1" "data.userId")

RESP_ADMIN=$(login_user "admin")
TOKEN_ADMIN=$(extract_token "$RESP_ADMIN" "admin")

echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo "  admin token: ${TOKEN_ADMIN:0:20}..."
echo ""

# --- PROJ-01: Create project + view list + detail ---
echo "[2/6] PROJ-01: Project CRUD..."

RESP_CREATE=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"projectName":"Test Forest Carbon Sink Project","projectType":1,"description":"Test project for lifecycle verification","location":"Beijing","expectedReduction":5000}')

PROJECT_ID=$(extract_field "$RESP_CREATE" "data.id")
assert_contains "Create project (DRAFT)" "$RESP_CREATE" '"status":0'
echo "  Project ID: $PROJECT_ID"

RESP_MY=$(curl -s $CURL_OPTS "$BASE_URL/carbon-neutral/my?page=1&size=20" \
    -H "Authorization: Bearer $TOKEN_E1")
assert_contains "View my projects list" "$RESP_MY" "$PROJECT_ID"

RESP_DETAIL=$(curl -s $CURL_OPTS "$BASE_URL/carbon-neutral/$PROJECT_ID" \
    -H "Authorization: Bearer $TOKEN_E1")
assert_contains "View project detail" "$RESP_DETAIL" '"projectName":"Test Forest Carbon Sink Project"'
assert_contains "Project detail status=DRAFT" "$RESP_DETAIL" '"status":0'
echo ""

# --- PROJ-02: Submit for review ---
echo "[3/6] PROJ-02: Submit for review..."

RESP_SUBMIT=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_ID/submit" \
    -H "Authorization: Bearer $TOKEN_E1")
assert_contains "Submit for review (DRAFT->PENDING)" "$RESP_SUBMIT" '"status":1'
echo ""

# --- PROJ-03: Review (approve + reject) ---
echo "[4/6] PROJ-03: Review (approve + reject)..."

RESP_CREATE_B=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"projectName":"Test Rejected Project","projectType":2,"description":"Will be rejected","expectedReduction":1000}')
PROJECT_B_ID=$(extract_field "$RESP_CREATE_B" "data.id")
echo "  Project B ID: $PROJECT_B_ID"

curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_B_ID/submit" \
    -H "Authorization: Bearer $TOKEN_E1" > /dev/null

RESP_APPROVE=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_ID/review" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d '{"approved":true,"comment":"Approved for testing"}')
assert_contains "Approve project A (PENDING->APPROVED)" "$RESP_APPROVE" '"status":2'

RESP_REJECT=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_B_ID/review" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d '{"approved":false,"comment":"Insufficient documentation"}')
assert_contains "Reject project B (PENDING->REJECTED)" "$RESP_REJECT" '"status":6'
echo ""

# --- PROJ-04: Start + Terminate ---
echo "[5/6] PROJ-04: Start + Terminate..."

RESP_START=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_ID/start" \
    -H "Authorization: Bearer $TOKEN_E1")
assert_contains "Start implementation (APPROVED->IMPLEMENTING)" "$RESP_START" '"status":3'

RESP_TERM=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_ID/terminate" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"reason":"Testing termination flow"}')
assert_contains "Terminate project (IMPLEMENTING->TERMINATED)" "$RESP_TERM" '"status":5'
echo ""

# --- PROJ-05: Verification + Certification ---
echo "[6/6] PROJ-05: Verification + Certification..."

RESP_CREATE_C=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"projectName":"Test Full Lifecycle Project","projectType":3,"description":"Full lifecycle","expectedReduction":3000}')
PROJECT_C_ID=$(extract_field "$RESP_CREATE_C" "data.id")
echo "  Project C ID: $PROJECT_C_ID"

curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_C_ID/submit" -H "Authorization: Bearer $TOKEN_E1" > /dev/null
curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_C_ID/review" -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json" -d '{"approved":true,"comment":"Approved"}' > /dev/null
curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_C_ID/start" -H "Authorization: Bearer $TOKEN_E1" > /dev/null
echo "  Project C status: IMPLEMENTING"

RESP_SUB_VER=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_C_ID/submit-verification" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d "{\"verifierId\":$E1_USER_ID}")
assert_contains "Submit for verification" "$RESP_SUB_VER" '"verificationStatus":1'

RESP_VERIFY=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/verify" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d "{\"projectId\":$PROJECT_C_ID,\"verifiedReduction\":3000,\"verificationReport\":\"Verified by admin\",\"monitoringData\":\"{}\"}")
assert_contains "Verify as ADMIN" "$RESP_VERIFY" '"verificationStatus":2'
assert_contains "Verify issues credits" "$RESP_VERIFY" '"issuedCredits":3000'

RESP_CERT_APPLY=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_C_ID/apply-certification" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d '{"certOrg":"TestCertOrg"}')
assert_contains "Apply for certification" "$RESP_CERT_APPLY" '"certStatus":1'

RESP_CERT=$(curl -s $CURL_OPTS -X POST "$BASE_URL/carbon-neutral/$PROJECT_C_ID/certify" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d '{"certNo":"CERT-TEST-001"}')
assert_contains "Certify as ADMIN" "$RESP_CERT" '"certStatus":2'
assert_contains "Certification number set" "$RESP_CERT" '"certNo":"CERT-TEST-001"'
echo ""

# --- Summary ---
print_summary