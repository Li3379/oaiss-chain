import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { routeLocationKey, routerKey } from 'vue-router'
import i18n from '../i18n'

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = ResizeObserverStub as typeof ResizeObserver
}

if (typeof window !== 'undefined' && typeof window.matchMedia === 'undefined') {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
    dispatchEvent() {
      return false
    },
  })) as typeof window.matchMedia
}

config.global.plugins = [...(config.global.plugins || []), i18n, ElementPlus]
config.global.stubs = {
  ...(config.global.stubs || {}),
  transition: false,
  teleport: true,
  'router-link': {
    props: ['to'],
    template: '<a :href="typeof to === \'string\' ? to : to?.path || \'#\'"><slot /></a>',
  },
  'router-view': true,
}

// 组件内 useRouter()/useRoute() 通过 inject 取路由上下文；未挂载真实路由器的用例会触发
// "injection Symbol(router) not found" 告警。这里提供最小可用的路由替身，避免污染测试输出。
// 用例若自行 app.use(router)，其 app 级 provide 会覆盖本替身。
const resolved = Promise.resolve()
const stubRouteLocation = {
  path: '/',
  fullPath: '/',
  name: undefined,
  hash: '',
  query: {},
  params: {},
  meta: {},
  matched: [],
}

config.global.provide = {
  ...(config.global.provide || {}),
  [routerKey]: {
    currentRoute: { value: stubRouteLocation },
    options: {},
    push: () => resolved,
    replace: () => resolved,
    go: () => {},
    back: () => {},
    forward: () => {},
    resolve: (to: unknown) =>
      typeof to === 'string' ? { href: to, path: to } : { href: '#', path: '/' },
    addRoute: () => () => {},
    removeRoute: () => {},
    hasRoute: () => false,
    getRoutes: () => [],
    isReady: () => resolved,
    beforeEach: () => () => {},
    beforeResolve: () => () => {},
    afterEach: () => () => {},
  },
  [routeLocationKey]: stubRouteLocation,
}
