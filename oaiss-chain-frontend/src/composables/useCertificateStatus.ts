/**
 * 企业准入证书状态（全局单例）。
 *
 * 后端已把准入证书变成真正生效的业务约束：无有效证书的企业提交报告、
 * 挂单、成交、充值都会被拒（7001）。但改造前前端对此毫无感知——
 * 用户只有在「点了按钮、被服务器拒绝」之后才知道自己没资格。
 *
 * 这一层把证书状态提到前端：登录后拉一次，页面据此显示横幅、
 * 禁用写操作按钮，把「被拒」变成「事先告知」。
 *
 * 判定口径与后端 CertificateGateService 保持一致：
 *   有效 = status===1 且（expiryDate 为空 或 >= 今天）
 * 到期日含当天（后端也是 !isBefore(today)）。
 */
import { computed, ref } from 'vue'
import { getMyEnterpriseAdmission } from '../api/enterprise'
import type { EnterpriseAdmissionResponse } from '../types/admin'

/** 与后端 QualificationStatusEnum.ACTIVE 对齐 */
const STATUS_ACTIVE = 1

/** 取今天 00:00 的本地日期字符串（yyyy-MM-dd），避免时区把「当天」判成过期 */
function todayIso(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 判断单张证书是否有效 */
export function isAdmissionValid(
  cert: Pick<EnterpriseAdmissionResponse, 'status' | 'expiryDate'> | null | undefined,
): boolean {
  if (!cert) return false
  if (Number(cert.status) !== STATUS_ACTIVE) return false
  if (!cert.expiryDate) return true // 未设有效期视为长期有效（与后端一致）
  return String(cert.expiryDate).slice(0, 10) >= todayIso()
}

/** 从证书列表挑出当前有效的那张（列表按创建时间倒序，取最新有效） */
export function pickActiveAdmission(
  list: EnterpriseAdmissionResponse[] | null | undefined,
): EnterpriseAdmissionResponse | null {
  if (!Array.isArray(list)) return null
  return list.find(c => isAdmissionValid(c)) ?? null
}

/** 该企业是否曾经有过证书（用于区分「从未签发」与「已吊销」的文案） */
export function hasAnyAdmission(
  list: EnterpriseAdmissionResponse[] | null | undefined,
): boolean {
  return Array.isArray(list) && list.length > 0
}

const admissions = ref<EnterpriseAdmissionResponse[]>([])
const loaded = ref(false)
const loading = ref(false)

/**
 * 拉取当前企业的证书列表。
 *
 * 刻意不在失败时抛错：证书状态是「增强提示」，拉取失败不应让页面崩掉，
 * 只把状态标记为未知（此时不显示横幅、不禁用按钮，退回「服务端兜底拒绝」）。
 */
async function refreshAdmission(): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const list = await getMyEnterpriseAdmission()
    admissions.value = Array.isArray(list) ? list : []
    loaded.value = true
  } catch {
    admissions.value = []
    loaded.value = true
  } finally {
    loading.value = false
  }
}

/** 登出或切换账号时清空，避免把上一个企业的状态带给下一个 */
function resetAdmission(): void {
  admissions.value = []
  loaded.value = false
}

export function useCertificateStatus() {
  const activeAdmission = computed(() => pickActiveAdmission(admissions.value))

  /** 有有效证书 → 允许写操作 */
  const admitted = computed(() => activeAdmission.value !== null)

  /**
   * 需要提示的状态：
   *   'none'     从未签发
   *   'revoked'  有过证但当前无有效证（已吊销或已过期）
   *   'expired'  最新一张是有效的状态但已过期
   */
  const blockReason = computed<'none' | 'revoked' | 'expired' | null>(() => {
    if (!loaded.value || admitted.value) return null
    const latest = admissions.value[0]
    if (!latest) return 'none'
    if (Number(latest.status) === STATUS_ACTIVE) return 'expired'
    return 'revoked'
  })

  /** 最新一张（无论是否有效），供界面显示编号/吊销原因 */
  const latestAdmission = computed<EnterpriseAdmissionResponse | null>(
    () => admissions.value[0] ?? null,
  )

  return {
    admissions,
    latestAdmission,
    activeAdmission,
    admitted,
    blockReason,
    loaded,
    loading,
    refreshAdmission,
    resetAdmission,
  }
}
