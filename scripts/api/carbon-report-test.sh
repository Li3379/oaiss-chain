#!/usr/bin/env bash
set -euo pipefail

# 加载数据库配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/db-config.sh"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[..]${NC} $1"; }

detect_api_base() {
    if [[ -n "${API:-}" ]]; then
        echo "$API"
        return
    fi

    if grep -qi microsoft /proc/version 2>/dev/null; then
        echo "http://host.docker.internal:8080/api/v1"
    else
        echo "http://localhost:8080/api/v1"
    fi
}

API="$(detect_api_base)"
CURL_OPTS="--connect-timeout 5 --max-time 15"
TIMESTAMP=$(date +%s)

# --- Cleanup test data before running ---
cleanup_test_data() {
    info "Cleaning up existing test data..."
    exec_mysql_query "
        DELETE FROM emission_rating WHERE remark LIKE 'CARB-TEST-%';
        DELETE FROM credit_event WHERE event_type LIKE 'CARB-TEST-%';
        DELETE FROM carbon_report WHERE title LIKE 'CARB-TEST-%';
    " 2>/dev/null || true
    ok "Test data cleanup complete"
}

# Run cleanup if SKIP_CLEANUP is not set
if [[ "${SKIP_CLEANUP:-}" != "true" ]]; then
    cleanup_test_data
fi

TOTAL=0
PASSED=0
FAILED=0

# --- Helper: extract JSON field value via grep ---
extract_field() {
  local json="$1" field="$2"
  echo "$json" | { grep -o "\"$field\":[^,}]*" || true; } | head -1 | sed "s/\"$field\"://" | tr -d '"'
}

# --- Verify backend is up ---
info "Checking backend availability..."
curl -sf $CURL_OPTS "$API/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' -o /dev/null || { fail "Backend not running. Start it first with './scripts/start-backend.sh' or 'scripts\\start-backend.bat'."; exit 1; }
ok "Backend is reachable"

# --- Login helpers ---
login() {
  local username="$1"
  local resp=$(curl -s $CURL_OPTS -X POST "$API/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$username\",\"password\":\"admin123\"}")

  local code=$(extract_field "$resp" "code")
  if [[ "$code" != "200" ]]; then
    fail "$username: Login failed (code=$code)"
    echo "$resp" >&2
    return 1
  fi

  local token=$(echo "$resp" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [[ -z "$token" ]]; then
    fail "$username: No accessToken in response"
    return 1
  fi
  echo "$token"
}

# --- Step 0: Login as enterprise001 and enterprise002 ---
info "Logging in as enterprise001..."
TOKEN_E1=$(login "enterprise001") || { fail "Cannot proceed without enterprise001 token"; exit 1; }
ok "enterprise001 logged in"

info "Logging in as enterprise002..."
TOKEN_E2=$(login "enterprise002") || { fail "Cannot proceed without enterprise002 token"; exit 1; }
ok "enterprise002 logged in"

info "Logging in as admin..."
TOKEN_ADMIN=$(login "admin") || { fail "Cannot proceed without admin token"; exit 1; }
ok "admin logged in"

# --- Step 1: [CARB-01] File upload (best-effort) ---
info "[CARB-01] Uploading test file..."
UPLOAD_FILE=$(mktemp)
echo "Carbon report test evidence - $TIMESTAMP" > "$UPLOAD_FILE"

UPLOAD_RESP=$(curl -s $CURL_OPTS -X POST "$API/file/upload" \
  -H "Authorization: Bearer $TOKEN_E1" \
  -F "file=@$UPLOAD_FILE;filename=test-evidence.txt;type=text/plain" || true)

UPLOAD_CODE=$(extract_field "$UPLOAD_RESP" "code")
OBJECT_NAME=""

if [[ "$UPLOAD_CODE" == "200" ]]; then
  OBJECT_NAME=$(extract_field "$UPLOAD_RESP" "objectName")
  ok "File uploaded, objectName=$OBJECT_NAME"
else
  info "File upload failed or MinIO unavailable (code=$UPLOAD_CODE), proceeding without attachment"
fi
rm -f "$UPLOAD_FILE"

# --- Step 2: [CARB-01] Create reports ---
info "[CARB-01] Creating carbon reports..."

EMISSION_DATA='{"scope1":[{"name":"gas","activity_data":1000,"emission_factor":2.0}],"scope2":[{"name":"electricity","activity_data":5000,"emission_factor":0.6}],"scope3":[{"name":"travel","activity_data":200,"emission_factor":0.15}]}'
# Escape internal quotes for embedding as a JSON string value
EMISSION_ESCAPED=$(echo "$EMISSION_DATA" | sed 's/"/\\"/g')

ATTACHMENTS="null"
if [[ -n "$OBJECT_NAME" ]]; then
  ATTACHMENTS="$OBJECT_NAME"
fi

# Create report1 (enterprise001, will be approved in Plan 02-02)
REPORT1_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/reports" \
  -H "Authorization: Bearer $TOKEN_E1" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"CARB-TEST-APPROVE-$TIMESTAMP\",\"accountingPeriod\":\"2024-Q1\",\"reportType\":1,\"emissionData\":\"${EMISSION_ESCAPED}\",\"calculationMethod\":\"manual\",\"attachments\":\"${ATTACHMENTS}\"}")

