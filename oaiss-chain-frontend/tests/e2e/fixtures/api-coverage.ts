import { request as playwrightRequest, type APIRequestContext } from '@playwright/test'
import * as fs from 'fs'
import * as path from 'path'

import { API_BASE_URL } from './api-base'

// 见 api-base.ts：baseURL 需以 / 结尾，否则 /api/v1 会被 Playwright 丢掉。
const API_BASE = API_BASE_URL

/**
 * Map of frontend API modules to their endpoint patterns.
 * Tracks v1.0 + v1.1 endpoints covered by frontend API modules.
 *
 * Source: oaiss-chain-frontend/src/api/*.ts
 */
export const API_MODULE_COVERAGE: Record<string, string[]> = {
  // v1.1 AI modules
  marketPrediction: [
    'POST /ai/market/trend',
    'POST /ai/market/price',
    'POST /ai/market/supply-demand',
  ],
  enterpriseInference: [
    'GET /predict/enterprise/{id}/inference',
  ],
  carbonFormula: [
    'POST /carbon/calculate/power-generation',
    'POST /carbon/calculate/power-grid',
  ],

  // v1.1 enterprise module
  enterprise: [
    'GET /enterprise/info',
    'GET /enterprise/quota',
    'PUT /enterprise/contact',
    'GET /enterprise/admission/my',
    'GET /enterprise/{id}',
  ],

  // v1.1 reviewer module
  reviewer: [
    'GET /reviewer/info',
    'GET /reviewer/reports/pending',
    'GET /reviewer/history',
    'GET /reviewer/qualification/my',
    'GET /reviewer/statistics',
  ],

  // v1.1 admin certificate endpoints
  adminCertificates: [
    'GET /admin/enterprise-admission',
    'POST /admin/enterprise-admission/{id}/issue',
    'DELETE /admin/enterprise-admission/{id}',
    'GET /admin/reviewer-qualification',
    'POST /admin/reviewer-qualification/{id}/issue',
    'DELETE /admin/reviewer-qualification/{id}',
  ],

  // v1.0 core APIs
  carbon: [
    'GET /carbon/reports',
    'POST /carbon/reports',
    'GET /carbon/reports/{id}',
    'PUT /carbon/reports/{id}',
    'DELETE /carbon/reports/{id}',
  ],
  auth: [
    'POST /auth/login',
    'POST /auth/logout',
    'POST /auth/refresh',
  ],
}

/**
 * Verify that an API endpoint exists and is accessible.
 * Returns true for any response status < 500 (even 4xx means the route exists).
 */
export async function verifyEndpointExists(
  method: string,
  endpointPath: string,
  token?: string,
): Promise<{ exists: boolean; status?: number; error?: string }> {
  let context: APIRequestContext | null = null
  try {
    context = await playwrightRequest.newContext({
      baseURL: API_BASE,
      timeout: 5000,
    })

    const headers: Record<string, string> = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    let response
    switch (method.toUpperCase()) {
      case 'GET':
        response = await context.get(endpointPath, { headers })
        break
      case 'POST':
        response = await context.post(endpointPath, { headers, data: {} })
        break
      case 'PUT':
        response = await context.put(endpointPath, { headers, data: {} })
        break
      case 'DELETE':
        response = await context.delete(endpointPath, { headers })
        break
      default:
        return { exists: false, error: `Unknown method: ${method}` }
    }

    const status = response.status()
    // 200-499 means the endpoint exists (route matched, even if auth/params rejected)
    // 500+ could mean endpoint missing or server error
    const exists = status < 500

    return { exists, status }
  } catch (error) {
    return { exists: false, error: String(error) }
  } finally {
    await context?.dispose()
  }
}

/**
 * Verify coverage for a specific API module.
 */
export async function verifyApiModuleCoverage(
  moduleName: string,
  token?: string,
): Promise<{
  module: string
  endpoints: number
  covered: number
  results: Array<{ endpoint: string; exists: boolean; status?: number }>
}> {
  const endpoints = API_MODULE_COVERAGE[moduleName] || []
  const results: Array<{ endpoint: string; exists: boolean; status?: number }> = []

  for (const endpoint of endpoints) {
    const [method, endpointPath] = endpoint.split(' ')
    const result = await verifyEndpointExists(method, endpointPath, token)
    results.push({ endpoint, exists: result.exists, status: result.status })
  }

  const covered = results.filter((r) => r.exists).length

  return {
    module: moduleName,
    endpoints: endpoints.length,
    covered,
    results,
  }
}

