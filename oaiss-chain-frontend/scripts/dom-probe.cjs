// Quick DOM probe - run with: node scripts/dom-probe.js
const { chromium } = require('playwright')

;(async () => {
  const BASE_URL = process.env.BASE_URL || 'http://localhost:5174'
  const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
  const browser = await chromium.launch()
  const page = await browser.newPage()

  // Login via API
  const loginResp = await page.request.post(`${API_BASE}/auth/login`, {
    headers: { 'Content-Type': 'application/json' },
    data: { username: 'enterprise001', password: 'admin123' },
  })
  const loginData = await loginResp.json()
  const token = loginData.data.accessToken

  // Set tokens and navigate
  await page.goto(BASE_URL)
  await page.evaluate(({ t }) => {
    localStorage.clear()
    sessionStorage.clear()
    localStorage.setItem('access_token', t)
    localStorage.setItem('refresh_token', 'r')
    localStorage.setItem('remember_me', 'true')
  }, { t: token })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2000)

  // Go to dashboard
  await page.goto(`${BASE_URL}/enterprise/company/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)

  // Inspect DOM
  const result = await page.evaluate(() => {
    const overviewCards = document.querySelectorAll('.overview-card')
    const metricValues = document.querySelectorAll('.metric-value')
    const overviewGrid = document.querySelector('.overview-grid')
    const loadingEl = document.querySelector('.loading-container')

    return {
      overviewCardsCount: overviewCards.length,
      metricValuesCount: metricValues.length,
      overviewGridExists: !!overviewGrid,
      loadingExists: !!loadingEl,
      overviewGridHTML: overviewGrid ? overviewGrid.outerHTML.substring(0, 1000) : 'NOT FOUND',
      allCardClasses: Array.from(document.querySelectorAll('.el-card')).map(e => e.className.substring(0, 100)),
    }
  })

  console.log(JSON.stringify(result, null, 2))
  await browser.close()
})()