REPORT1_CODE=$(extract_field "$REPORT1_RESP" "code")
TOTAL=$((TOTAL + 1))
if [[ "$REPORT1_CODE" == "200" ]]; then
  REPORT1_ID=$(extract_field "$REPORT1_RESP" "id")
  ok "Report1 created (id=$REPORT1_ID, title=CARB-TEST-APPROVE-$TIMESTAMP)"
  PASSED=$((PASSED + 1))
else
  fail "Report1 creation failed (code=$REPORT1_CODE)"
  echo "$REPORT1_RESP" >&2
  FAILED=$((FAILED + 1))
fi

# Create report2 (enterprise001, will be rejected in Plan 02-02)
REPORT2_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/reports" \
  -H "Authorization: Bearer $TOKEN_E1" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"CARB-TEST-REJECT-$TIMESTAMP\",\"accountingPeriod\":\"2024-Q2\",\"reportType\":1,\"emissionData\":\"${EMISSION_ESCAPED}\",\"calculationMethod\":\"manual\",\"attachments\":null}")

REPORT2_CODE=$(extract_field "$REPORT2_RESP" "code")
TOTAL=$((TOTAL + 1))
if [[ "$REPORT2_CODE" == "200" ]]; then
  REPORT2_ID=$(extract_field "$REPORT2_RESP" "id")
  ok "Report2 created (id=$REPORT2_ID, title=CARB-TEST-REJECT-$TIMESTAMP)"
  PASSED=$((PASSED + 1))
else
  fail "Report2 creation failed (code=$REPORT2_CODE)"
  echo "$REPORT2_RESP" >&2
  FAILED=$((FAILED + 1))
fi

# Create report3 (enterprise002, stays DRAFT -- tests data isolation)
REPORT3_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/reports" \
  -H "Authorization: Bearer $TOKEN_E2" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"CARB-TEST-ISOLATION-$TIMESTAMP\",\"accountingPeriod\":\"2024-Q3\",\"reportType\":2,\"emissionData\":\"${EMISSION_ESCAPED}\",\"calculationMethod\":\"manual\",\"attachments\":null}")

REPORT3_CODE=$(extract_field "$REPORT3_RESP" "code")
TOTAL=$((TOTAL + 1))
if [[ "$REPORT3_CODE" == "200" ]]; then
  REPORT3_ID=$(extract_field "$REPORT3_RESP" "id")
  ok "Report3 created (id=$REPORT3_ID, title=CARB-TEST-ISOLATION-$TIMESTAMP)"
  PASSED=$((PASSED + 1))
else
  fail "Report3 creation failed (code=$REPORT3_CODE)"
  echo "$REPORT3_RESP" >&2
  FAILED=$((FAILED + 1))
fi

# --- Step 3: [CARB-02] List my-reports for enterprise001 ---
info "[CARB-02] Listing enterprise001 reports..."
LIST_RESP=$(curl -s $CURL_OPTS "$API/carbon/my-reports?page=1&size=20" \
  -H "Authorization: Bearer $TOKEN_E1")

LIST_CODE=$(extract_field "$LIST_RESP" "code")
TOTAL=$((TOTAL + 1))
if [[ "$LIST_CODE" != "200" ]]; then
  fail "List my-reports failed (code=$LIST_CODE)"
  FAILED=$((FAILED + 1))
