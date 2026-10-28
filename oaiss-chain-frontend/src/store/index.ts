import { createPinia, defineStore } from 'pinia'
import { ROLE, ROLE_HOME, ROLE_LABEL, type RoleType } from '../config/menu'
import { getAccessToken, setTokens, clearTokens, isTokenExpired, parseJwtPayload } from '../utils/auth'

export const pinia = createPinia()

const ROLE_VALUES = Object.values(ROLE) as RoleType[]
const USER_TYPE_TO_ROLE: Partial<Record<number, RoleType>> = {
  1: ROLE.ENTERPRISE,
  2: ROLE.REVIEWER,
  3: ROLE.THIRD_PARTY,
  4: ROLE.ADMIN,
}

interface UserState {
  loggedIn: boolean
  role: RoleType | null
  username: string
  userId: number | null
  enterpriseId: number | null
}

function createLoggedOutState(): UserState {
  return { loggedIn: false, role: null, username: '', userId: null, enterpriseId: null }
}

function isRoleType(value: unknown): value is RoleType {
  return typeof value === 'string' && ROLE_VALUES.includes(value as RoleType)
}

function extractUserFromToken(token: string): UserState | null {
  const payload = parseJwtPayload(token)
  if (!payload) return null

  const roles = Array.isArray(payload.roles) ? payload.roles : []
  const tokenRole = roles.find(isRoleType) ?? null
  const fallbackRole = typeof payload.userType === 'number'
    ? (USER_TYPE_TO_ROLE[payload.userType] ?? null)
    : null
  const role = tokenRole ?? fallbackRole
  if (!role) return null

  return {
    loggedIn: true,
    role,
    username: (payload.sub as string) || (payload.username as string) || '',
    userId: (payload.userId as number) || null,
    enterpriseId: (payload.enterpriseId as number) || null,
  }
}

function resolveInitialState(): UserState {
  const token = getAccessToken()
  if (!token || isTokenExpired(token)) {
    clearTokens()
    return createLoggedOutState()
  }

  const user = extractUserFromToken(token)
  if (!user) {
    clearTokens()
    return createLoggedOutState()
  }

  return user
}

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    systemTitle: 'layout.title',
    ...resolveInitialState(),
  }),
  getters: {
    roleLabel: (state) => (state.role ? ROLE_LABEL[state.role] : 'layout.notLoggedIn'),
    homePath: (state) => (state.role ? ROLE_HOME[state.role] : '/official-home'),
  },
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    login({ accessToken, refreshToken }: { accessToken: string; refreshToken?: string }) {
      const user = extractUserFromToken(accessToken)
      if (user) {
        setTokens(accessToken, refreshToken)
        this.loggedIn = user.loggedIn
        this.role = user.role
        this.username = user.username
        this.userId = user.userId
        this.enterpriseId = user.enterpriseId
        return true
      } else {
        this.logout()
        return false
      }
    },
    logout() {
      clearTokens()
      Object.assign(this, createLoggedOutState())
      this.sidebarCollapsed = false
    },
  },
})
