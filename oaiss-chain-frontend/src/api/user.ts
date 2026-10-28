/**
 * ==============================================================================
 * 【API 模块】：用户中心与个人资料安全管理 (User API)
 * 【对应后端】：com.oaiss.chain.controller.UserController (/user)
 * 【主要服务页面】：
 *   - 个人中心页面: views/enterprise/UserProfile.vue (/enterprise/user/profile)
 *   - 全局会话活跃度统计: layout/index.vue
 * ==============================================================================
 */
import request from './request'
import type { PasswordChangeRequest } from '../types/auth'
import type { StayTimeStats, UserInfoResponse, UserProfileUpdateRequest } from '../types/user'

/**
 * 【业务功能】：查询当前登录用户的个人基本档案与安全配置
 * 【对应前端页面】：UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：进入个人中心页面自动加载个人资料卡片
 * 【后端对应端点】：UserController -> GET /user/profile
 */
export function getProfile(): Promise<UserInfoResponse> {
  return request.get('/user/profile')
}

/**
 * 【业务功能】：修改当前用户的个人资料（姓名、联系方式、头像等）
 * 【对应前端页面】：UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：在基本信息表单修改后，点击【保存修改】按钮
 * 【后端对应端点】：UserController -> PUT /user/profile
 */
export function updateProfile(data: UserProfileUpdateRequest): Promise<UserInfoResponse> {
  if (!data) return Promise.reject(new Error('Profile update payload is required'))
  return request.put('/user/profile', data)
}

/**
 * 【业务功能】：用户安全修改登录密码
 * 【对应前端页面】：UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：在“安全设置”卡片输入原密码与新密码后，点击【确认修改密码】按钮
 * 【后端对应端点】：UserController -> PUT /user/password
 */
export function changePassword(data: PasswordChangeRequest): Promise<void> {
  if (!data?.oldPassword) return Promise.reject(new Error('Current password is required'))
  if (!data?.newPassword || data.newPassword.length < 6) {
    return Promise.reject(new Error('New password must be at least 6 characters'))
  }
  return request.put('/user/password', data)
}

/**
 * 【业务功能】：根据用户ID查询指定用户的公开基本信息
 * 【对应前端页面】：系统管理用户详情、各端审计人查看弹窗
 * 【界面触发操作】：查看审核员或用户详情时触发
 * 【后端对应端点】：UserController -> GET /user/{userId}
 */
export function getUserById(userId: number): Promise<UserInfoResponse> {
  if (!userId) return Promise.reject(new Error('User ID is required'))
  return request.get(`/user/${userId}`)
}

/**
 * 【业务功能】：校验用户名是否已被注册占用（唯一性校验）
 * 【对应前端页面】：Login.vue (/login 注册Tab)
 * 【界面触发操作】：注册表单用户名输入框失焦 (blur) 时自动防重检查
 * 【后端对应端点】：UserController -> GET /user/check-username
 */
export function checkUsername(username: string): Promise<boolean> {
  if (!username) return Promise.reject(new Error('Username is required'))
  return request.get('/user/check-username', { params: { username } })
}

/**
 * 【业务功能】：校验电子邮箱是否已被绑定（唯一性校验）
 * 【对应前端页面】：Login.vue (/login 注册Tab), UserProfile.vue
 * 【界面触发操作】：邮箱输入框失焦时自动触发校验
 * 【后端对应端点】：UserController -> GET /user/check-email
 */
export function checkEmail(email: string): Promise<boolean> {
  if (!email) return Promise.reject(new Error('Email is required'))
  return request.get('/user/check-email', { params: { email } })
}

/**
 * 【业务功能】：定时上报用户当前页面会话停留活跃时长
 * 【对应前端页面】：全局布局 layout/index.vue, UserProfile.vue
 * 【界面触发操作】：前端心跳定时器静默上报当前在线停留秒数
 * 【后端对应端点】：UserController -> POST /user/stay-time
 */
export function reportStayTime(seconds: number): Promise<void> {
  return request.post('/user/stay-time', null, { params: { seconds }, suppressErrorMessage: true })
}

/**
 * 【业务功能】：查询当前用户/系统维度的在线停留时长与活跃度指标
 * 【对应前端页面】：UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：个人中心卡片展示用户累计在线时长
 * 【后端对应端点】：UserController -> GET /user/stay-time
 */
export function getStayTimeStats(): Promise<StayTimeStats> {
  return request.get('/user/stay-time')
}