else
  # Verify report1 and report2 appear, report3 does NOT
  FOUND1=0; FOUND2=0; FOUND3=0
  [[ -n "${REPORT1_ID:-}" ]] && echo "$LIST_RESP" | grep -q "\"id\":$REPORT1_ID" && FOUND1=1
  [[ -n "${REPORT2_ID:-}" ]] && echo "$LIST_RESP" | grep -q "\"id\":$REPORT2_ID" && FOUND2=1
  [[ -n "${REPORT3_ID:-}" ]] && echo "$LIST_RESP" | grep -q "\"id\":$REPORT3_ID" && FOUND3=1

  if [[ "$FOUND1" -eq 1 && "$FOUND2" -eq 1 && "$FOUND3" -eq 0 ]]; then
    ok "List contains report1 ($REPORT1_ID) and report2 ($REPORT2_ID), but NOT report3 (data isolation OK)"
    PASSED=$((PASSED + 1))
  else
    fail "List check failed: found1=$FOUND1 found2=$FOUND2 found3=$FOUND3 (expected 1,1,0)"
    FAILED=$((FAILED + 1))
  fi
fi

# --- Step 4: [CARB-03] Get report1 detail ---
info "[CARB-03] Fetching report1 detail..."
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT1_ID:-}" ]]; then
  fail "Cannot fetch detail: report1 ID not available"
  FAILED=$((FAILED + 1))
else
  DETAIL_RESP=$(curl -s $CURL_OPTS "$API/carbon/reports/$REPORT1_ID" \
    -H "Authorization: Bearer $TOKEN_E1")

  DETAIL_CODE=$(extract_field "$DETAIL_RESP" "code")
  if [[ "$DETAIL_CODE" != "200" ]]; then
    fail "Get report detail failed (code=$DETAIL_CODE)"
    FAILED=$((FAILED + 1))
  else
    DETAIL_ID=$(extract_field "$DETAIL_RESP" "id")
    DETAIL_STATUS=$(extract_field "$DETAIL_RESP" "status")
    HAS_EMISSION=$(echo "$DETAIL_RESP" | grep -o '"emissionData"' | head -1)

    if [[ "$DETAIL_ID" == "$REPORT1_ID" && "$DETAIL_STATUS" == "0" && -n "$HAS_EMISSION" ]]; then
      ok "Report1 detail: id=$DETAIL_ID status=$DETAIL_STATUS emissionData=present"
      PASSED=$((PASSED + 1))
    else
      fail "Report1 detail mismatch: id=$DETAIL_ID (expected $REPORT1_ID) status=$DETAIL_STATUS (expected 0) emissionData=$HAS_EMISSION"
      FAILED=$((FAILED + 1))
    fi
  fi
fi

# --- Step 5: [CARB-04] Submit report1 and report2 ---
info "[CARB-04] Submitting report1..."

submit_report() {
  local rid="$1" label="$2"
  local resp=$(curl -s $CURL_OPTS -X POST "$API/carbon/reports/$rid/submit" \
    -H "Authorization: Bearer $TOKEN_E1")

  local code=$(extract_field "$resp" "code")
  TOTAL=$((TOTAL + 1))
  if [[ "$code" != "200" ]]; then
    fail "$label submission failed (code=$code)"
    echo "$resp" >&2
    FAILED=$((FAILED + 1))
    return
  fi

  local new_status=$(extract_field "$resp" "status")
  local total_emission=$(extract_field "$resp" "totalEmission")

  if [[ "$new_status" == "1" && -n "$total_emission" && "$total_emission" != "0" ]]; then
    ok "$label submitted: status=$new_status totalEmission=$total_emission"
    PASSED=$((PASSED + 1))
  else
    fail "$label submit check failed: status=$new_status (expected 1) totalEmission=$total_emission"
    FAILED=$((FAILED + 1))
  fi
}

if [[ -n "${REPORT1_ID:-}" ]]; then
  submit_report "$REPORT1_ID" "Report1"
else
  TOTAL=$((TOTAL + 1))
  fail "Report1 submit skipped: no ID"
  FAILED=$((FAILED + 1))
fi

info "[CARB-04] Submitting report2..."
if [[ -n "${REPORT2_ID:-}" ]]; then
  submit_report "$REPORT2_ID" "Report2"
else
  TOTAL=$((TOTAL + 1))
  fail "Report2 submit skipped: no ID"
  FAILED=$((FAILED + 1))
fi

# --- Step 6: Login as reviewer001 ---
info "Logging in as reviewer001..."
TOKEN_R=$(login "reviewer001") || { fail "Cannot proceed without reviewer001 token"; exit 1; }
ok "reviewer001 logged in"

