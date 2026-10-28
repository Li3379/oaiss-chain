/**
 * ==============================================================================
 * 【API 模块】：身份认证与访问控制 (Auth API)
 * 【对应后端】：com.oaiss.chain.controller.AuthController (/auth)
 * 【主要服务页面】：
 *   - 登录页面: views/Login.vue (/login)
 *   - 顶部导航登出: layout/index.vue
 *   - 全局路由守卫与鉴权: router/index.ts, stores/user.ts
 * ==============================================================================
 */
import request from './request'
import type { JwtUserDetails, LoginRequest, LoginResponse, RegisterRequest } from '../types'
import { t } from '@/i18n'

/**
 * 【业务功能】：用户账号密码认证登录，签发双 Token (Access + Refresh Token)
 * 【对应前端页面】：Login.vue (/login)
 * 【界面触发操作】：输入账号、密码与验证码后，点击【登录】按钮或按回车键
 * 【后端对应端点】：AuthController -> POST /auth/login
 */
export function login(data: LoginRequest): Promise<LoginResponse> {
  return request.post('/auth/login', data)
}

/**
 * 【业务功能】：用户安全登出，注销服务端 Session 并将 Token 纳入黑名单
 * 【对应前端页面】：layout/index.vue (顶部导航栏个人下拉菜单)
 * 【界面触发操作】：点击右上角头像菜单中的【退出登录】选项
 * 【后端对应端点】：AuthController -> POST /auth/logout
 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}

/**
 * 【业务功能】：新用户或排碳企业账号注册申请
 * 【对应前端页面】：Login.vue (/login 注册Tab)
 * 【界面触发操作】：填写用户名、密码、角色与组织信息后点击【注册】按钮
 * 【后端对应端点】：AuthController -> POST /auth/register
 */
export function register(data: RegisterRequest): Promise<LoginResponse> {
  if (!data?.username) return Promise.reject(new Error(t('auth.usernameRequired')))
  if (!data?.password) return Promise.reject(new Error(t('auth.passwordRequired')))
  return request.post('/auth/register', data)
}

/**
 * 【业务功能】：客户端网络 IP 安全检查与白名单/风控验证
 * 【对应前端页面】：Login.vue (/login)
 * 【界面触发操作】：登录前置网络安全探针检查
 * 【后端对应端点】：AuthController -> GET /auth/check-ip
 */
export function checkIp(): Promise<boolean> {
  return request.get('/auth/check-ip')
}

/**
 * 【业务功能】：获取当前登录用户的上下文身份、权限与主体资料
 * 【对应前端页面】：全局初始化、stores/user.ts (恢复登录态)
 * 【界面触发操作】：页面刷新或登录成功后自动触发拉取当前用户信息
 * 【后端对应端点】：AuthController -> GET /auth/me
 */
export function getCurrentUser(): Promise<JwtUserDetails> {
  return request.get('/auth/me')
}
