#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

WITH_ML_HEALTH=false
WITH_BLOCKCHAIN=false
WITH_DELIVERY_GUARDS=false
WITH_BACKEND_TESTS=false
WITH_BACKEND_VERIFY=false
WITH_FRONTEND_TESTS=false
WITH_FRONTEND_BUILD=false
WITH_FRONTEND_E2E=false

usage() {
    cat <<'EOF'
OAISS CHAIN stability-first baseline runner

Usage:
  bash ./scripts/stability-baseline.sh [options]

Default stages:
  1. health-check.sh
  2. login-test.sh
  3. carbon-report-test.sh

Options:
  --with-ml-health         Check ML service /health after core flow
  --with-blockchain        Run blockchain-test.sh after login/core flow
  --with-delivery-guards   Run env validation and closure audit
  --with-backend-tests     Run mvn test
  --with-backend-verify    Run mvn verify
  --with-frontend-tests    Run npm run test
  --with-frontend-build    Run npm run build
  --with-frontend-e2e      Run npm run test:e2e
  --all                    Enable all optional stages except --with-backend-verify
  --help                   Show this help

Notes:
  - login-test.sh and blockchain-test.sh are always serialized.
  - Use local or local,fabric profiles deliberately; do not mix deployment profiles here.
  - This script is intended for takeover and stability verification, not production deployment.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --with-ml-health)
            WITH_ML_HEALTH=true
            ;;
        --with-blockchain)
            WITH_BLOCKCHAIN=true
            ;;
        --with-delivery-guards)
            WITH_DELIVERY_GUARDS=true
            ;;
        --with-backend-tests)
            WITH_BACKEND_TESTS=true
            ;;
        --with-backend-verify)
            WITH_BACKEND_VERIFY=true
            ;;
        --with-frontend-tests)
            WITH_FRONTEND_TESTS=true
            ;;
        --with-frontend-build)
            WITH_FRONTEND_BUILD=true
            ;;
        --with-frontend-e2e)
            WITH_FRONTEND_E2E=true
            ;;
        --all)
            WITH_ML_HEALTH=true
            WITH_BLOCKCHAIN=true
            WITH_DELIVERY_GUARDS=true
            WITH_BACKEND_TESTS=true
            WITH_FRONTEND_TESTS=true
            WITH_FRONTEND_BUILD=true
            WITH_FRONTEND_E2E=true
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
    shift
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo -e "${GREEN}[BASELINE]${NC} $*"
}

info() {
    echo -e "${YELLOW}[INFO]${NC} $*"
}

fail() {
    echo -e "${RED}[FAIL]${NC} $*" >&2
    exit 1
}

run_step() {
    local label="$1"
    shift
    log "$label"
    "$@"
}

require_cmd() {
    local cmd="$1"
    command -v "$cmd" >/dev/null 2>&1 || fail "Required command not found: $cmd"
}

detect_http_base() {
    if [[ -n "${1:-}" ]]; then
        echo "$1"
        return
    fi

    if grep -qi microsoft /proc/version 2>/dev/null; then
        echo "http://host.docker.internal:$2"
    else
        echo "http://localhost:$2"
    fi
}

check_ml_health() {
    local ml_base
    ml_base="$(detect_http_base "${ML_BASE_URL:-}" 8001)"
    curl -fsS "${ml_base}/health" >/dev/null
}

validate_env_templates() {
    require_cmd node

    (cd "$PROJECT_ROOT" && node ./scripts/validate-prod-env.mjs)
    (cd "$PROJECT_ROOT" && node ./scripts/validate-prod-env.mjs .env.staging.example)

    if (cd "$PROJECT_ROOT" && node ./scripts/validate-prod-env.mjs --require-real-secrets .env.prod.example >/dev/null 2>&1); then
        fail "Strict production env validation unexpectedly passed for placeholder template"
    fi

    if (cd "$PROJECT_ROOT" && node ./scripts/validate-prod-env.mjs --require-real-secrets .env.staging.example >/dev/null 2>&1); then
        fail "Strict staging env validation unexpectedly passed for placeholder template"
    fi
}

run_delivery_guards() {
    validate_env_templates
    (cd "$PROJECT_ROOT" && node ./scripts/closure-audit.mjs)
}

run_backend_tests() {
    require_cmd mvn
    (cd "$PROJECT_ROOT/oaiss-chain-backend" && mvn test)
}

run_backend_verify() {
    require_cmd mvn
    (cd "$PROJECT_ROOT/oaiss-chain-backend" && mvn verify)
}

run_frontend_tests() {
    require_cmd npm
    (cd "$PROJECT_ROOT/oaiss-chain-frontend" && npm run test)
}

run_frontend_build() {
    require_cmd npm
    (cd "$PROJECT_ROOT/oaiss-chain-frontend" && npm run build)
}

run_frontend_e2e() {
    require_cmd npm
    (cd "$PROJECT_ROOT/oaiss-chain-frontend" && npm run test:e2e)
}

main() {
    require_cmd bash
    require_cmd curl

    info "Project root: $PROJECT_ROOT"
    info "Core flow order: health -> login -> carbon lifecycle"

    run_step "Stage 1/3: environment health baseline" bash "$PROJECT_ROOT/scripts/common/health-check.sh"
    run_step "Stage 2/3: authentication and seed account smoke" bash "$PROJECT_ROOT/scripts/api/login-test.sh"
    run_step "Stage 3/3: carbon report lifecycle baseline" bash "$PROJECT_ROOT/scripts/api/carbon-report-test.sh"

    if [[ "$WITH_ML_HEALTH" == true ]]; then
        run_step "Optional: ML service health" check_ml_health
    fi

    if [[ "$WITH_BLOCKCHAIN" == true ]]; then
        run_step "Optional: blockchain explorer smoke" bash "$PROJECT_ROOT/scripts/api/blockchain-test.sh"
    fi

    if [[ "$WITH_BACKEND_TESTS" == true ]]; then
        run_step "Optional: backend unit and contract tests" run_backend_tests
    fi

    if [[ "$WITH_BACKEND_VERIFY" == true ]]; then
        run_step "Optional: backend verify / integration baseline" run_backend_verify
    fi

    if [[ "$WITH_FRONTEND_TESTS" == true ]]; then
        run_step "Optional: frontend unit tests" run_frontend_tests
    fi

    if [[ "$WITH_FRONTEND_BUILD" == true ]]; then
        run_step "Optional: frontend production build" run_frontend_build
    fi

    if [[ "$WITH_FRONTEND_E2E" == true ]]; then
        run_step "Optional: frontend Playwright E2E" run_frontend_e2e
    fi

    if [[ "$WITH_DELIVERY_GUARDS" == true ]]; then
        run_step "Optional: delivery guardrails" run_delivery_guards
    fi

    echo
    log "Baseline verification complete"
    info "Recommended doc: $PROJECT_ROOT/docs/运维部署/稳定性接手.md"
}

main "$@"
