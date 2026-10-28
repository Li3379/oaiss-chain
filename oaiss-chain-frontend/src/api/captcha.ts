/**
 * ==============================================================================
 * 【API 模块】：人机安全验证与多通道通信认证 (Captcha API)
 * 【对应后端】：com.oaiss.chain.controller.CaptchaController (/captcha)
 * 【主要服务页面】：
 *   - 登录页面: views/Login.vue (/login)
 * ==============================================================================
 */
import request from './request'
import type { CaptchaResponse, CaptchaSendRequest } from '../types'

/**
 * 【业务功能】：生成防暴力破解图形验证码（包含Base64图片与缓存Key）
 * 【对应前端页面】：Login.vue (/login)
 * 【界面触发操作】：进入登录页自动加载，或用户点击验证码图片重新刷新获取
 * 【后端对应端点】：CaptchaController -> GET /captcha/generate
 */
export function generateCaptcha(): Promise<CaptchaResponse> {
  return request.get('/captcha/generate')
}

/**
 * 【业务功能】：服务端校验用户输入的图形验证码是否正确
 * 【对应前端页面】：Login.vue (/login)
 * 【界面触发操作】：表单提交时前置调用，或登录接口内部联动校验
 * 【后端对应端点】：CaptchaController -> POST /captcha/verify
 */
export function verifyCaptcha(data: { captchaKey: string; captchaCode: string }): Promise<boolean> {
  if (!data?.captchaKey) return Promise.reject(new Error('Captcha key is required'))
  if (!data?.captchaCode) return Promise.reject(new Error('Captcha code is required'))
  return request.post('/captcha/verify', data)
}

/**
 * 【业务功能】：发送手机短信动态验证码（支持绑定、二次认证或注册）
 * 【对应前端页面】：Login.vue (/login), UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：点击【获取短信验证码】按钮
 * 【后端对应端点】：CaptchaController -> POST /captcha/sms/send
 */
export function sendSmsCode(data: { phone: string; type?: number }): Promise<void> {
  if (!data?.phone) return Promise.reject(new Error('Phone number is required'))
  const requestData: CaptchaSendRequest = {
    target: data.phone,
    type: data.type ?? 1,
  }
  return request.post('/captcha/sms/send', requestData)
}

/**
 * 【业务功能】：发送电子邮箱动态验证码
 * 【对应前端页面】：Login.vue (/login), UserProfile.vue (/enterprise/user/profile)
 * 【界面触发操作】：点击【发送邮箱验证码】按钮
 * 【后端对应端点】：CaptchaController -> POST /captcha/email/send
 */
export function sendEmailCode(data: { email: string; type?: number }): Promise<void> {
  if (!data?.email) return Promise.reject(new Error('Email is required'))
  const requestData: CaptchaSendRequest = {
    target: data.email,
    type: data.type ?? 1,
  }
  return request.post('/captcha/email/send', requestData)
}
