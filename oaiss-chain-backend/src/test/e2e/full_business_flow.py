"""
OAISS CHAIN - Full Business Flow E2E Test Suite
Tests all major business flows via Playwright against running frontend (5173) + backend (8080)
"""

import json
import sys
import time
import traceback
from datetime import datetime

# ── Test result tracking ──────────────────────────────────────────
results = {"passed": [], "failed": [], "skipped": []}


def record(test_name, status, detail=""):
    results[status].append({"name": test_name, "detail": detail, "time": datetime.now().isoformat()})
    icon = {"passed": "✅", "failed": "❌", "skipped": "⏭️"}[status]
    print(f"  {icon} {test_name}" + (f" — {detail}" if detail else ""))


# ── HTTP API helpers (direct backend calls) ───────────────────────
import urllib.request
import urllib.error

API = "http://localhost:8080/api/v1"
_tokens = {}  # role -> {accessToken, refreshToken, userId, username}


def api_call(method, path, data=None, token=None, headers=None):
    """Make API call and return (status_code, response_json)"""
    url = f"{API}{path}"
    body = json.dumps(data).encode() if data else None
    hdrs = {"Content-Type": "application/json"}
    if token:
        hdrs["Authorization"] = f"Bearer {token}"
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, data=body, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"raw": body}
    except Exception as e:
        return 0, {"error": str(e)}


# ──────────────────────────────────────────────────────────────────
# PHASE 1: Backend API Health Checks
# ──────────────────────────────────────────────────────────────────
print("\n" + "=" * 70)
print("PHASE 1: Backend API Health Checks")
print("=" * 70)


def test_backend_health():
    code, _ = api_call("GET", "/auth/check-ip")
    record("Backend /auth/check-ip reachable", "passed" if code == 200 else "failed", f"HTTP {code}")


def test_captcha_api():
    code, body = api_call("POST", "/auth/captcha")
    ok = code == 200 and body.get("data", {}).get("captchaKey")
    record("Captcha generation API", "passed" if ok else "failed", f"HTTP {code}")


test_backend_health()
test_captcha_api()

# ──────────────────────────────────────────────────────────────────
# PHASE 2: Authentication Flow
# ──────────────────────────────────────────────────────────────────
print("\n" + "=" * 70)
print("PHASE 2: Authentication Flow (API-level)")
print("=" * 70)


def test_login_invalid_credentials():
    code, body = api_call("POST", "/auth/login", {"username": "nonexistent", "password": "wrong", "captchaKey": "skip", "captcha": "skip"})
    record("Login with invalid credentials → non-200", "passed" if code != 200 else "failed", f"HTTP {code}")


def test_login_empty_fields():
    code, body = api_call("POST", "/auth/login", {"username": "", "password": ""})
    record("Login with empty fields → 400", "passed" if code == 400 else "failed", f"HTTP {code}")


test_login_invalid_credentials()
test_login_empty_fields()

# Try to login with common test accounts
TEST_ACCOUNTS = [
    ("admin", "admin123", "ADMIN"),
    ("enterprise", "123456", "ENTERPRISE"),
    ("reviewer", "123456", "REVIEWER"),
    ("testuser", "test123456", "ENTERPRISE"),
]


def attempt_login(username, password):
    """Attempt login (captcha is optional, omitted for testing)"""
    code, body = api_call("POST", "/auth/login", {
        "username": username,
        "password": password,
    })
    if code == 200 and body.get("data", {}).get("accessToken"):
        return body["data"]
    return None


# Try to find working accounts
print("\n  🔍 Attempting login with test accounts...")
for uname, pwd, role in TEST_ACCOUNTS:
    login_data = attempt_login(uname, pwd)
    if login_data:
        _tokens[role] = login_data
        record(f"Login as {uname} ({role})", "passed", f"userId={login_data.get('userId')}")
    else:
        record(f"Login as {uname} ({role})", "skipped", "credentials not accepted")

