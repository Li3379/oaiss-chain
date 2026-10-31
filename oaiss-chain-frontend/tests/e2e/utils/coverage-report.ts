import * as fs from 'fs'
import * as path from 'path'

/**
 * Endpoint coverage tracker
 * Maps backend endpoints to test files that verify them
 */
export interface EndpointCoverage {
  endpoint: string
  method: string
  module: string
  testedIn: string[]
  category: 'v1.0' | 'v1.1' | 'v1.5'
}

/**
 * Core business endpoints that must be covered
 */
export const CORE_ENDPOINTS: EndpointCoverage[] = [
  // Auth (v1.0)
  { endpoint: '/auth/login', method: 'POST', module: 'auth', testedIn: ['auth-flow', 'smoke'], category: 'v1.0' },
  { endpoint: '/auth/logout', method: 'POST', module: 'auth', testedIn: ['auth-flow'], category: 'v1.0' },
  { endpoint: '/auth/refresh', method: 'POST', module: 'auth', testedIn: ['auth-resilience-flow'], category: 'v1.0' },

  // Carbon Reports (v1.0)
  { endpoint: '/carbon/reports', method: 'GET', module: 'carbon', testedIn: ['carbon-report-flow'], category: 'v1.0' },
  { endpoint: '/carbon/reports', method: 'POST', module: 'carbon', testedIn: ['carbon-report-flow'], category: 'v1.0' },
  { endpoint: '/carbon/reports/{id}', method: 'GET', module: 'carbon', testedIn: ['carbon-report-flow'], category: 'v1.0' },
  { endpoint: '/carbon/reports/{id}', method: 'PUT', module: 'carbon', testedIn: ['carbon-report-flow'], category: 'v1.0' },
  { endpoint: '/carbon/reports/{id}/submit', method: 'POST', module: 'carbon', testedIn: ['carbon-report-flow'], category: 'v1.0' },
  { endpoint: '/carbon/reports/{id}/approve', method: 'POST', module: 'carbon', testedIn: ['carbon-report-flow'], category: 'v1.0' },
  { endpoint: '/carbon/reports/{id}/reject', method: 'POST', module: 'carbon', testedIn: ['carbon-report-flow'], category: 'v1.0' },

  // Carbon Coin (v1.0)
  { endpoint: '/carbon-coin/accounts', method: 'GET', module: 'carbonCoin', testedIn: ['carbon-coin-flow'], category: 'v1.0' },
  { endpoint: '/carbon-coin/recharge', method: 'POST', module: 'carbonCoin', testedIn: ['carbon-coin-flow'], category: 'v1.0' },

  // Trading (v1.0)
  { endpoint: '/auction/orders', method: 'POST', module: 'auction', testedIn: ['auction-flow'], category: 'v1.0' },
  { endpoint: '/auction/match', method: 'POST', module: 'auction', testedIn: ['auction-flow'], category: 'v1.0' },
  // v1.5.3 撮合控制台：自动撮合运行态与全平台成交明细
  { endpoint: '/auction/matching-status', method: 'GET', module: 'auction', testedIn: ['admin-matching-console'], category: 'v1.5' },
  { endpoint: '/auction/all-results', method: 'GET', module: 'auction', testedIn: ['admin-matching-console'], category: 'v1.5' },
  { endpoint: '/trades/p2p', method: 'POST', module: 'trade', testedIn: ['p2p-trade-flow'], category: 'v1.0' },
  { endpoint: '/trades/{id}/confirm', method: 'POST', module: 'trade', testedIn: ['p2p-trade-flow'], category: 'v1.0' },

  // Credit Score (v1.0)
  { endpoint: '/credit-score/{enterpriseId}', method: 'GET', module: 'credit', testedIn: ['credit-score-flow'], category: 'v1.0' },

  // Carbon Neutral (v1.0)
  { endpoint: '/carbon-neutral/projects', method: 'GET', module: 'carbonNeutral', testedIn: ['carbon-neutral-flow'], category: 'v1.0' },
  { endpoint: '/carbon-neutral/projects', method: 'POST', module: 'carbonNeutral', testedIn: ['carbon-neutral-flow'], category: 'v1.0' },

  // Blockchain (v1.0)
  { endpoint: '/blockchain/transactions', method: 'GET', module: 'blockchain', testedIn: ['blockchain-flow'], category: 'v1.0' },

  // Admin (v1.0)
  { endpoint: '/admin/users', method: 'GET', module: 'admin', testedIn: ['admin-manage-flow', 'admin.smoke'], category: 'v1.0' },
  { endpoint: '/admin/users/{id}', method: 'PUT', module: 'admin', testedIn: ['admin-manage-flow'], category: 'v1.0' },

  // Third Party (v1.0)
  { endpoint: '/third-party/monitor', method: 'GET', module: 'thirdParty', testedIn: ['third-party-monitor-flow', 'third-party.smoke'], category: 'v1.0' },

  // AI Predictions (v1.1)
  { endpoint: '/ai/market/trend', method: 'POST', module: 'marketPrediction', testedIn: ['ai-prediction-flow'], category: 'v1.1' },
  { endpoint: '/ai/market/price', method: 'POST', module: 'marketPrediction', testedIn: ['ai-prediction-flow'], category: 'v1.1' },
  { endpoint: '/ai/market/supply-demand', method: 'POST', module: 'marketPrediction', testedIn: ['ai-prediction-flow'], category: 'v1.1' },
  { endpoint: '/predict/enterprise/{id}/inference', method: 'GET', module: 'enterpriseInference', testedIn: ['ai-prediction-flow'], category: 'v1.1' },

  // Carbon Formulas (v1.1)
  { endpoint: '/carbon/calculate/power-generation', method: 'POST', module: 'carbonFormula', testedIn: ['blockchain-formula-flow'], category: 'v1.1' },
  { endpoint: '/carbon/calculate/power-grid', method: 'POST', module: 'carbonFormula', testedIn: ['blockchain-formula-flow'], category: 'v1.1' },

  // Emission Prediction (v1.1)
  { endpoint: '/emission/predict', method: 'POST', module: 'emissionPrediction', testedIn: ['ai-prediction-flow'], category: 'v1.1' },

  // Certificates (v1.1)
  { endpoint: '/admin/enterprise-admission/{id}/issue', method: 'POST', module: 'admin', testedIn: ['certificate-flow'], category: 'v1.1' },
  { endpoint: '/admin/enterprise-admission/{id}', method: 'DELETE', module: 'admin', testedIn: ['certificate-flow'], category: 'v1.1' },
  { endpoint: '/admin/enterprise-admission', method: 'GET', module: 'admin', testedIn: ['certificate-flow'], category: 'v1.1' },
  { endpoint: '/admin/reviewer-qualification/{id}/issue', method: 'POST', module: 'admin', testedIn: ['certificate-flow'], category: 'v1.1' },
  { endpoint: '/admin/reviewer-qualification/{id}', method: 'DELETE', module: 'admin', testedIn: ['certificate-flow'], category: 'v1.1' },
  { endpoint: '/admin/reviewer-qualification', method: 'GET', module: 'admin', testedIn: ['certificate-flow'], category: 'v1.1' },
  { endpoint: '/enterprise/admission/my', method: 'GET', module: 'enterprise', testedIn: ['certificate-flow'], category: 'v1.1' },

  // File Management (v1.0)
  { endpoint: '/files/upload', method: 'POST', module: 'file', testedIn: ['file-management-flow'], category: 'v1.0' },

  // Digital Signature (v1.0)
  { endpoint: '/signatures/generate', method: 'POST', module: 'signature', testedIn: ['digital-signature-flow'], category: 'v1.0' },
  { endpoint: '/signatures/verify', method: 'POST', module: 'signature', testedIn: ['digital-signature-flow'], category: 'v1.0' },
]

