import { chromium } from 'playwright';
import { writeFileSync, mkdirSync, existsSync } from 'fs';
import { join } from 'path';

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';
const API_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1';
const SCREENSHOT_DIR = 'docs/qa-screenshots';
const EXPECTED_BRAND_MARKERS = ['OAISS CHAIN', '双碳链动', '碳'];

if (!existsSync(SCREENSHOT_DIR)) mkdirSync(SCREENSHOT_DIR, { recursive: true });

const findings = [];
let findingId = 0;

function log(level, category, message, page = '') {
  const id = ++findingId;
  const entry = { id, level, category, message, page };
  findings.push(entry);
  const icon = { CRITICAL: '🔴', HIGH: '🟠', MEDIUM: '🟡', LOW: '🔵', INFO: 'ℹ️' }[level] || '•';
  console.log(`${icon} [${category}] ${page ? `(${page}) ` : ''}${message}`);
}

async function screenshot(page, name) {
  const path = join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function verifyAppIdentity(page, locationLabel) {
  const title = await page.title();
  const bodyText = await page.locator('body').innerText().catch(() => '');
  const hasExpectedMarker = EXPECTED_BRAND_MARKERS.some((marker) =>
    title.includes(marker) || bodyText.includes(marker),
  );

  if (!hasExpectedMarker) {
    log(
      'CRITICAL',
      'Identity',
      `${locationLabel}: unexpected app fingerprint (title="${title}")`,
      page.url(),
    );
  } else {
    log('INFO', 'Identity', `${locationLabel}: app fingerprint OK (${title})`, page.url());
  }
}

// API login helper (captcha optional)
async function apiLogin(username, password) {
  const resp = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const data = await resp.json();
  if (resp.ok && data.data?.accessToken) return data.data;
  return null;
}

// Inject tokens into browser context
// Frontend uses 'access_token'/'refresh_token' as storage keys (auth.ts)
// API returns 'accessToken'/'refreshToken' in response body
async function injectAuth(page, tokens) {
  await page.evaluate((t) => {
    localStorage.setItem('access_token', t.accessToken);
    localStorage.setItem('refresh_token', t.refreshToken);
    localStorage.setItem('remember_me', 'true');
    // Also set legacy keys for safety
    sessionStorage.setItem('access_token', t.accessToken);
  }, tokens);
}

// ===== Phase 1: Smoke Test =====
async function phase1_smokeTest(browser) {
  console.log('\n' + '='.repeat(60));
  console.log('Phase 1: SMOKE TEST');
  console.log('='.repeat(60));

  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  const consoleErrors = [];
  const networkErrors = [];

  page.on('console', msg => {
    if (msg.type() === 'error') {
      const text = msg.text();
      if (!text.includes('favicon') && !text.includes('analytics') && !text.includes('Download the React DevTools') && !text.includes('[HMR]')) {
        consoleErrors.push(text);
      }
    }
  });
  page.on('pageerror', err => consoleErrors.push(err.message));
  page.on('response', resp => {
    const status = resp.status();
    const url = resp.url();
    if (status >= 400 && !url.includes('/auth/login') && !url.includes('/auth/captcha') && !url.includes('favicon') && !url.includes('.ico') && !url.includes('hot-update')) {
      networkErrors.push({ url, status });
    }
  });
  page.on('requestfailed', req => {
    const url = req.url();
    if (!url.includes('hot-update') && !url.includes('favicon')) {
      networkErrors.push({ url, error: req.failure()?.errorText });
    }
  });

  // Test 1.1: Homepage
  console.log('\n--- 1.1 Homepage ---');
  try {
    await page.goto(BASE_URL, { waitUntil: 'networkidle', timeout: 15000 });
    log('INFO', 'Smoke', `Homepage loaded: ${page.url()}`);
    await verifyAppIdentity(page, 'homepage');
  } catch (e) {
    log('HIGH', 'Smoke', `Homepage failed: ${e.message}`);
  }
  await screenshot(page, '01-homepage');

  // Test 1.2: Login page
  console.log('\n--- 1.2 Login Page ---');
  try {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    log('INFO', 'Smoke', 'Login page loaded');
    await verifyAppIdentity(page, 'login');
  } catch (e) {
    log('HIGH', 'Smoke', `Login page failed: ${e.message}`);
  }
  await screenshot(page, '02-login-page');

  // Test 1.3: Official home
  console.log('\n--- 1.3 Official Home ---');
  try {
    await page.goto(`${BASE_URL}/official-home`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    log('INFO', 'Smoke', 'Official home loaded');
  } catch (e) {
    log('MEDIUM', 'Smoke', `Official home failed: ${e.message}`);
  }
  await screenshot(page, '03-official-home');

  // Test 1.4: Responsive viewports
  console.log('\n--- 1.4 Responsive ---');
  for (const [name, vp] of [['mobile', { width: 375, height: 812 }], ['tablet', { width: 768, height: 1024 }]]) {
    await page.setViewportSize(vp);
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(500);
    await screenshot(page, `04-login-${name}`);
    log('INFO', 'Smoke', `Screenshot: ${name} (${vp.width}x${vp.height})`);
  }
  await page.setViewportSize({ width: 1440, height: 900 });

  // Test 1.5: Console errors summary
  console.log('\n--- 1.5 Console Errors ---');
  if (consoleErrors.length > 0) {
    for (const err of consoleErrors.slice(0, 5)) {
      log('MEDIUM', 'Smoke', `Console: ${err.substring(0, 200)}`);
    }
  } else {
    log('INFO', 'Smoke', 'No console errors');
  }

  // Test 1.6: Network errors summary
  if (networkErrors.length > 0) {
    for (const err of networkErrors.slice(0, 5)) {
      log('MEDIUM', 'Smoke', `Network: ${err.status || err.error} → ${err.url}`);
    }
  } else {
    log('INFO', 'Smoke', 'No network errors');
  }

  await context.close();
}

// ===== Phase 2: Interaction Test =====
async function phase2_interactionTest(browser) {
  console.log('\n' + '='.repeat(60));
  console.log('Phase 2: INTERACTION TEST');
  console.log('='.repeat(60));

  // Test 2.1: Login page form elements
  console.log('\n--- 2.1 Login Form Elements ---');
  const loginCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const loginPage = await loginCtx.newPage();

  const allConsoleErrors = [];
  const allNetworkErrors = [];

  loginPage.on('console', msg => {
    if (msg.type() === 'error') {
      const text = msg.text();
      if (!text.includes('favicon') && !text.includes('analytics') && !text.includes('HMR') && !text.includes('DevTools')) {
        allConsoleErrors.push({ page: loginPage.url(), text: text.substring(0, 200) });
      }
    }
  });
  loginPage.on('pageerror', err => allConsoleErrors.push({ page: loginPage.url(), text: err.message.substring(0, 200) }));
  loginPage.on('response', resp => {
    const status = resp.status();
    const url = resp.url();
    if (status >= 400 && !url.includes('/auth/login') && !url.includes('/auth/captcha') && !url.includes('favicon') && !url.includes('hot-update')) {
      allNetworkErrors.push({ page: loginPage.url(), url, status });
    }
  });

  await loginPage.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
  await loginPage.waitForTimeout(1500);

  // Check form elements
  const formElements = {
    accountInput: await loginPage.locator('input').first().count(),
    passwordInput: await loginPage.locator('input[type="password"]').first().count(),
    captchaInput: await loginPage.locator('.captcha-row input, input[placeholder*="验证"], input[placeholder*="captcha"]').first().count(),
    captchaImage: await loginPage.locator('.captcha-image, img[class*="captcha"]').first().count(),
    submitButton: await loginPage.locator('button[type="submit"], button.submit-btn, .el-button--primary').first().count(),
    rememberMe: await loginPage.locator('.el-checkbox, input[type="checkbox"]').first().count(),
  };

  if (formElements.accountInput > 0) log('INFO', 'Interaction', 'Login: account input found');
  else log('HIGH', 'Interaction', 'Login: account input NOT found');

  if (formElements.passwordInput > 0) log('INFO', 'Interaction', 'Login: password input found');
  else log('HIGH', 'Interaction', 'Login: password input NOT found');

  if (formElements.captchaImage > 0) log('INFO', 'Interaction', 'Login: captcha image found');
  else log('MEDIUM', 'Interaction', 'Login: captcha image NOT found');

  if (formElements.submitButton > 0) log('INFO', 'Interaction', 'Login: submit button found');
  else log('HIGH', 'Interaction', 'Login: submit button NOT found');

  await screenshot(loginPage, '05-login-form-elements');

  // Test 2.2: Empty form submission
  console.log('\n--- 2.2 Empty Form Submission ---');
  try {
    const submitBtn = loginPage.locator('button[type="submit"], button.submit-btn, .el-button--primary').first();
    if (await submitBtn.count() > 0) {
      await submitBtn.click();
      await loginPage.waitForTimeout(1000);
      // Check for validation error messages
      const errorMessages = await loginPage.locator('.el-form-item__error, .el-message--error, .el-message--warning').allTextContents();
      if (errorMessages.length > 0) {
        log('INFO', 'Interaction', `Validation errors shown: ${errorMessages.join(', ')}`);
      } else {
        log('MEDIUM', 'Interaction', 'Empty form submit: no validation errors shown');
      }
      await screenshot(loginPage, '06-login-empty-submit');
    }
  } catch (e) {
    log('MEDIUM', 'Interaction', `Empty submit error: ${e.message}`);
  }

  // Test 2.3: API Login and navigate to authenticated pages
  console.log('\n--- 2.3 Authenticated Pages ---');

  const testAccounts = [
    { username: 'admin', password: 'admin123', role: 'ADMIN' },
    { username: 'enterprise001', password: 'admin123', role: 'ENTERPRISE' },
    { username: 'reviewer001', password: 'admin123', role: 'REVIEWER' },
    { username: 'thirdparty001', password: 'admin123', role: 'THIRD_PARTY' },
  ];

  let workingTokens = {};

  for (const account of testAccounts) {
    const tokens = await apiLogin(account.username, account.password);
    if (tokens) {
      workingTokens[account.role] = tokens;
      log('INFO', 'Interaction', `API login OK: ${account.username} (${account.role})`);
    } else {
      log('INFO', 'Interaction', `API login failed: ${account.username} (${account.role})`);
    }
  }

  // Test authenticated pages for each role
  const rolePages = {
    ENTERPRISE: [
      { path: '/enterprise/carbon/upload', name: 'carbon-upload' },
      { path: '/enterprise/trading/market', name: 'trading-market' },
      { path: '/enterprise/credit/score', name: 'credit-score' },
      { path: '/enterprise/carbon-coin/account', name: 'carbon-coin' },
      { path: '/enterprise/carbon-neutral/projects', name: 'carbon-neutral' },
      { path: '/enterprise/emission/data', name: 'emission-data' },
      { path: '/enterprise/market-prediction', name: 'market-prediction' },
      { path: '/enterprise/user/profile', name: 'user-profile' },
    ],
    ADMIN: [
      { path: '/admin/system/users', name: 'admin-users' },
      { path: '/admin/system/carbon', name: 'admin-carbon' },
      { path: '/admin/certificates', name: 'admin-certificates' },
      { path: '/admin/verify/list', name: 'admin-verify' },
      { path: '/admin/system/config', name: 'admin-config' },
      { path: '/admin/data/statistics', name: 'admin-statistics' },
    ],
    REVIEWER: [
      { path: '/auditor/audit/list', name: 'audit-list' },
      { path: '/auditor/review/history', name: 'review-history' },
      { path: '/auditor/project/review', name: 'project-review' },
    ],
  };

  for (const [role, pages] of Object.entries(rolePages)) {
    if (!workingTokens[role]) {
      log('INFO', 'Interaction', `Skipping ${role} pages - no token`);
      continue;
    }

    console.log(`\n--- ${role} Pages ---`);
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const pg = await ctx.newPage();

    // Collect errors for this context
    const pageErrors = [];
    pg.on('console', msg => {
      if (msg.type() === 'error') {
        const text = msg.text();
        if (!text.includes('favicon') && !text.includes('HMR') && !text.includes('DevTools')) {
          pageErrors.push(text.substring(0, 200));
        }
      }
    });
    pg.on('pageerror', err => pageErrors.push(err.message.substring(0, 200)));

    // Navigate to login first to set up routing, then inject token
    await pg.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    await pg.waitForTimeout(500);
    await injectAuth(pg, workingTokens[role]);

    for (const { path, name } of pages) {
      try {
        await pg.goto(`${BASE_URL}${path}`, { waitUntil: 'networkidle', timeout: 15000 });
        await pg.waitForTimeout(1500);
        const finalUrl = pg.url();

        if (finalUrl.includes('/login')) {
          log('HIGH', 'Interaction', `${role} ${path}: redirected to login (auth failed)`);
        } else {
          log('INFO', 'Interaction', `${role} ${path}: loaded OK`);
        }

        // Check for page-specific issues
        const elErrors = await pg.locator('.el-message--error:visible').count();
        if (elErrors > 0) {
          log('MEDIUM', 'Interaction', `${name}: ${elErrors} error message(s) visible`);
        }

        await screenshot(pg, `${role.toLowerCase()}-${name}`);
      } catch (e) {
        log('MEDIUM', 'Interaction', `${role} ${path}: ${e.message}`);
      }
    }

    // Report console errors for this role
    if (pageErrors.length > 0) {
      const uniqueErrors = [...new Set(pageErrors)];
      for (const err of uniqueErrors.slice(0, 5)) {
        log('MEDIUM', 'Interaction', `${role} console: ${err}`);
      }
    }

    await ctx.close();
  }

  // Test 2.4: THIRD_PARTY role
  if (workingTokens.THIRD_PARTY) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const pg = await ctx.newPage();
    await pg.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    await injectAuth(pg, workingTokens.THIRD_PARTY);

    const tpPages = [
      { path: '/third-party/monitor', name: 'monitor' },
    ];
    for (const { path, name } of tpPages) {
      try {
        await pg.goto(`${BASE_URL}${path}`, { waitUntil: 'networkidle', timeout: 15000 });
        await pg.waitForTimeout(1000);
        log('INFO', 'Interaction', `THIRD_PARTY ${path}: loaded`);
        await screenshot(pg, `third-party-${name}`);
      } catch (e) {
        log('MEDIUM', 'Interaction', `THIRD_PARTY ${path}: ${e.message}`);
      }
    }
    await ctx.close();
  }

  // Test 2.5: Form validation on login page
  console.log('\n--- 2.5 Form Validation ---');
  const valCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const valPage = await valCtx.newPage();
  await valPage.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
  await valPage.waitForTimeout(1000);

  // Fill only username, submit
  const usernameInput = valPage.locator('input').first();
  if (await usernameInput.count() > 0) {
    await usernameInput.fill('testuser');
    const submitBtn = valPage.locator('button[type="submit"], button.submit-btn, .el-button--primary').first();
    if (await submitBtn.count() > 0) {
      await submitBtn.click();
      await valPage.waitForTimeout(1000);
      const errors = await valPage.locator('.el-form-item__error').allTextContents();
      if (errors.length > 0) {
        log('INFO', 'Interaction', `Partial form validation: ${errors.join(', ')}`);
      } else {
        log('MEDIUM', 'Interaction', 'Partial form submit: no field-level validation');
      }
      await screenshot(valPage, '07-login-partial-submit');
    }
  }

  await valCtx.close();

  // Summary
  console.log('\n--- 2.6 Error Summary ---');
  if (allConsoleErrors.length > 0) {
    const unique = [...new Set(allConsoleErrors.map(e => e.text))];
    for (const err of unique.slice(0, 5)) {
      log('MEDIUM', 'Interaction', `Console: ${err}`);
    }
  }
  if (allNetworkErrors.length > 0) {
    for (const err of allNetworkErrors.slice(0, 5)) {
      log('HIGH', 'Interaction', `Network: ${err.status} → ${err.url}`);
    }
  }

  await loginCtx.close();
}

// ===== Phase 3: Visual Regression =====
async function phase3_visualRegression(browser) {
  console.log('\n' + '='.repeat(60));
  console.log('Phase 3: VISUAL REGRESSION');
  console.log('='.repeat(60));

  const pages_to_test = [
    { path: '/login', name: 'login' },
    { path: '/official-home', name: 'official-home' },
  ];

  for (const { path, name } of pages_to_test) {
    for (const [vpName, vp] of [['mobile', { width: 375, height: 812 }], ['tablet', { width: 768, height: 1024 }], ['desktop', { width: 1440, height: 900 }]]) {
      const ctx = await browser.newContext({ viewport: vp });
      const page = await ctx.newPage();
      try {
        await page.goto(`${BASE_URL}${path}`, { waitUntil: 'networkidle', timeout: 15000 });
        await page.waitForTimeout(1000);
        await screenshot(page, `visual-${name}-${vpName}`);

        // Check horizontal overflow
        const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
        if (bodyWidth > vp.width + 5) {
          log('MEDIUM', 'Visual', `${name} (${vpName}): horizontal overflow (body:${bodyWidth}px > viewport:${vp.width}px)`);
        } else {
          log('INFO', 'Visual', `${name} (${vpName}): no overflow`);
        }

        // Check for overlapping elements (basic check)
        const fixedElements = await page.evaluate(() => {
          const els = document.querySelectorAll('*');
          let count = 0;
          for (const el of els) {
            const style = window.getComputedStyle(el);
            if (style.position === 'fixed' || style.position === 'sticky') count++;
          }
          return count;
        });
        if (fixedElements > 5) {
          log('LOW', 'Visual', `${name} (${vpName}): ${fixedElements} fixed/sticky elements (may cause layout issues)`);
        }
      } catch (e) {
        log('MEDIUM', 'Visual', `${name} (${vpName}): ${e.message}`);
      }
      await ctx.close();
    }
  }
}

// ===== Phase 4: Accessibility =====
async function phase4_accessibility(browser) {
  console.log('\n' + '='.repeat(60));
  console.log('Phase 4: ACCESSIBILITY');
  console.log('='.repeat(60));

  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();

  // Test login page accessibility
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1000);

  // 4.1: Form labels
  console.log('\n--- 4.1 Form Labels ---');
  const inputs = await page.locator('input:visible').all();
  let missingLabel = 0;
  for (const input of inputs) {
    const id = await input.getAttribute('id');
    const ariaLabel = await input.getAttribute('aria-label');
    const placeholder = await input.getAttribute('placeholder');
    const hasLabel = id ? await page.locator(`label[for="${id}"]`).count() > 0 : false;
    if (!hasLabel && !ariaLabel && !placeholder) missingLabel++;
  }
  if (missingLabel > 0) {
    log('MEDIUM', 'A11y', `${missingLabel}/${inputs.length} inputs missing labels/aria-label/placeholder`);
  } else {
    log('INFO', 'A11y', `All ${inputs.length} inputs have labels or placeholders`);
  }

  // 4.2: Image alt texts
  console.log('\n--- 4.2 Image Alt Texts ---');
  const images = await page.locator('img:visible').all();
  let missingAlt = 0;
  for (const img of images) {
    const alt = await img.getAttribute('alt');
    if (!alt) missingAlt++;
  }
  if (missingAlt > 0) {
    log('MEDIUM', 'A11y', `${missingAlt}/${images.length} images missing alt text`);
  } else {
    log('INFO', 'A11y', `All ${images.length} images have alt text`);
  }

  // 4.3: Button accessible names
  console.log('\n--- 4.3 Button Names ---');
  const buttons = await page.locator('button:visible').all();
  let unnamed = 0;
  for (const btn of buttons) {
    const text = await btn.textContent();
    const ariaLabel = await btn.getAttribute('aria-label');
    if (!text?.trim() && !ariaLabel) unnamed++;
  }
  if (unnamed > 0) {
    log('MEDIUM', 'A11y', `${unnamed}/${buttons.length} buttons without accessible name`);
  } else {
    log('INFO', 'A11y', `All ${buttons.length} buttons have accessible name`);
  }

  // 4.4: Heading hierarchy
  console.log('\n--- 4.4 Heading Hierarchy ---');
  const headings = await page.locator('h1, h2, h3, h4, h5, h6').all();
  const headingLevels = [];
  for (const h of headings) {
    const level = parseInt(await h.evaluate(el => el.tagName.charAt(1)));
    headingLevels.push(level);
  }
  if (headingLevels.length > 0) {
    let issues = 0;
    for (let i = 1; i < headingLevels.length; i++) {
      if (headingLevels[i] > headingLevels[i - 1] + 1) issues++;
    }
    if (issues > 0) {
      log('LOW', 'A11y', `Heading hierarchy: ${issues} skipped levels`);
    } else {
      log('INFO', 'A11y', `Heading hierarchy OK (${headings.length} headings)`);
    }
  }

  // 4.5: Focus order
  console.log('\n--- 4.5 Focus Order ---');
  const focusable = await page.locator('button:visible, a:visible, input:visible, select:visible, [tabindex]:visible').count();
  log('INFO', 'A11y', `${focusable} focusable elements on login page`);

  // 4.6: ARIA landmarks
  console.log('\n--- 4.6 ARIA Landmarks ---');
  const landmarks = await page.evaluate(() => {
    const roleToNative = { banner: 'header', navigation: 'nav', main: 'main', contentinfo: 'footer' };
    const roles = ['banner', 'navigation', 'main', 'contentinfo', 'complementary', 'search'];
    const found = [];
    for (const role of roles) {
      const selector = `[role="${role}"]`;
      const native = roleToNative[role];
      const fullSelector = native ? `${selector}, ${native}` : selector;
      if (document.querySelector(fullSelector)) {
        found.push(role);
      }
    }
    return found;
  });
  if (landmarks.length >= 2) {
    log('INFO', 'A11y', `ARIA landmarks found: ${landmarks.join(', ')}`);
  } else {
    log('LOW', 'A11y', `Few ARIA landmarks found: ${landmarks.join(', ') || 'none'}`);
  }

  // 4.7: Color contrast (basic)
  console.log('\n--- 4.7 Basic Checks ---');
  const bgColor = await page.evaluate(() => {
    return window.getComputedStyle(document.body).backgroundColor;
  });
  const textColor = await page.evaluate(() => {
    return window.getComputedStyle(document.body).color;
  });
  log('INFO', 'A11y', `Body colors: bg=${bgColor}, text=${textColor}`);

  await ctx.close();
}