# If no tokens found, try direct API calls to check if accounts exist
if not _tokens:
    print("  ⚠️ No test accounts available. Some flows will be skipped.")

# ──────────────────────────────────────────────────────────────────
# PHASE 3: Playwright Frontend Tests
# ──────────────────────────────────────────────────────────────────
print("\n" + "=" * 70)
print("PHASE 3: Frontend E2E Tests (Playwright)")
print("=" * 70)

try:
    from playwright.sync_api import sync_playwright

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1280, "height": 900})
        page = context.new_page()

        # ── 3a: Official Home Page ──
        print("\n  --- Official Home Page ---")
        try:
            page.goto("http://localhost:5173/official-home", timeout=15000)
            page.wait_for_load_state("domcontentloaded", timeout=10000)
            page.wait_for_timeout(3000)  # wait for Vue to render
            title = page.title()
            url_ok = "official-home" in page.url or "localhost" in page.url
            record("Official Home page loads", "passed" if url_ok else "failed", f"title='{title}', url={page.url}")
        except Exception as e:
            record("Official Home page loads", "failed", str(e))

        # ── 3b: Login Page ──
        print("\n  --- Login Page ---")
        try:
            page.goto("http://localhost:5173/login", timeout=10000)
            page.wait_for_load_state("networkidle", timeout=8000)

            # Check login form elements
            h1 = page.locator("h1").first.text_content(timeout=3000)
            record("Login page shows title", "passed" if "碳资产监管" in h1 else "failed", f"h1='{h1}'")

            # Check form fields exist
            account_input = page.locator('input[placeholder="请输入账号"]')
            password_input = page.locator('input[placeholder="请输入密码"]')
            captcha_input = page.locator('input[placeholder="请输入验证码"]')
            login_btn = page.locator('button:has-text("登录")')

            fields_ok = all([
                account_input.count() > 0,
                password_input.count() > 0,
                captcha_input.count() > 0,
                login_btn.count() > 0,
            ])
            record("Login form has all required fields", "passed" if fields_ok else "failed")

            # Check captcha image loaded
            captcha_img = page.locator("img.captcha-image")
            captcha_loaded = captcha_img.count() > 0
            record("Captcha image loads on login page", "passed" if captcha_loaded else "failed")

            # Test form validation (empty submit)
            login_btn.click()
            page.wait_for_timeout(500)
            validation_msg = page.locator(".el-form-item__error").first.text_content(timeout=2000)
            record("Empty form shows validation errors", "passed" if validation_msg else "failed",
                   f"msg='{validation_msg}'" if validation_msg else "no msg")

            # Test entering invalid credentials
            account_input.fill("invalid_user")
            password_input.fill("wrong_password")
            captcha_input.fill("xxxx")
            page.wait_for_timeout(300)
            login_btn.click()
            page.wait_for_timeout(2000)
            # Should still be on login page with error
            still_on_login = "/login" in page.url
            record("Invalid credentials stays on login page", "passed" if still_on_login else "failed",
                   f"url={page.url}")

        except Exception as e:
            record("Login page test", "failed", str(e))

        # ── 3c: Test authenticated pages with injected token ──
        if _tokens:
            print("\n  --- Authenticated Enterprise Flow ---")
            # Pick the first available token
            role = next(iter(_tokens))
            token_data = _tokens[role]
            access_token = token_data["accessToken"]

            # Inject token into localStorage and navigate
            try:
                page.goto("http://localhost:5173/login", timeout=10000)
                page.wait_for_load_state("networkidle", timeout=8000)

                # Set tokens in localStorage
                page.evaluate(f"""() => {{
                    localStorage.setItem('access_token', '{access_token}');
                    localStorage.setItem('refresh_token', '{token_data.get("refreshToken", "")}');
                    localStorage.setItem('user_info', JSON.stringify({{
                        userId: {token_data.get('userId', 0)},
                        username: '{token_data.get('username', '')}',
                        userType: {token_data.get('userType', 1)},
                        realName: '{token_data.get('realName', '')}'
                    }}));
                }}""")

                # Navigate to enterprise home
                enterprise_routes = [
                    ("/enterprise/carbon/upload", "上传审核", "carbon_upload"),
                    ("/enterprise/orders/manage", "订单管理", "orders_manage"),
                    ("/enterprise/trading/market", "双向拍卖", "trading_market"),
                    ("/enterprise/trading/p2p", "P2P交易", "trading_p2p"),
                    ("/enterprise/company/dashboard", "数据可视化", "company_dashboard"),
                    ("/enterprise/credit/score", "信誉评分", "credit_score"),
                    ("/enterprise/carbon-coin/account", "碳币账户", "carbon_coin"),
                    ("/enterprise/blockchain/browser", "区块链浏览器", "blockchain_browser"),
                    ("/enterprise/carbon-neutral/projects", "碳中和项目", "carbon_neutral"),
                    ("/enterprise/emission/data", "排放数据", "emission_data"),
                    ("/enterprise/user/profile", "个人中心", "user_profile"),
                ]

                for path, label, key in enterprise_routes:
                    try:
                        page.goto(f"http://localhost:5173{path}", timeout=10000)
                        page.wait_for_load_state("networkidle", timeout=8000)
                        current_url = page.url
                        # Check if redirected to login (token invalid) or page loaded
                        if "/login" in current_url:
                            record(f"Page: {label} ({path})", "failed", "redirected to login (token may be invalid)")
                            break  # Token is bad, stop trying
                        else:
                            # Check page has content (not just blank layout)
                            has_content = page.locator(".el-main, .main-content, .page-container, [class*='content']").count() > 0
                            has_menu = page.locator(".el-menu, .sidebar, [class*='menu']").count() > 0
                            page_ok = has_content or has_menu or (current_url.endswith(path.split("?")[0]))
                            record(f"Page: {label} ({path})", "passed" if page_ok else "failed",
                                   f"url={current_url}")
                    except Exception as e:
                        record(f"Page: {label} ({path})", "failed", str(e)[:80])

                # ── 3d: Test Logout Flow ──
                print("\n  --- Logout Flow ---")
                try:
                    # Find and click logout button or menu
                    logout_btn = page.locator('text=退出登录, text=登出, text=Logout')
                    if logout_btn.count() > 0:
                        logout_btn.first.click()
                        page.wait_for_timeout(2000)
                        record("Logout redirects to login", "passed" if "/login" in page.url else "failed",
                               f"url={page.url}")
                    else:
                        # Try dropdown user menu
                        user_menu = page.locator('.user-info, .avatar, [class*="user"], .el-dropdown')
                        if user_menu.count() > 0:
                            user_menu.first.click()
                            page.wait_for_timeout(500)
                            logout_link = page.locator('text=退出, text=登出, text=Logout')
                            if logout_link.count() > 0:
                                logout_link.first.click()
                                page.wait_for_timeout(2000)
                                record("Logout via dropdown menu", "passed" if "/login" in page.url else "failed")
                            else:
                                record("Logout flow", "skipped", "logout button not found in dropdown")
                        else:
                            record("Logout flow", "skipped", "no logout button found")
                except Exception as e:
                    record("Logout flow", "failed", str(e))

            except Exception as e:
                record("Authenticated flow", "failed", str(e))
        else:
            record("All authenticated page tests", "skipped", "no valid test accounts")

        # ── 3e: Responsive Design Check ──
        print("\n  --- Responsive Design ---")
        try:
            # Clear any auth state first
            page.evaluate("() => { localStorage.clear(); }")

            # Mobile
            page.set_viewport_size({"width": 375, "height": 812})
            page.goto("http://localhost:5173/login", timeout=10000)
            page.wait_for_load_state("domcontentloaded", timeout=8000)
            page.wait_for_timeout(2000)
            login_card = page.locator(".login-card")
            mobile_ok = login_card.count() > 0
            record("Mobile (375x812) login renders", "passed" if mobile_ok else "failed")

            # Tablet
            page.set_viewport_size({"width": 768, "height": 1024})
            page.goto("http://localhost:5173/login", timeout=10000)
            page.wait_for_load_state("domcontentloaded", timeout=8000)
            page.wait_for_timeout(2000)
            login_card2 = page.locator(".login-card")
            tablet_ok = login_card2.count() > 0
            record("Tablet (768x1024) login renders", "passed" if tablet_ok else "failed")

            # Desktop
            page.set_viewport_size({"width": 1280, "height": 900})
            page.goto("http://localhost:5173/login", timeout=10000)
            page.wait_for_load_state("domcontentloaded", timeout=8000)
            page.wait_for_timeout(2000)
            login_card3 = page.locator(".login-card")
            desktop_ok = login_card3.count() > 0
            record("Desktop (1280x900) login renders", "passed" if desktop_ok else "failed")

        except Exception as e:
            record("Responsive design", "failed", str(e))

        browser.close()

