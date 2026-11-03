#!/usr/bin/env bash
# bootstrap-fabric.sh smoke test (chaincode version decision logic).
#
# Background: the old bootstrap hard-coded v1.0/seq1. Against a network that
# already committed a newer version (e.g. v1.2/seq3) it mis-detected "not
# committed", tried to redefine with sequence 1, got rejected by Fabric
# ("sequence must be strictly increasing"), and set -e killed start-all
# --with-fabric.
#
# This test locks in:
#   1. parse_committed_line parses real querycommitted output
#   2. committed == desired -> skip (idempotent)
#   3. not committed -> fresh deploy sequence 1
#   4. committed != desired -> upgrade deploy sequence = committed + 1
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BOOTSTRAP="$REPO_ROOT/scripts/ops/bootstrap-fabric.sh"

PASS=0
FAIL=0
ok()   { PASS=$((PASS+1)); echo "  OK: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  FAIL: $1"; }

TMP_DIR="$(mktemp -d 2>/dev/null || echo "$REPO_ROOT/tmp/bootstrap-test-$$")"
mkdir -p "$TMP_DIR"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "=== bootstrap-fabric.sh chaincode version decision smoke test ==="

# load_bootstrap: main-guard keeps sourcing side-effect free; stubs override after load.
load_bootstrap() {
    # shellcheck disable=SC1090
    source "$BOOTSTRAP"
}

DEPLOY_CALLS=()

# ============================================================
# Case 1: parse_committed_line on real querycommitted output
# ============================================================
echo "[1/4] parse_committed_line parses real output"
load_bootstrap
set +e
OUT=$(parse_committed_line "Committed chaincode definition for chaincode 'carbon-chaincode' on channel 'mychannel':
Version: 1.2, Sequence: 3, Endorsement Plugin: escc, Validation Plugin: vscc, Approvals: [Org1MSP: true]")
if [[ "$OUT" == "1.2 3" ]]; then
    ok "parses version=1.2 sequence=3"
else
    bad "unexpected parse result: '$OUT'"
fi

OUT=$(parse_committed_line "")
if [[ -z "$OUT" ]]; then
    ok "empty input returns empty"
else
    bad "empty input should return empty: '$OUT'"
fi

OUT=$(parse_committed_line "Version: 1.2, Endorsement Plugin: escc")
if [[ -z "$OUT" ]]; then
    ok "version-without-sequence returns empty (both-or-empty contract)"
else
    bad "version-without-sequence should return empty, got: '$OUT'"
fi

# ============================================================
# Case 2: committed == desired -> skip deploy
# ============================================================
echo "[2/4] skip when committed version matches desired"
load_bootstrap
set +e
fabric_admin_bash() {
    printf 'Version: 1.2, Sequence: 3, Endorsement Plugin: escc, Validation Plugin: vscc, Approvals: [Org1MSP: true]\n'
}
DEPLOY_CALLS=()
run_deploy() { DEPLOY_CALLS+=("$1 $2"); }
ensure_chaincode
if [[ ${#DEPLOY_CALLS[@]} -eq 0 ]]; then
    ok "no deploy when committed == desired"
else
    bad "unexpected deploy on matching version: ${DEPLOY_CALLS[*]}"
fi

# ============================================================
# Case 3: not committed -> fresh deploy seq1
# ============================================================
echo "[3/4] fresh deploy seq1 when nothing committed"
load_bootstrap
set +e
fabric_admin_bash() { return 0; }
DEPLOY_CALLS=()
run_deploy() { DEPLOY_CALLS+=("$1 $2"); }
ensure_chaincode
if [[ "${DEPLOY_CALLS[0]:-}" == "1.2 1" ]]; then
    ok "not committed -> deploy v1.2 seq1"
else
    bad "fresh deploy args unexpected: '${DEPLOY_CALLS[0]}'"
fi

# ============================================================
# Case 4: committed 1.0/seq1 -> upgrade deploy 1.2/seq2
# ============================================================
echo "[4/4] upgrade deploy when committed version is behind"
load_bootstrap
set +e
fabric_admin_bash() { printf 'Version: 1.0, Sequence: 1, ...\n'; }
DEPLOY_CALLS=()
run_deploy() { DEPLOY_CALLS+=("$1 $2"); }
ensure_chaincode
if [[ "${DEPLOY_CALLS[0]:-}" == "1.2 2" ]]; then
    ok "1.0/seq1 -> upgrade deploy v1.2 seq2"
else
    bad "upgrade deploy args unexpected: '${DEPLOY_CALLS[0]}'"
fi

echo ""
echo "=== Result: $PASS passed, $FAIL failed ==="
if [[ $FAIL -eq 0 ]]; then
    echo "OK: all chaincode version decision tests passed"
    exit 0
else
    echo "FAILED: some cases failed"
    exit 1
fi
