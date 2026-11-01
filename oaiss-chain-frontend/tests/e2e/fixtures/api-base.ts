/**
 * API 基址规范化。
 *
 * Playwright 的 `APIRequestContext.newContext({ baseURL })` 按 **RFC 3986 相对引用**
 * 解析路径：baseURL 的最后一段会被当作「文件名」，被后续的相对路径替换掉。
 *
 * 实测（这是本项目踩过的真实坑）：
 *   baseURL = 'http://host:8080/api/v1'
 *   ctx.get('/actuator/health')  → http://host:8080/actuator/health     ← /api/v1 丢了
 *   ctx.get('actuator/health')   → http://host:8080/api/actuator/health ← v1 丢了
 *
 * 于是登录请求打到 `http://host:8080/auth/login`，得到 Tomcat 的 404 HTML，
 * 上层 `response.json()` 抛 `SyntaxError: Unexpected token '<', "<!doctype "...`。
 * 报错完全指不出根因——看起来像接口返回了 HTML，实际是 URL 拼错了。
 *
 * 修复很简单：baseURL 必须以 `/` 结尾，此时它被当作目录，
 * 相对路径在它之下解析：
 *   baseURL = 'http://host:8080/api/v1/' + '/actuator/health'
 *          → http://host:8080/api/v1/actuator/health   ✓
 *
 * 所有构造 `newContext({ baseURL: ... })` 的地方都必须走这里。
 */

/** 把 API 基址规范化为「以 / 结尾」的形式 */
export function normalizeApiBase(raw: string): string {
  return raw.endsWith('/') ? raw : `${raw}/`
}

/** 环境变量或默认值，已规范化 */
export const API_BASE_URL: string = normalizeApiBase(
  process.env.API_BASE_URL || 'http://localhost:8080/api/v1',
)