# --- Step 7: [CARB-05] Reviewer views SUBMITTED reports ---
info "[CARB-05] Reviewer listing SUBMITTED reports (status=1)..."
REVIEW_LIST_RESP=$(curl -s $CURL_OPTS "$API/reviewer/reports/pending?page=1&size=20" \
  -H "Authorization: Bearer $TOKEN_R")

REVIEW_LIST_CODE=$(extract_field "$REVIEW_LIST_RESP" "code")
TOTAL=$((TOTAL + 1))
if [[ "$REVIEW_LIST_CODE" != "200" ]]; then
  fail "CARB-05: Reviewer list SUBMITTED reports failed (code=$REVIEW_LIST_CODE)"
  FAILED=$((FAILED + 1))
else
  FOUND_R1=0; FOUND_R2=0
  [[ -n "${REPORT1_ID:-}" ]] && echo "$REVIEW_LIST_RESP" | grep -q "\"id\":$REPORT1_ID" && FOUND_R1=1
  [[ -n "${REPORT2_ID:-}" ]] && echo "$REVIEW_LIST_RESP" | grep -q "\"id\":$REPORT2_ID" && FOUND_R2=1

  if [[ "$FOUND_R1" -eq 1 || "$FOUND_R2" -eq 1 ]]; then
    ok "CARB-05: Found submitted reports (report1=$FOUND_R1, report2=$FOUND_R2)"
    PASSED=$((PASSED + 1))
  else
    fail "CARB-05: No submitted reports found for reviewer"
    FAILED=$((FAILED + 1))
  fi
fi

# --- Step 8: [CARB-06] Reviewer approves report1 ---
info "[CARB-06] Reviewer approving report1..."
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT1_ID:-}" ]]; then
  fail "CARB-06: Cannot approve report1: no ID"
  FAILED=$((FAILED + 1))
else
    APPROVE_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/review" \
      -H "Authorization: Bearer $TOKEN_R" \
      -H "Content-Type: application/json" \
      -d "{\"reportId\":$REPORT1_ID,\"reviewResult\":3,\"reviewComment\":\"Approved for testing\"}")

  APPROVE_CODE=$(extract_field "$APPROVE_RESP" "code")
  if [[ "$APPROVE_CODE" != "200" ]]; then
    fail "CARB-06: Approve report1 failed (code=$APPROVE_CODE)"
    echo "$APPROVE_RESP" >&2
    FAILED=$((FAILED + 1))
  else
    APPROVE_STATUS=$(extract_field "$APPROVE_RESP" "status")

    if [[ "$APPROVE_STATUS" == "3" ]]; then
      ok "CARB-06: Report1 approved: status=$APPROVE_STATUS"
      PASSED=$((PASSED + 1))
    else
      fail "CARB-06: Approval result mismatch: status=$APPROVE_STATUS (expected 3)"
      FAILED=$((FAILED + 1))
    fi
  fi
fi

# --- Step 9: [CARB-07] Reviewer rejects report2 ---
info "[CARB-07] Reviewer rejecting report2..."
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT2_ID:-}" ]]; then
  fail "CARB-07: Cannot reject report2: no ID"
  FAILED=$((FAILED + 1))
else
  REJECT_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/review" \
    -H "Authorization: Bearer $TOKEN_R" \
    -H "Content-Type: application/json" \
    -d "{\"reportId\":$REPORT2_ID,\"reviewResult\":4,\"reviewComment\":\"Data incomplete\"}")

  REJECT_CODE=$(extract_field "$REJECT_RESP" "code")
  if [[ "$REJECT_CODE" != "200" ]]; then
    fail "CARB-07: Reject report2 failed (code=$REJECT_CODE)"
    echo "$REJECT_RESP" >&2
    FAILED=$((FAILED + 1))
  else
    REJECT_STATUS=$(extract_field "$REJECT_RESP" "status")
    REJECT_COMMENT=$(extract_field "$REJECT_RESP" "reviewComment")

    if [[ "$REJECT_STATUS" == "4" ]]; then
      ok "CARB-07: Report2 rejected: status=$REJECT_STATUS comment=$REJECT_COMMENT"
      PASSED=$((PASSED + 1))
    else
      fail "CARB-07: Reject result mismatch: status=$REJECT_STATUS (expected 4)"
      FAILED=$((FAILED + 1))
    fi
  fi
fi