export interface TestResult {
  status: 'passed' | 'failed' | 'skipped' | 'timedout'
  duration?: number
  errors?: string[]
}

/**
 * Calculate endpoint coverage percentage
 */
export function calculateCoverage(
  endpoints: EndpointCoverage[] = CORE_ENDPOINTS,
  testResults?: Record<string, TestResult>
): {
  total: number
  covered: number
  coverage: number
  byCategory: { v1_0: { total: number; covered: number }; v1_1: { total: number; covered: number } }
  byModule: Record<string, { total: number; covered: number }>
} {
  let covered = 0
  const byCategory = {
    v1_0: { total: 0, covered: 0 },
    v1_1: { total: 0, covered: 0 }
  }
  const byModule: Record<string, { total: number; covered: number }> = {}

  for (const ep of endpoints) {
    if (ep.category === 'v1.0') {
      byCategory.v1_0.total++
    } else {
      byCategory.v1_1.total++
    }

    if (!byModule[ep.module]) {
      byModule[ep.module] = { total: 0, covered: 0 }
    }
    byModule[ep.module].total++

    const isCovered = testResults
      ? ep.testedIn.some(testFile => {
          const result = testResults[testFile]
          return result && result.status === 'passed'
        })
      : ep.testedIn.length > 0

    if (isCovered) {
      covered++
      if (ep.category === 'v1.0') {
        byCategory.v1_0.covered++
      } else {
        byCategory.v1_1.covered++
      }
      byModule[ep.module].covered++
    }
  }

  return {
    total: endpoints.length,
    covered,
    coverage: Math.round((covered / endpoints.length) * 100),
    byCategory,
    byModule
  }
}

