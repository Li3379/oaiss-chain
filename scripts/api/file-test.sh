#!/bin/bash
# 05-02: File Management - Upload/Download/Info/Exists/List/Presigned/Delete + MinIO Console
# Requirements: FILE-01, FILE-02, FILE-03
#
# Required seed data:
#   - Users: enterprise001 (password from TEST_PASSWORD env, default: admin123)
#   - MinIO running and accessible
#   - Backend running at BASE_URL (default: http://localhost:8080/api/v1)

source "$(dirname "$0")/test-helpers.sh"

check_dependencies

echo "=== 05-02: File Management (FILE-01~03) ==="
echo ""

# --- Authentication (WR-01: validate tokens) ---
echo "[1/10] Authenticating..."

RESP_E1=$(login_user "enterprise001")
TOKEN_E1=$(extract_token "$RESP_E1" "enterprise001")
echo "  enterprise001 token: ${TOKEN_E1:0:20}..."
echo ""

# --- Create temp file for upload (WR-04: trap-based cleanup) ---
echo "[2/10] Creating test file..."
TEST_FILE="/tmp/oaiss-test-file-$(date +%s).txt"
echo "OAISS CHAIN file management test - $(date)" > "$TEST_FILE"
echo "  Test file: $TEST_FILE"
trap 'rm -f "$TEST_FILE" /tmp/oaiss-downloaded.txt 2>/dev/null' EXIT
echo ""

# --- FILE-01: Upload file ---
echo "[3/10] FILE-01: Upload file..."

RESP_UPLOAD=$(curl -s -X POST "$BASE_URL/file/upload" \
    -H "Authorization: Bearer $TOKEN_E1" \
    -F "file=@$TEST_FILE" \
    -F "folder=test/")
echo "  Upload response: $(echo "$RESP_UPLOAD" | head -c 300)"

assert_code_200 "Upload returns 200" "$RESP_UPLOAD"
assert_contains "Upload has objectName" "$RESP_UPLOAD" '"objectName":'

OBJECT_NAME=$(extract_data_field "$RESP_UPLOAD" "objectName")
echo "  Uploaded objectName: $OBJECT_NAME"
echo ""

# --- FILE-02: Get file info ---
echo "[4/10] FILE-02: Get file info..."

RESP_INFO=$(curl -s "$BASE_URL/file/info?objectName=$OBJECT_NAME" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Info response: $(echo "$RESP_INFO" | head -c 300)"

assert_code_200 "File info returns 200" "$RESP_INFO"
assert_contains "File info has size" "$RESP_INFO" '"size":'
assert_contains "File info has contentType" "$RESP_INFO" '"contentType":'
echo ""

# --- Check file exists ---
echo "[5/10] FILE-02: Check file exists..."

RESP_EXISTS=$(curl -s "$BASE_URL/file/exists?objectName=$OBJECT_NAME" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Exists response: $RESP_EXISTS"

assert_code_200 "File exists returns 200" "$RESP_EXISTS"
assert_contains "File exists returns true" "$RESP_EXISTS" '"data":true'
echo ""

# --- List files ---
echo "[6/10] FILE-02: List files..."

RESP_LIST=$(curl -s "$BASE_URL/file/list?prefix=test/&page=1&size=10" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  List response: $(echo "$RESP_LIST" | head -c 300)"

assert_code_200 "File list returns 200" "$RESP_LIST"
echo ""

# --- Presigned URL ---
echo "[7/10] FILE-03: Presigned URL..."

RESP_PRESIGNED=$(curl -s "$BASE_URL/file/presigned-url?objectName=$OBJECT_NAME" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Presigned URL response: $(echo "$RESP_PRESIGNED" | head -c 300)"

assert_code_200 "Presigned URL returns 200" "$RESP_PRESIGNED"
assert_contains "Presigned URL has URL data" "$RESP_PRESIGNED" '"data":'
echo ""

# --- Download file ---
echo "[8/10] FILE-03: Download file..."

HTTP_CODE=$(curl -s -o /tmp/oaiss-downloaded.txt -w "%{http_code}" \
    "$BASE_URL/file/download?objectName=$OBJECT_NAME" \
    -H "Authorization: Bearer $TOKEN_E1")
TEST_ID=$((TEST_ID + 1))
if [ "$HTTP_CODE" = "200" ]; then
    echo "  [PASS] Test $TEST_ID: Download returns HTTP 200"
    PASS=$((PASS + 1))
    # Verify content
    DOWNLOADED=$(cat /tmp/oaiss-downloaded.txt)
    if echo "$DOWNLOADED" | grep -q "OAISS CHAIN file management test"; then
        TEST_ID=$((TEST_ID + 1))
        echo "  [PASS] Test $TEST_ID: Downloaded content matches original"
        PASS=$((PASS + 1))
    else
        TEST_ID=$((TEST_ID + 1))
        echo "  [FAIL] Test $TEST_ID: Downloaded content does NOT match original"
        FAIL=$((FAIL + 1))
    fi
else
    echo "  [FAIL] Test $TEST_ID: Download returns HTTP $HTTP_CODE (expected 200)"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- MinIO console check (IN-02: renumbered from 8.5 to 9) ---
echo "[9/10] MinIO console accessibility..."

MINIO_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9003 2>/dev/null || echo "000")
TEST_ID=$((TEST_ID + 1))
if [ "$MINIO_CODE" = "200" ] || [ "$MINIO_CODE" = "302" ]; then
    echo "  [PASS] Test $TEST_ID: MinIO console at localhost:9003 (HTTP $MINIO_CODE)"
    PASS=$((PASS + 1))
else
    echo "  [FAIL] Test $TEST_ID: MinIO console at localhost:9003 (HTTP $MINIO_CODE)"
    FAIL=$((FAIL + 1))
fi
echo ""

# --- Delete file ---
echo "[10/10] Cleanup: Delete file..."

RESP_DELETE=$(curl -s -X DELETE "$BASE_URL/file?objectName=$OBJECT_NAME" \
    -H "Authorization: Bearer $TOKEN_E1")
echo "  Delete response: $(echo "$RESP_DELETE" | head -c 300)"

assert_code_200 "Delete returns 200" "$RESP_DELETE"

# Verify deleted
RESP_EXISTS_AFTER=$(curl -s "$BASE_URL/file/exists?objectName=$OBJECT_NAME" \
    -H "Authorization: Bearer $TOKEN_E1")
assert_contains "File no longer exists after delete" "$RESP_EXISTS_AFTER" '"data":false'
echo ""

# --- Summary ---
print_summary
