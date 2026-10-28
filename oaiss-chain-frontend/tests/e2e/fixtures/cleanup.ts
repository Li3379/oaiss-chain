import { request, type APIRequestContext } from '@playwright/test'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const ADMIN_TOKEN = process.env.E2E_ADMIN_TOKEN || ''

let sharedContext: APIRequestContext | null = null

async function getRequestContext(): Promise<APIRequestContext> {
  if (!sharedContext) {
    sharedContext = await request.newContext({
      baseURL: API_BASE,
      timeout: 10000,
    })
  }
  return sharedContext
}

/**
 * Generate unique prefix for test data to prevent collisions between
 * parallel or sequential test runs.
 */
export function generateUniquePrefix(testName: string): string {
  const timestamp = Date.now()
  const random = Math.random().toString(36).substring(2, 6)
  return `E2E-${testName}-${timestamp}-${random}`
}

/**
 * Clean up test data by entity type and ID.
 * Requires E2E_ADMIN_TOKEN environment variable for authentication.
 */
export async function cleanupTestData(
  entityType: string,
  ids: number[],
): Promise<void> {
  if (!ADMIN_TOKEN) {
    console.warn('E2E_ADMIN_TOKEN not set, skipping cleanup')
    return
  }

  const ctx = await getRequestContext()
  for (const id of ids) {
    try {
      await ctx.delete(`/admin/test-data/${entityType}/${id}`, {
        headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
      })
    } catch (error) {
      console.warn(`Failed to cleanup ${entityType}#${id}:`, error)
    }
  }
}

/**
 * Dispose of the shared request context.
 * Call in afterAll or teardown to release resources.
 */
export async function disposeCleanupContext(): Promise<void> {
  if (sharedContext) {
    await sharedContext.dispose()
    sharedContext = null
  }
}
