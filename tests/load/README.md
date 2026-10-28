# OAISS CHAIN Load Tests

Performance testing suite using k6 for API endpoint stress testing.

## Prerequisites

- k6 installed: https://k6.io/docs/getting-started/installation/
- Backend running on localhost:8080 (or set API_BASE_URL)
- Test users seeded in database (enterprise001, admin, reviewer001, thirdparty001)

## Test Types

### Smoke Test
Minimal load to verify all endpoints work correctly.
`ash
k6 run tests/load/smoke.js
`

### Stress Test
Ramp up to find the system's breaking point (10 → 25 → 50 VUs).
`ash
k6 run tests/load/stress.js
`

### Custom Configuration
`ash
# Custom API URL
API_BASE_URL=http://staging.example.com/api/v1 k6 run tests/load/smoke.js

# Custom test password
TEST_PASSWORD=securePass123 k6 run tests/load/smoke.js

# Override VUs and duration
k6 run --vus 20 --duration 2m tests/load/smoke.js
`

## Metrics Collected

| Metric | Description | Target |
|--------|-------------|--------|
| http_req_duration | Total request duration | p95 < 500ms (smoke), p95 < 1000ms (stress) |
| pi_latency | API-specific latency | p95 < 500ms |
| errors | Error rate | < 10% (smoke), < 15% (stress) |
| http_reqs | Total requests per second | Baseline measurement |

## Endpoints Tested

| Endpoint | Auth | Weight | Description |
|----------|------|--------|-------------|
| POST /auth/login | No | 5% | Authentication |
| GET /actuator/health | No | 10% | Health check |
| GET /carbon/my-reports | ENTERPRISE | 30% | Carbon report listing |
| GET /trade/auction-orders | ENTERPRISE | 20% | Trading market |
| GET /credit/score | ENTERPRISE | 15% | Credit score |
| GET /search/market-overview | ENTERPRISE | 10% | Market overview |
| GET /admin/users | ADMIN | 10% | Admin user list |

## CI Integration

Add to GitHub Actions:
`yaml
- name: Run load tests
  uses: grafana/k6-action@v0.3.1
  with:
    filename: tests/load/smoke.js
  env:
    API_BASE_URL: http://localhost:8080/api/v1
`

## Output

Test results are saved as JSON summaries in 	ests/load/:
- smoke-summary.json
- stress-summary.json

Import these into Grafana for visualization using the k6 dashboard.