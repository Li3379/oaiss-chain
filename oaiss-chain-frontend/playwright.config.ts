import { defineConfig, devices } from '@playwright/test'

const isFlowMode = process.env.TEST_MODE === 'flow'
const isV11Mode = process.env.TEST_MODE === 'v1.1'
const DEFAULT_WEB_BASE_URL = 'http://127.0.0.1:5173'
const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'

// Normalize local defaults once so every spec/fixture reading process.env
// uses the same loopback address as Playwright's auto-started web server.
process.env.BASE_URL ||= DEFAULT_WEB_BASE_URL
process.env.API_BASE_URL ||= DEFAULT_API_BASE_URL

const webBaseUrl = process.env.BASE_URL

export default defineConfig({
  testDir: isV11Mode
    ? './tests/e2e/v1.1'
    : isFlowMode
      ? './tests/e2e/flows'
      : './tests/e2e/smoke',
  // CI 中排除人工排查用的探针与调试脚本。
  //
  // `*-probe.spec.ts` 与 `*debug*.spec.ts` 是开发期用来「对着真实后端数据
  // 逐项核对」的脚本：它们假设库里有特定数据、会打印大量诊断输出、
  // 个别还会等待长超时（focus-debug 单条就要跑 10 分钟，直接吃掉整个
  // 30 分钟 job 预算，导致运行被 cancelled）。
  //
  // 它们在本地手工跑有价值，但放进自动回归只会带来噪音与超时。
  //
  // 同理排除 full-functional.spec.ts：它在**单条用例**里串行跑 83 个子场景
  // （test.setTimeout(45 * 60 * 1000)），实测 17.6 分钟。叠上前面的构建步骤
  // 必然撞穿 30 分钟 job 上限，整个 run 会被 cancelled——前面所有通过的
  // 步骤都拿不到最终结论。它是全量验收脚本，不是回归测试，
  // 由 e2e-full-matrix.yml 手动触发。
  //
  // CI 的回归保障是各角色的 *.smoke.spec.ts（5 个文件 35 项）。
  // 用显式开关而非「CI 是否为空」：后者依赖「空字符串为假」的隐式语义，
  // workflow 里一旦写成 CI=false 就会静默失效（字符串 "false" 是真值）。
  //
  // 两个开关刻意分开：
  //   E2E_INCLUDE_LONG   放行 full-functional（全矩阵验收，~18 分钟）
  //   E2E_INCLUDE_PROBES 放行 *-probe / *debug*（人工探针，依赖特定数据）
  // 只想要全矩阵时不该被迫连探针一起跑。
  testIgnore: (() => {
    const ignored = []
    if (!process.env.E2E_INCLUDE_PROBES) {
      ignored.push('**/*-probe.spec.ts', '**/*debug*.spec.ts')
    }
    if (!process.env.E2E_INCLUDE_LONG) {
      ignored.push('**/full-functional.spec.ts')
    }
    return process.env.CI ? ignored : []
  })(),
  timeout: isV11Mode ? 45000 : isFlowMode ? 30000 : 15000,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    [
      'html',
      {
        open: 'never',
        outputFolder: isV11Mode ? 'playwright-report/v1.1' : 'playwright-report',
      },
    ],
    ['list'],
  ],
  use: {
    baseURL: webBaseUrl,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: isFlowMode || isV11Mode ? 'on-first-retry' : 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer:
    isFlowMode || isV11Mode
      ? undefined
      : {
          command: 'npx vite --host 127.0.0.1 --port 5173 --strictPort',
          url: webBaseUrl,
          reuseExistingServer: !process.env.CI,
          timeout: 30000,
        },
})