// ===== Main =====
async function main() {
  console.log('╔══════════════════════════════════════════════════════════╗');
  console.log('║     OAISS CHAIN — Browser QA Acceptance Test           ║');
  console.log('║     ' + new Date().toISOString().replace('T', ' ').substring(0, 48).padEnd(48) + '║');
  console.log('╚══════════════════════════════════════════════════════════╝');

  const browser = await chromium.launch({ headless: true });

  try {
    await phase1_smokeTest(browser);
    await phase2_interactionTest(browser);
    await phase3_visualRegression(browser);
    await phase4_accessibility(browser);
  } catch (e) {
    log('CRITICAL', 'System', `Fatal error: ${e.message}`);
  } finally {
    await browser.close();
  }

  // Summary
  console.log('\n' + '='.repeat(60));
  console.log('SUMMARY');
  console.log('='.repeat(60));

  const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 };
  for (const f of findings) counts[f.level] = (counts[f.level] || 0) + 1;

  console.log(`🔴 CRITICAL: ${counts.CRITICAL}`);
  console.log(`🟠 HIGH:     ${counts.HIGH}`);
  console.log(`🟡 MEDIUM:   ${counts.MEDIUM}`);
  console.log(`🔵 LOW:      ${counts.LOW}`);
  console.log(`ℹ️  INFO:     ${counts.INFO}`);
  console.log(`Total: ${findings.length}`);

  // Write report
  let report = `# Browser QA Report — OAISS CHAIN\n\n`;
  report += `**Date**: ${new Date().toISOString()}\n`;
  report += `**URL**: ${BASE_URL}\n`;
  report += `**Tool**: Playwright ${process.env.PLAYWRIGHT_VERSION || '1.60.0'} (headless Chromium)\n\n`;
  report += `## Summary\n\n`;
  report += `| Level | Count |\n|-------|-------|\n`;
  for (const [level, count] of Object.entries(counts)) {
    if (count > 0) report += `| ${level} | ${count} |\n`;
  }

  report += `\n## Findings\n\n`;
  report += `| # | Level | Category | Page | Description |\n|---|-------|----------|------|-------------|\n`;
  for (const f of findings) {
    if (f.level !== 'INFO') {
      report += `| ${f.id} | ${f.level} | ${f.category} | ${f.page || '-'} | ${f.message.replace(/\|/g, '\\|').replace(/\n/g, ' ')} |\n`;
    }
  }

  report += `\n## Screenshots\n\n`;
  report += `Screenshots saved to: \`${SCREENSHOT_DIR}/\`\n`;

  const reportDate = new Date().toISOString().slice(0, 10);
  const reportFile = `docs/browser-qa-report-${reportDate}.md`;
  writeFileSync(reportFile, report);
  console.log(`\nReport: ${reportFile}`);
  console.log(`Screenshots: ${SCREENSHOT_DIR}/`);
}

main().catch(e => {
  console.error('Fatal:', e);
  process.exit(1);
});