/**
 * Get Swagger OpenAPI spec from backend SpringDoc endpoint.
 */
export async function getSwaggerSpec(): Promise<Record<string, unknown> | null> {
  let context: APIRequestContext | null = null
  try {
    // Swagger 文档路径由 application.yml 的 springdoc.api-docs.path 决定，
    // 本项目配置为 /v1/api-docs（不是 Springdoc 默认的 /v3/api-docs）。
    // 叠加 context-path 后实际地址是 /api/v1/v1/api-docs —— 实测 200。
    // 早先请求 /v3/api-docs 得到 404，导致「Swagger paths: 0」而后端明明有文档。
    context = await playwrightRequest.newContext({
      baseURL: API_BASE,
      timeout: 5000,
    })
    // 该端点要求认证：无 token 返回 401，而 catch 会把失败吞成 null，
    // 表现为「Swagger paths: 0」——看起来像后端没文档，实际是没登录。
    const login = await context.post('auth/login', {
      data: {
        username: process.env.ADMIN_USERNAME || 'admin',
        password: process.env.ADMIN_PASSWORD || 'admin123',
      },
    })
    const headers: Record<string, string> = {}
    if (login.ok()) {
      const token = (await login.json())?.data?.accessToken
      if (token) headers['Authorization'] = `Bearer ${token}`
    }

    const response = await context.get('v1/api-docs', { headers })
    if (response.ok()) {
      return await response.json()
    }
    return null
  } catch {
    return null
  } finally {
    await context?.dispose()
  }
}

/**
 * Compare Swagger paths with frontend API modules.
 */
export async function compareSwaggerWithApiModules(): Promise<{
  swaggerPaths: number
  frontendModules: number
  coverage: number
  missingInFrontend: string[]
}> {
  const swagger = await getSwaggerSpec()
  if (!swagger || !swagger.paths) {
    return {
      swaggerPaths: 0,
      frontendModules: 0,
      coverage: 0,
      missingInFrontend: [],
    }
  }

  const paths = swagger.paths as Record<string, unknown>
  const swaggerPathKeys = Object.keys(paths)

  // Count unique frontend endpoints
  let frontendModules = 0
  for (const endpoints of Object.values(API_MODULE_COVERAGE)) {
    frontendModules += endpoints.length
  }

  // Find Swagger paths not covered by any frontend module
  const missingInFrontend: string[] = []
  for (const swaggerPath of swaggerPathKeys) {
    const normalizedPath = swaggerPath.replace(/^\/api\/v1/, '')

    const hasMatchingModule = Object.values(API_MODULE_COVERAGE).some((endpoints) =>
      endpoints.some((e) => {
        const epPath = e.replace(/^(GET|POST|PUT|DELETE)\s+/, '').replace(/\{[^}]+\}/, '[^/]+')
        const normalized = normalizedPath.replace(/\{[^}]+\}/, '[^/]+')
        return normalizedPath.includes(epPath.split('/')[1]) || normalized.includes(epPath)
      }),
    )

    if (!hasMatchingModule && !normalizedPath.includes('/auth/')) {
      missingInFrontend.push(swaggerPath)
    }
  }

  const coverage =
    swaggerPathKeys.length > 0 ? Math.round((frontendModules / swaggerPathKeys.length) * 100) : 0

  return {
    swaggerPaths: swaggerPathKeys.length,
    frontendModules,
    coverage,
    missingInFrontend,
  }
}

/**
 * Read a frontend API module file and extract endpoint patterns
 * by parsing request.get/post/put/delete calls.
 */
export function extractEndpointsFromApiModule(filePath: string): string[] {
  try {
    // cwd 在 playwright 运行时就是 oaiss-chain-frontend 目录，不能再拼一层
    const fullPath = path.join(process.cwd(), 'src', 'api', path.basename(filePath))
    const content = fs.readFileSync(fullPath, 'utf-8')

    // Match request.get/post/put/delete calls with string literal paths
    const requestRegex = /request\.(get|post|put|delete)\s*\(\s*[`'"]([^`'"]+)[`'"]/g
    const endpoints: string[] = []
    let match

    while ((match = requestRegex.exec(content)) !== null) {
      endpoints.push(`${match[1].toUpperCase()} ${match[2]}`)
    }

    return endpoints
  } catch {
    return []
  }
}