# --- Step 10: [CARB-08/09/10] Admin certifies approved report and verifies side effects ---
info "[CARB-08/09/10] Admin certifying approved report1 and verifying side effects..."
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT1_ID:-}" ]]; then
  fail "CARB-08/09/10: Cannot verify side effects: no report1 ID"
  FAILED=$((FAILED + 1))
else
  CERTIFY_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/certify" \
    -H "Authorization: Bearer $TOKEN_ADMIN" \
    -H "Content-Type: application/json" \
    -d "{\"reportId\":$REPORT1_ID,\"reviewResult\":5,\"reviewComment\":\"Certified for chain\"}")

  CERTIFY_CODE=$(extract_field "$CERTIFY_RESP" "code")
  if [[ "$CERTIFY_CODE" != "200" ]]; then
    fail "CARB-08/09/10: Certify report1 failed (code=$CERTIFY_CODE)"
    echo "$CERTIFY_RESP" >&2
    FAILED=$((FAILED + 1))
  else
  VERIFY_RESP=$(curl -s $CURL_OPTS "$API/carbon/reports/$REPORT1_ID" \
    -H "Authorization: Bearer $TOKEN_ADMIN")

  VERIFY_CODE=$(extract_field "$VERIFY_RESP" "code")
  if [[ "$VERIFY_CODE" != "200" ]]; then
    fail "CARB-08/09/10: Fetch report1 detail failed (code=$VERIFY_CODE)"
    FAILED=$((FAILED + 1))
  else
    VERIFY_STATUS=$(extract_field "$VERIFY_RESP" "status")
    VERIFY_TXHASH=$(echo "$VERIFY_RESP" | grep -o '"blockchainTxHash":"[^"]*"' | head -1 | cut -d'"' -f4)
    VERIFY_ONCHAIN=$(extract_field "$VERIFY_RESP" "onChainAt")

    TXHASH_VALID=0
    if [[ "$VERIFY_TXHASH" == tx_mock_* ]]; then
      TXHASH_VALID=1
    elif [[ "$VERIFY_TXHASH" =~ ^[0-9a-fA-F]{64}$ ]]; then
      TXHASH_VALID=1
    fi

    if [[ "$VERIFY_STATUS" == "5" && "$TXHASH_VALID" -eq 1 && -n "$VERIFY_ONCHAIN" ]]; then
      ok "CARB-08/09/10: Side effects verified: status=$VERIFY_STATUS txHash=$VERIFY_TXHASH onChainAt=present"
      PASSED=$((PASSED + 1))
    else
      fail "CARB-08/09/10: Side effect verification failed: status=$VERIFY_STATUS txHash=$VERIFY_TXHASH onChainAt=$VERIFY_ONCHAIN"
      FAILED=$((FAILED + 1))
    fi
  fi
  fi
fi

# --- Step 11: [CARB-11] Illegal state transitions ---
info "[CARB-11] Testing illegal state transitions..."

# Try to submit report1 again (already ON_CHAIN)
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT1_ID:-}" ]]; then
  fail "CARB-11a: Cannot test resubmit: no report1 ID"
  FAILED=$((FAILED + 1))
else
  RESUBMIT_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/reports/$REPORT1_ID/submit" \
    -H "Authorization: Bearer $TOKEN_E1")

  RESUBMIT_CODE=$(extract_field "$RESUBMIT_RESP" "code")
  if [[ "$RESUBMIT_CODE" != "200" ]]; then
    ok "CARB-11a: Resubmit ON_CHAIN report correctly rejected (code=$RESUBMIT_CODE)"
    PASSED=$((PASSED + 1))
  else
    fail "CARB-11a: Resubmit ON_CHAIN report should have failed but returned 200"
    FAILED=$((FAILED + 1))
  fi
fi

# Try to review report3 (DRAFT -- not reviewable)
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT3_ID:-}" ]]; then
  fail "CARB-11b: Cannot test draft review: no report3 ID"
  FAILED=$((FAILED + 1))
else
  DRAFT_REVIEW_RESP=$(curl -s $CURL_OPTS -X POST "$API/carbon/review" \
    -H "Authorization: Bearer $TOKEN_R" \
    -H "Content-Type: application/json" \
    -d "{\"reportId\":$REPORT3_ID,\"reviewResult\":3,\"reviewComment\":\"Should fail\"}")

  DRAFT_REVIEW_CODE=$(extract_field "$DRAFT_REVIEW_RESP" "code")
  if [[ "$DRAFT_REVIEW_CODE" != "200" ]]; then
    ok "CARB-11b: Review of DRAFT report correctly rejected (code=$DRAFT_REVIEW_CODE)"
    PASSED=$((PASSED + 1))
  else
    fail "CARB-11b: Review of DRAFT report should have failed but returned 200"
    FAILED=$((FAILED + 1))
  fi
