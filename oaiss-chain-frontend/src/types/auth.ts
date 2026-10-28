/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
  captcha?: string
  captchaKey?: string
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  password: string
  confirmPassword: string
  phone?: string
  email?: string
  realName?: string
  userType: number
  creditCode?: string
  enterpriseName?: string
  qualificationNo?: string
  orgCode?: string
  orgName?: string
}

/** 登录响应 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  userType: number
  realName: string
}

/** 验证码响应 */
export interface CaptchaResponse {
  captchaKey: string
  captchaImage: string
  expiresIn: number
}

/** 发送验证码请求 */
export interface CaptchaSendRequest {
  target: string
  type?: number
}

/** 修改密码请求 */
export interface PasswordChangeRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

/** JWT用户详情 */
export interface JwtUserDetails {
  userId: number
  username: string
  userType: number
  roles: string[]
  enterpriseId: number
  enabled: boolean
}
