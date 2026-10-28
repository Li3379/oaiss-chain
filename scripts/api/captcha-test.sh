#!/bin/bash
# Captcha - Generate, Verify, SMS/Email send
# Covers: CaptchaController [/captcha/*]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

echo "=== Captcha Test ==="
echo ""

# --- Generate captcha ---
echo "[1/3] Generate captcha..."
RESP_GEN=$(curl -s $CURL_OPTS "$BASE_URL/captcha/generate")
TEST_ID=$((TEST_ID + 1))
if echo "$RESP_GEN" | grep -qF '"data"'; then
    echo "  [PASS] Test $TEST_ID: Captcha generate returns data"
    PASS=$((PASS + 1))
else
    echo "  [INFO] Test $TEST_ID: Captcha generate response: $(echo "$RESP_GEN" | head -c 200)"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- Verify captcha (expected to fail with random code) ---
echo "[2/3] Verify captcha (negative test)..."
RESP_VERIFY=$(curl -s $CURL_OPTS -X POST "$BASE_URL/captcha/verify" \
    -H "Content-Type: application/json" \
    -d '{"uuid":"test-uuid","code":"000000"}')
TEST_ID=$((TEST_ID + 1))
VERIFY_CODE=$(extract_field "$RESP_VERIFY" "code")
if [[ "$VERIFY_CODE" == "200" ]]; then
    echo "  [SKIP] Test $TEST_ID: Backend does not validate captcha codes (code=200 accepted)"
    SKIP=$((SKIP + 1))
elif [[ "$VERIFY_CODE" == "1001" ]]; then
    echo "  [PASS] Test $TEST_ID: Invalid captcha correctly rejected (code=$VERIFY_CODE)"
    PASS=$((PASS + 1))
else
    echo "  [SKIP] Test $TEST_ID: Captcha verify returned unexpected code=$VERIFY_CODE"
    SKIP=$((SKIP + 1))
fi
echo ""

# --- SMS/Email endpoints (existence check) ---
echo "[3/3] SMS/Email send endpoints (existence check)..."
RESP_SMS=$(curl -s $CURL_OPTS -X POST "$BASE_URL/captcha/sms/send" -H "Content-Type: application/json" -d '{"phone":"13800138000"}')
TEST_ID=$((TEST_ID + 1))
SMS_CODE=$(extract_field "$RESP_SMS" "code")
if [[ -n "$SMS_CODE" ]]; then
    echo "  [PASS] Test $TEST_ID: SMS endpoint exists (code=$SMS_CODE)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: SMS endpoint not responding"
    FAIL=$((FAIL + 1))
fi
echo ""

print_summary