fi

# --- Step 12: [CARB-13] Cross-role access control (enterprise denied review) ---
info "[CARB-13] Testing cross-role access control..."

TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT1_ID:-}" ]]; then
  fail "CARB-13a: Cannot test enterprise review: no report1 ID"
  FAILED=$((FAILED + 1))
else
  ENT_REVIEW_RESP=$(curl -s -w "\n%{http_code}" -X POST "$API/carbon/review" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -H "Content-Type: application/json" \
    -d "{\"reportId\":$REPORT1_ID,\"reviewResult\":3,\"reviewComment\":\"Should be denied\"}")

  ENT_REVIEW_HTTP=$(echo "$ENT_REVIEW_RESP" | tail -1)
  if [[ "$ENT_REVIEW_HTTP" == "403" ]]; then
    ok "CARB-13a: Enterprise POST /review correctly denied (HTTP 403)"
    PASSED=$((PASSED + 1))
  else
    ENT_REVIEW_CODE=$(extract_field "$(echo "$ENT_REVIEW_RESP" | head -1)" "code")
    fail "CARB-13a: Expected 403 for enterprise review, got HTTP=$ENT_REVIEW_HTTP code=$ENT_REVIEW_CODE"
    FAILED=$((FAILED + 1))
  fi
fi

# Data isolation: GET /carbon/my-reports with TOKEN_E1 does NOT contain report3
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT3_ID:-}" ]]; then
  fail "CARB-13b: Cannot test data isolation: no report3 ID"
  FAILED=$((FAILED + 1))
else
  ISO_E1_RESP=$(curl -s $CURL_OPTS "$API/carbon/my-reports?page=1&size=20" \
    -H "Authorization: Bearer $TOKEN_E1")

  ISO_E1_CODE=$(extract_field "$ISO_E1_RESP" "code")
  ISO_E1_HAS_R3=0
  echo "$ISO_E1_RESP" | grep -q "\"id\":$REPORT3_ID" && ISO_E1_HAS_R3=1

  if [[ "$ISO_E1_CODE" == "200" && "$ISO_E1_HAS_R3" -eq 0 ]]; then
    ok "CARB-13b: Enterprise001 my-reports does NOT contain report3 (data isolation OK)"
    PASSED=$((PASSED + 1))
  else
    fail "CARB-13b: Data isolation failed: code=$ISO_E1_CODE has_report3=$ISO_E1_HAS_R3"
    FAILED=$((FAILED + 1))
  fi
fi

# Data isolation: GET /carbon/my-reports with TOKEN_E2 contains only report3
TOTAL=$((TOTAL + 1))
if [[ -z "${REPORT3_ID:-}" ]]; then
  fail "CARB-13c: Cannot test data isolation E2: no report3 ID"
  FAILED=$((FAILED + 1))
else
  ISO_E2_RESP=$(curl -s $CURL_OPTS "$API/carbon/my-reports?page=1&size=20" \
    -H "Authorization: Bearer $TOKEN_E2")

  ISO_E2_CODE=$(extract_field "$ISO_E2_RESP" "code")
  ISO_E2_HAS_R3=0
  echo "$ISO_E2_RESP" | grep -q "\"id\":$REPORT3_ID" && ISO_E2_HAS_R3=1

  if [[ "$ISO_E2_CODE" == "200" && "$ISO_E2_HAS_R3" -eq 1 ]]; then
    ok "CARB-13c: Enterprise002 my-reports contains report3 (data isolation OK)"
    PASSED=$((PASSED + 1))
  else
    fail "CARB-13c: Data isolation failed: code=$ISO_E2_CODE has_report3=$ISO_E2_HAS_R3"
    FAILED=$((FAILED + 1))
  fi
fi

# --- Summary ---
echo ""
echo -e "========================================"
echo -e " Carbon Report Test Results (CARB-01..13)"
echo -e "========================================"
echo -e " Total:  $TOTAL"
echo -e " Passed: ${GREEN}$PASSED${NC}"
if [[ $FAILED -gt 0 ]]; then
  echo -e " Failed: ${RED}$FAILED${NC}"
else
  echo -e " Failed: 0"
fi
echo -e "========================================"

if [[ $FAILED -gt 0 ]]; then
  exit 1
fi