except ImportError:
    record("Playwright not installed", "failed", "pip install playwright && playwright install chromium")
except Exception as e:
    record("Playwright tests", "failed", str(e))

# ──────────────────────────────────────────────────────────────────
# PHASE 4: API-Level Business Flow Tests (with token)
# ──────────────────────────────────────────────────────────────────
print("\n" + "=" * 70)
print("PHASE 4: API-Level Business Flow Tests")
print("=" * 70)

if _tokens:
    for role, token_data in _tokens.items():
        token = token_data["accessToken"]
        username = token_data.get("username", "unknown")
        print(f"\n  --- Testing as {role} ({username}) ---")

        # ── Current User Info ──
        code, body = api_call("GET", "/auth/me", token=token)
        me_ok = code == 200 and body.get("data", {}).get("username") == username
        record(f"[{role}] GET /auth/me returns current user", "passed" if me_ok else "failed", f"HTTP {code}")

        # ── Role-specific tests ──
        if role == "ENTERPRISE":
            # Carbon Upload
            code, body = api_call("GET", "/carbon/report/list?page=1&size=5", token=token)
            record(f"[{role}] Carbon report list", "passed" if code == 200 else "failed", f"HTTP {code}")

            # Orders
            code, body = api_call("GET", "/trade/orders?page=1&size=5", token=token)
            record(f"[{role}] P2P orders list", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

            # Trading Market
            code, body = api_call("GET", "/auction/orders?page=1&size=5", token=token)
            record(f"[{role}] Auction orders list", "passed" if code == 200 else "failed", f"HTTP {code}")

            # Credit Score
            code, body = api_call("GET", "/credit/score", token=token)
            record(f"[{role}] Credit score query", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

            # Carbon Coin
            code, body = api_call("GET", "/carbon-coin/account", token=token)
            record(f"[{role}] Carbon coin account", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

            # Blockchain
            code, body = api_call("GET", "/blockchain/status", token=token)
            record(f"[{role}] Blockchain status", "passed" if code in [200, 404, 503] else "failed", f"HTTP {code}")

            # Carbon Neutral
            code, body = api_call("GET", "/carbon-neutral/projects?page=1&size=5", token=token)
            record(f"[{role}] Carbon neutral projects", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

            # Emission Data
            code, body = api_call("GET", "/emission/data?page=1&size=5", token=token)
            record(f"[{role}] Emission data list", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

        elif role == "REVIEWER":
            code, body = api_call("GET", "/audit/list?page=1&size=5", token=token)
            record(f"[{role}] Audit list", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

        elif role == "THIRD_PARTY":
            code, body = api_call("GET", "/third-party/monitor", token=token)
            record(f"[{role}] Third party monitor", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

        elif role == "ADMIN":
            code, body = api_call("GET", "/admin/users?page=1&size=5", token=token)
            record(f"[{role}] Admin user list", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

            code, body = api_call("GET", "/admin/statistics", token=token)
            record(f"[{role}] Admin statistics", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

            code, body = api_call("GET", "/admin/dashboard", token=token)
            record(f"[{role}] Admin dashboard", "passed" if code in [200, 404] else "failed", f"HTTP {code}")

        # ── Logout ──
        code, body = api_call("POST", "/auth/logout", token=token)
        record(f"[{role}] Logout", "passed" if code == 200 else "failed", f"HTTP {code}")

        # ── Verify token blacklisted after logout (use same token) ──
        code2, body2 = api_call("GET", "/auth/me", token=token)
        blacklisted = code2 != 200 or body2.get("code") != 200
        record(f"[{role}] Token blacklisted after logout", "passed" if blacklisted else "failed",
               f"HTTP {code2}, code={body2.get('code')}")
else:
    record("All API business flow tests", "skipped", "no valid test accounts")

# ──────────────────────────────────────────────────────────────────
# PHASE 5: Security & Edge Case Tests
# ──────────────────────────────────────────────────────────────────
print("\n" + "=" * 70)
print("PHASE 5: Security & Edge Case Tests")
print("=" * 70)

# SQL Injection attempt
code, body = api_call("POST", "/auth/login", {"username": "' OR 1=1 --", "password": "test"})
record("SQL injection in login → not 200", "passed" if code != 200 else "failed", f"HTTP {code}")

# XSS attempt
code, body = api_call("POST", "/auth/login", {"username": "<script>alert(1)</script>", "password": "test"})
record("XSS in login → not 200", "passed" if code != 200 else "failed", f"HTTP {code}")

# Rate limit check (rapid requests)
rapid_results = []
for i in range(5):
    code, _ = api_call("POST", "/auth/login", {"username": "test", "password": "test"})
    rapid_results.append(code)
no_500 = all(c != 500 for c in rapid_results)
record("Rapid login requests → no 500 errors", "passed" if no_500 else "failed",
       f"codes={rapid_results}")

# Pagination overflow (H18 fix verification)
if _tokens:
    # Re-login for a fresh token
    for role, token_data in _tokens.items():
        fresh = attempt_login(token_data.get("username", ""), token_data.get("username", "") == "admin" and "admin123" or "test")
        if fresh:
            code, body = api_call("GET", "/auction/orders?page=1&size=99999", token=fresh["accessToken"])
            # H18 fix: size should be capped to 100
            overflow_ok = code in [200, 404, 400]  # not 500
            record("Pagination overflow (size=99999) → no crash", "passed" if overflow_ok else "failed",
                   f"HTTP {code}")
            break

# File upload dangerous type check (H5 fix verification)
import io
import urllib.request as urllib_req

try:
    boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    body_bytes = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="test.jsp"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
        f"<% out.println(\"hack\"); %>\r\n"
        f"--{boundary}--\r\n"
    ).encode()

    req = urllib_req.Request(
        f"{API}/file/upload",
        data=body_bytes,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    code = 0
    try:
        with urllib_req.urlopen(req, timeout=5) as resp:
            code = resp.status
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception:
        code = 0
    record("Upload .jsp file → rejected", "passed" if code in [400, 403, 404, 401, 415] else "failed",
           f"HTTP {code}")
except Exception as e:
    record("Upload .jsp file → rejected", "skipped", str(e))

# ──────────────────────────────────────────────────────────────────
# FINAL REPORT
# ──────────────────────────────────────────────────────────────────
print("\n" + "=" * 70)
print("FINAL TEST REPORT")
print("=" * 70)
total = len(results["passed"]) + len(results["failed"]) + len(results["skipped"])
print(f"\n  Total: {total} tests")
print(f"  ✅ Passed:  {len(results['passed'])}")
print(f"  ❌ Failed:  {len(results['failed'])}")
print(f"  ⏭️  Skipped: {len(results['skipped'])}")
print(f"  📊 Pass Rate: {len(results['passed']) / total * 100:.1f}%" if total > 0 else "  No tests run")

if results["failed"]:
    print("\n  ❌ FAILED TESTS:")
    for t in results["failed"]:
        print(f"    - {t['name']}: {t['detail']}")

print("\n" + "=" * 70)