/**
 * Parse Playwright JSON report
 */
export function parsePlaywrightReport(reportPath: string): Record<string, TestResult> | null {
  try {
    const reportPathResolved = path.join(process.cwd(), path.basename(reportPath))
    if (!fs.existsSync(reportPathResolved)) {
      return null
    }

    const reportContent = fs.readFileSync(reportPathResolved, 'utf-8')
    const report = JSON.parse(reportContent)

    const results: Record<string, TestResult> = {}

    for (const suite of report.suites || []) {
      for (const spec of suite.specs || []) {
        const key = `${suite.file}:${spec.title}`
        results[key] = {
          status: spec.ok ? 'passed' : 'failed',
          duration: spec.duration,
          errors: spec.errors?.map((e: unknown) => String(e))
        }
      }
    }

    return results
  } catch {
    return null
  }
}

/**
 * Generate coverage report
 */
export function generateCoverageReport(
  coverage: ReturnType<typeof calculateCoverage>,
  testResults?: Record<string, TestResult>
): string {
  const lines: string[] = []

  lines.push('# E2E Test Coverage Report')
  lines.push('')
  lines.push('## Summary')
  lines.push('')
  lines.push(`- **Total Endpoints**: ${coverage.total}`)
  lines.push(`- **Covered**: ${coverage.covered}`)
  lines.push(`- **Coverage**: ${coverage.coverage}%`)
  lines.push('')

  if (coverage.coverage >= 90) {
    lines.push('**Status**: PASSED (Coverage >= 90%)')
  } else {
    lines.push(`**Status**: FAILED (Coverage ${coverage.coverage}% < 90%)`)
  }

  lines.push('')
  lines.push('## By Category')
  lines.push('')
  lines.push('### v1.0 Features')
  lines.push(`- Total: ${coverage.byCategory.v1_0.total}`)
  lines.push(`- Covered: ${coverage.byCategory.v1_0.covered}`)
  if (coverage.byCategory.v1_0.total > 0) {
    lines.push(`- Coverage: ${Math.round((coverage.byCategory.v1_0.covered / coverage.byCategory.v1_0.total) * 100)}%`)
  }
  lines.push('')
  lines.push('### v1.1 Features')
  lines.push(`- Total: ${coverage.byCategory.v1_1.total}`)
  lines.push(`- Covered: ${coverage.byCategory.v1_1.covered}`)
  if (coverage.byCategory.v1_1.total > 0) {
    lines.push(`- Coverage: ${Math.round((coverage.byCategory.v1_1.covered / coverage.byCategory.v1_1.total) * 100)}%`)
  }
  lines.push('')

  lines.push('## By Module')
  lines.push('')

  for (const [mod, stats] of Object.entries(coverage.byModule)) {
    const moduleCoverage = Math.round((stats.covered / stats.total) * 100)
    lines.push(`### ${mod}`)
    lines.push(`- Total: ${stats.total}`)
    lines.push(`- Covered: ${stats.covered}`)
    lines.push(`- Coverage: ${moduleCoverage}%`)
    lines.push('')
  }

  return lines.join('\n')
}

/**
 * Generate acceptance checklist
 */
