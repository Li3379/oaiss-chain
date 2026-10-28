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