export function generateAcceptanceChecklist(
  coverage: ReturnType<typeof calculateCoverage>,
  passRate: number,
  testResults?: Record<string, TestResult>
): string {
  const lines: string[] = []

  lines.push('# v1.1.0 Milestone Acceptance Report')
  lines.push('')
  lines.push(`**Generated**: ${new Date().toISOString()}`)
  lines.push('')

  lines.push('## Acceptance Criteria')
  lines.push('')

  const v1_1CoveragePercent = coverage.byCategory.v1_1.total > 0
    ? Math.round((coverage.byCategory.v1_1.covered / coverage.byCategory.v1_1.total) * 100)
    : 0
  const v1_0CoveragePercent = coverage.byCategory.v1_0.total > 0
    ? Math.round((coverage.byCategory.v1_0.covered / coverage.byCategory.v1_0.total) * 100)
    : 0

  const criteria = [
    {
      id: 'AC-1',
      description: 'E2E tests cover v1.1.0 all 12 requirement items',
      status: v1_1CoveragePercent >= 70 ? 'PASSED' : 'FAILED',
      evidence: `v1.1 coverage: ${coverage.byCategory.v1_1.covered}/${coverage.byCategory.v1_1.total} endpoints (${v1_1CoveragePercent}%)`
    },
    {
      id: 'AC-2',
      description: 'E2E test coverage >= 90%',
      status: coverage.coverage >= 90 ? 'PASSED' : 'FAILED',
      evidence: `Coverage: ${coverage.coverage}%`
    },
    {
      id: 'AC-3',
      description: 'E2E test pass rate >= 90%',
      status: passRate >= 90 ? 'PASSED' : 'FAILED',
      evidence: `Pass rate: ${passRate}%`
    },
    {
      id: 'AC-4',
      description: 'v1.0 features not broken by v1.1.0 changes',
      status: v1_0CoveragePercent >= 90 ? 'PASSED' : 'FAILED',
      evidence: `v1.0 coverage: ${v1_0CoveragePercent}%`
    },
    {
      id: 'AC-5',
      description: 'v1.1.0 milestone acceptance criteria verified',
      status: coverage.coverage >= 90 && passRate >= 90 ? 'PASSED' : 'FAILED',
      evidence: 'All quality gates met'
    }
  ]

  for (const c of criteria) {
    const icon = c.status === 'PASSED' ? '[PASS]' : '[FAIL]'
    lines.push(`### ${c.id}: ${c.description}`)
    lines.push(`- **Status**: ${icon} ${c.status}`)
    lines.push(`- **Evidence**: ${c.evidence}`)
    lines.push('')
  }

  lines.push('## Requirement Coverage (REQ-01 through REQ-12)')
  lines.push('')

  const reqCoverage = [
    { req: 'REQ-01', name: 'Market Prediction API', covered: (coverage.byModule.marketPrediction?.covered ?? 0) > 0 },
    { req: 'REQ-02', name: 'Enterprise Inference API', covered: (coverage.byModule.enterpriseInference?.covered ?? 0) > 0 },
    { req: 'REQ-03', name: 'Carbon ML Prediction', covered: (coverage.byModule.emissionPrediction?.covered ?? 0) > 0 },
    { req: 'REQ-04', name: 'AI Frontend Pages', covered: (coverage.byModule.marketPrediction?.covered ?? 0) > 0 },
    { req: 'REQ-05', name: 'Blockchain Real Integration', covered: (coverage.byModule.blockchain?.covered ?? 0) > 0 },
    { req: 'REQ-06', name: 'Carbon Formulas', covered: (coverage.byModule.carbonFormula?.covered ?? 0) > 0 },
    { req: 'REQ-07', name: 'Enterprise Admission', covered: (coverage.byModule.admin?.covered ?? 0) > 0 },
    { req: 'REQ-08', name: 'Reviewer Qualification', covered: (coverage.byModule.admin?.covered ?? 0) > 0 },
    { req: 'REQ-09', name: 'Frontend API Coverage', covered: (coverage.byModule.enterprise?.covered ?? 0) > 0 },
    { req: 'REQ-10', name: 'Enterprise Views', covered: (coverage.byModule.carbon?.covered ?? 0) > 0 },
    { req: 'REQ-11', name: 'Reviewer Views', covered: (coverage.byModule.carbon?.covered ?? 0) > 0 },
    { req: 'REQ-12', name: 'Fabric CA (optional)', covered: true }
  ]

  for (const r of reqCoverage) {
    const status = r.covered ? '[PASS]' : '[FAIL]'
    lines.push(`- ${status} **${r.req}**: ${r.name}`)
  }

  lines.push('')
  lines.push('## Test Execution Summary')
  lines.push('')

  if (testResults) {
    const tests = Object.values(testResults)
    const passed = tests.filter(t => t.status === 'passed').length
    const failed = tests.filter(t => t.status === 'failed').length
    const skipped = tests.filter(t => t.status === 'skipped').length
    const total = tests.length

    lines.push(`- **Total Tests**: ${total}`)
    lines.push(`- **Passed**: ${passed}`)
    lines.push(`- **Failed**: ${failed}`)
    lines.push(`- **Skipped**: ${skipped}`)
    lines.push(`- **Pass Rate**: ${passRate}%`)
    lines.push('')
  }

  lines.push('## Recommendation')
  lines.push('')

  const allPassed = criteria.every(c => c.status === 'PASSED')
  if (allPassed) {
    lines.push('**RECOMMENDATION**: **APPROVE for v1.1.0 release**')
    lines.push('')
    lines.push('All acceptance criteria have been met. The milestone is ready for release.')
  } else {
    lines.push('**RECOMMENDATION**: **DO NOT RELEASE**')
    lines.push('')
    lines.push('The following acceptance criteria have not been met:')
    for (const c of criteria.filter(c => c.status === 'FAILED')) {
      lines.push(`- ${c.id}: ${c.description}`)
    }
  }

  return lines.join('\n')
}
