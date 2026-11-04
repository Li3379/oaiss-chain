<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳核算填报与凭证管理 (Carbon Accounting Upload)
 * 【路由地址】：/enterprise/carbon/upload
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 碳排放填报表单卡片：范围一（直接燃烧）、范围二（外购电热）数据录入
 *   2. 附件凭证上传组件：支持上传发票、电费单、化验单佐证原件至 MinIO
 *   3. RSA 电子签名状态面板：展示企业公钥证书与防篡改签名就绪状态
 *   4. 历史填报报告列表：分页展示草稿、待初审、审核通过、已驳回、已上链状态
 *   5. 报告详情抽屉：穿透查看核算因数、上传材料、TxHash 链上凭证
 *   6. AI 减排诊断分析弹窗：调用 Copilot 大模型生成报告诊断与减排建议
 * 【对应后端 Controller & API】：
 *   - CarbonController:
 *     * GET    /api/v1/carbon/my-reports          -> getMyReports() 查询历史报告
 *     * POST   /api/v1/carbon/reports             -> createReport() 保存报告草稿
 *     * POST   /api/v1/carbon/reports/{id}/submit -> submitReport() 提交报告进入初审
 *     * DELETE /api/v1/carbon/reports/{id}        -> deleteReport() 删除草稿
 *     * GET    /api/v1/carbon/reports/{id}        -> getReport() 获取报告详情
 *   - DigitalSignatureController:
 *     * GET    /api/v1/signature/keypair          -> getKeyPair() 查询RSA密钥状态
 *   - FileController:
 *     * POST   /api/v1/file/upload                -> uploadFile() 上传核算凭据材料
 * ==============================================================================
 */
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Clock, CopyDocument, Cpu, DataAnalysis, Document, Key, Link, Lock, Paperclip, Search, TrendCharts, Warning } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { getMyReports, createReport, updateReport, deleteReport, submitReport, getReport, getAiReportAnalysis, type AiReportAnalysisResponse } from '../../api/carbon'
import { getKeyPair } from '../../api/signature'
import { useCertificateStatus } from '../../composables/useCertificateStatus'
import { uploadFile } from '../../api/file'
import type { CarbonReportResponse, RsaKeyPairResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import StatusTag from '../../components/StatusTag.vue'
import EmissionComposition from '../../components/EmissionComposition.vue'
import EmissionDataPanel from '../../components/EmissionDataPanel.vue'
import TermTip from '../../components/TermTip.vue'
import EmptyHint from '../../components/EmptyHint.vue'

const { t } = useI18n()

// 准入证书门禁：无有效证书时禁止提交送审（后端以 7001 拒绝），
// 创建草稿不拦——草稿不产生对外证据。
const { admitted, blockReason } = useCertificateStatus()

const aiAnalysisDialogVisible = ref(false)
const aiAnalysisLoading = ref(false)
const aiAnalysisData = ref<AiReportAnalysisResponse | null>(null)
const aiAnalysisError = ref('')

const onGenerateAiAnalysis = async (report: CarbonReportResponse | null) => {
  if (!report) return
  aiAnalysisDialogVisible.value = true
  aiAnalysisLoading.value = true
  aiAnalysisData.value = null
  aiAnalysisError.value = ''

  let totalEm = Number(report.totalEmission) || 0
  let s1 = Number(report.scope1Emission) || 0
  let s2 = Number(report.scope2Emission) || 0
  let desulf = 0

  // 深度解析：若表字段为0，从结构化 emissionData 中反向提取真实排放量
  if (totalEm === 0 && report.emissionData) {
    try {
      const ed = JSON.parse(report.emissionData)
      if (typeof ed.totalEmission === 'number' && ed.totalEmission > 0) totalEm = ed.totalEmission
      if (typeof ed.scope1 === 'number' && ed.scope1 > 0) s1 = ed.scope1
      if (typeof ed.scope2 === 'number' && ed.scope2 > 0) s2 = ed.scope2
      if (typeof ed.desulf === 'number' && ed.desulf > 0) desulf = ed.desulf
      if (Array.isArray(ed.scope1)) {
        s1 = ed.scope1.reduce((sum: number, it: any) => sum + (Number(it.activity_data) || 0) * (Number(it.emission_factor) || 0), 0)
      }
      if (Array.isArray(ed.scope2)) {
        s2 = ed.scope2.reduce((sum: number, it: any) => sum + (Number(it.activity_data) || 0) * (Number(it.emission_factor) || 0), 0)
      }
      if (totalEm === 0 && (s1 > 0 || s2 > 0)) totalEm = s1 + s2
    } catch {
      // ignore
    }
  }

  // 不再用基准值兜底：没有排放量就如实上报 0，由 ML 服务返回「数据不足，无法生成诊断」
  if (totalEm === 0) {
    s1 = 0
    s2 = 0
    desulf = 0
  }

  try {
    const res = await getAiReportAnalysis({
      report_id: report.id,
      // 企业名/周期缺失时如实传空，不再用虚构的企业名与周期填充诊断文本
      enterprise_name: report.enterpriseName || '',
      accounting_period: report.accountingPeriod || '',
      total_emission: totalEm,
      scope1_emission: s1,
      scope2_emission: s2,
      desulf_emission: desulf,
      emission_details: report.emissionData,
    })
    aiAnalysisData.value = res
  } catch {
    aiAnalysisError.value = t('carbonUpload.aiAnalysisFailed')
    ElMessage.error(t('carbonUpload.aiAnalysisFailed'))
  } finally {
    aiAnalysisLoading.value = false
  }
}

const copyAiAnalysisText = () => {
  // 数据不足时不提供可复制的「报告全文」，避免把说明文字当成诊断结论传播
  if (aiAnalysisData.value?.full_text && !aiAnalysisData.value.insufficient_data) {
    navigator.clipboard.writeText(aiAnalysisData.value.full_text)
    ElMessage.success(t('carbonUpload.copiedAiAnalysis'))
  }
}

const searchForm = reactive({ title: '', accountingPeriod: '' })
const tableData = ref<CarbonReportResponse[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
/** D13：非空时表示当前对话框处于「修改已有报告」模式，而非新建 */
const editingReportId = ref<number | null>(null)
const formRef = ref()
const submitting = ref(false)
const detailDialogVisible = ref(false)
const detailData = ref<CarbonReportResponse | null>(null)
const detailLoading = ref(false)
const formModel = reactive({
  accountingPeriod: '',
  title: '',
  reportType: 1,
  emissionData: '',
  calculationMethod: '',
  attachments: '',
  // ===== 组件1：配额发放依据 =====
  // 核算周期拆结构化字段——accountingPeriod 是无格式约束的自由文本，
  // 无法承载「同企业同周期唯一」这条约束。accountingPeriod 降为展示值，
  // 由 periodYear + periodQuarter 自动拼出。
  periodYear: null as number | null,
  periodQuarter: null as number | null,
  outputQuantity: null as number | null,
  outputUnit: 'MWh',
  unitType: '' as string,
})

/* ---------- 附件：真实文件上传与 SHA-256 存证指纹计算 ---------- */
// objectName 是 MinIO 对象的真实主键，审核端凭它换取预签名链接；
// url 仍保留落库以兼容历史数据（未签名直连地址在私有桶下不可访问，仅作留档）
type UploadedAttachment = { name: string; objectName: string; url: string; size: number; hash?: string }
const attachmentList = ref<UploadedAttachment[]>([])
const attachmentUploading = ref(false)

async function computeFileSha256(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
}

function getReportStepIndex(status: number): number {
  if (status === 0) return 0
  if (status === 1) return 1
  if (status === 3) return 2
  if (status === 4) return 1
  if (status === 5) return 4
  return 0
}

const syncAttachments = () => {
  formModel.attachments = attachmentList.value.length > 0
    ? JSON.stringify(attachmentList.value.map(({ name, objectName, url, size, hash }) => ({ name, objectName, url, size, hash })))
    : ''
}

const onAttachmentUpload = async (options: { file: File }) => {
  try {
    attachmentUploading.value = true
    const [result, fileHash] = await Promise.all([
      uploadFile(options.file, 'reports'),
      computeFileSha256(options.file).catch(() => '')
    ])
    attachmentList.value.push({
      name: options.file.name,
      objectName: result.objectName,
      url: result.url,
      size: result.size,
      hash: fileHash,
    })
    syncAttachments()
    ElMessage.success(t('carbonUpload.attachmentUploadSuccess'))
  } catch {
    ElMessage.error(t('carbonUpload.attachmentUploadFailed'))
  } finally {
    attachmentUploading.value = false
  }
}

const removeAttachment = (index: number) => {
  attachmentList.value.splice(index, 1)
  syncAttachments()
}

const formatAttachmentSize = (size: number) => {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(0)} KB`
  return `${size} B`
}

/* ---------- 排放数据：结构化填写（ELI5 改造，替代纯 JSON 粘贴） ---------- */
type EmissionRow = { name: string; activity: number | null; factor: number | null }
type ScopeKey = 'scope1' | 'scope2' | 'scope3'

const emissionMode = ref<'structured' | 'json'>('structured')

function emptyScopeRows(): EmissionRow[] {
  return [{ name: '', activity: null, factor: null }]
}

const structuredScopes = reactive<Record<ScopeKey, EmissionRow[]>>({
  scope1: emptyScopeRows(),
  scope2: emptyScopeRows(),
  scope3: emptyScopeRows(),
})

const scopeMeta = computed(() => [
  { key: 'scope1' as ScopeKey, title: t('structuredForm.scope1Title'), tip: 'scope1' },
  { key: 'scope2' as ScopeKey, title: t('structuredForm.scope2Title'), tip: 'scope2' },
  { key: 'scope3' as ScopeKey, title: t('structuredForm.scope3Title'), tip: 'scope3' },
])

function addRow(scope: ScopeKey) {
  structuredScopes[scope].push({ name: '', activity: null, factor: null })
}

function removeRow(scope: ScopeKey, index: number) {
  structuredScopes[scope].splice(index, 1)
}

const validRows = (rows: EmissionRow[]) =>
  rows.filter(
    (row) =>
      typeof row.activity === 'number' &&
      typeof row.factor === 'number' &&
      row.activity >= 0 &&
      row.factor >= 0,
  )

const scopeSubtotal = (rows: EmissionRow[]) =>
  validRows(rows).reduce((sum, row) => sum + (row.activity as number) * (row.factor as number), 0)

const structuredRowCount = computed(() =>
  scopeMeta.value.reduce((sum, scope) => sum + validRows(structuredScopes[scope.key]).length, 0),
)

const grandTotal = computed(() =>
  scopeMeta.value.reduce((sum, scope) => sum + scopeSubtotal(structuredScopes[scope.key]), 0),
)

function formatEmissionNumber(value: number): string {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

function buildEmissionJson(): string {
  const payload: Record<string, Array<Record<string, unknown>>> = {}
  for (const scope of scopeMeta.value) {
    const rows = validRows(structuredScopes[scope.key]).map((row) => {
      const item: Record<string, unknown> = {
        activity_data: row.activity,
        emission_factor: row.factor,
      }
      const name = row.name.trim()
      if (name) item.name = name
      return item
    })
    if (rows.length > 0) payload[scope.key] = rows
  }
  return Object.keys(payload).length > 0 ? JSON.stringify(payload, null, 2) : ''
}

watch(
  () => JSON.stringify(structuredScopes),
  () => {
    if (emissionMode.value === 'structured') {
      formModel.emissionData = buildEmissionJson()
    }
  },
  { immediate: true },
)

function resetStructuredScopes() {
  structuredScopes.scope1 = emptyScopeRows()
  structuredScopes.scope2 = emptyScopeRows()
  structuredScopes.scope3 = emptyScopeRows()
  formModel.emissionData = ''
}

// 常见排放源国家推荐因子下拉预设
const presetSources = computed<Record<string, Array<{ name: string; factor: number; unit: string }>>>(() => ({
  scope1: [
    { name: t('presetEmissions.coalAnthracite'), factor: 2.77, unit: 'tCO2/t' },
    { name: t('presetEmissions.coalBituminous'), factor: 2.49, unit: 'tCO2/t' },
    { name: t('presetEmissions.naturalGas'), factor: 2.16, unit: 'tCO2/万m³' },
    { name: t('presetEmissions.gasoline'), factor: 2.98, unit: 'tCO2/t' },
    { name: t('presetEmissions.diesel'), factor: 3.10, unit: 'tCO2/t' },
  ],
  scope2: [
    { name: t('presetEmissions.gridEast'), factor: 0.70, unit: 'tCO2/MWh' },
    { name: t('presetEmissions.gridNorth'), factor: 0.88, unit: 'tCO2/MWh' },
    { name: t('presetEmissions.gridNational'), factor: 0.58, unit: 'tCO2/MWh' },
  ],
  scope3: [
    { name: t('presetEmissions.commute'), factor: 0.15, unit: 'tCO2/人次' },
    { name: t('presetEmissions.flightEconomy'), factor: 0.11, unit: 'tCO2/千人公里' },
  ],
}))

function onSelectPreset(scopeKey: 'scope1' | 'scope2' | 'scope3', index: number, presetName: string) {
  const list = presetSources.value[scopeKey] || []
  const match = list.find((p) => p.name === presetName)
  if (match && structuredScopes[scopeKey][index]) {
    structuredScopes[scopeKey][index].name = match.name
    structuredScopes[scopeKey][index].factor = match.factor
  }
}

// 送审弹窗与数字证书状态
const submitConfirmVisible = ref(false)
const pendingSubmitReport = ref<CarbonReportResponse | null>(null)
const enterpriseKeyPair = ref<RsaKeyPairResponse | null>(null)
const keyPairLoading = ref(false)
const submitProcessing = ref(false)

const openSubmitConfirm = async (row: CarbonReportResponse) => {
  pendingSubmitReport.value = row
  submitConfirmVisible.value = true
  keyPairLoading.value = true
  try {
    enterpriseKeyPair.value = await getKeyPair()
  } catch {
    enterpriseKeyPair.value = null
  } finally {
    keyPairLoading.value = false
  }
}

const confirmExecuteSubmit = async () => {
  if (!pendingSubmitReport.value || submitProcessing.value) return
  submitProcessing.value = true
  try {
    if (!ensureAdmitted()) {
      submitting.value = false
      return
    }
    await submitReport(pendingSubmitReport.value.id)
    ElMessage.success(`${t('carbonUpload.submitSuccess')} ${t('nextSteps.afterSubmitReport')}`)
    submitConfirmVisible.value = false
    pendingSubmitReport.value = null
    await fetchData()
  } catch {
    ElMessage.error(t('carbonUpload.submitFailed'))
  } finally {
    submitProcessing.value = false
  }
}

const validateEmissionData = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (emissionMode.value === 'structured') {
    if (structuredRowCount.value === 0) {
      callback(new Error(t('structuredForm.needRow')))
      return
    }
    callback()
    return
  }
  if (!value) {
    callback(new Error(t('carbonUpload.enterEmissionData')))
    return
  }
  try {
    JSON.parse(value)
    callback()
  } catch {
    callback(new Error(t('structuredForm.jsonHint')))
  }
}

const reportTypeOptions = [
  { label: t('carbonUpload.reportTypeQuarterly'), value: 1 },
  { label: t('carbonUpload.reportTypeAnnual'), value: 2 },
]

/* ---------- 组件1：配额发放依据 ---------- */

/** 可选年度：当前年往前 5 年到往后 1 年（覆盖补报与预填） */
const periodYearOptions = computed(() => {
  const now = new Date().getFullYear()
  return Array.from({ length: 7 }, (_, i) => now - 5 + i)
})

/**
 * 机组类别。四类对应官方四种基准值：
 * 300MW 以上常规燃煤 / 及以下常规燃煤 / 非常规燃煤 / 燃气。
 * 本轮由企业自选（项目无装机容量字段，无法自动判定）。
 */
const unitTypeOptions = computed(() => [
  { label: t('carbonUpload.unitTypeI'), value: 'I' },
  { label: t('carbonUpload.unitTypeII'), value: 'II' },
  { label: t('carbonUpload.unitTypeIII'), value: 'III' },
  { label: t('carbonUpload.unitTypeIV'), value: 'IV' },
])

/**
 * 由年 + 季拼出展示用的 accountingPeriod。
 *
 * 后端唯一约束建在 (period_year, period_quarter) 上，不依赖这个字符串；
 * 这里只是保持既有字段的展示连续性（列表、详情、导出都在读它）。
 */
const syncAccountingPeriod = () => {
  const y = formModel.periodYear
  const q = formModel.periodQuarter
  if (!y) {
    formModel.accountingPeriod = ''
    return
  }
  formModel.accountingPeriod = q && q > 0 ? `${y}-Q${q}` : String(y)
}

const reportTypeLabel = (value: number | string | null | undefined) =>
  reportTypeOptions.find((option) => option.value === Number(value))?.label ?? String(value ?? '-')

/**
 * 配额盈余判定的标签语气。
 *
 * 盈余用 success、缺口用 danger、无法判定用 info——「判不出来」不该被渲染成
 * 红色警报，那会让企业以为出了事故，实际只是范围一排放缺失。
 */
const quotaTone = (judgement: string | null | undefined): 'success' | 'danger' | 'info' => {
  if (judgement === 'SURPLUS') return 'success'
  if (judgement === 'SHORTFALL') return 'danger'
  return 'info'
}

const formRules = {
  accountingPeriod: [{ required: true, message: t('carbonUpload.enterAccountingPeriod'), trigger: 'change' }],
  title: [{ required: true, message: t('carbonUpload.enterReportTitle'), trigger: 'blur' }],
  reportType: [{ required: true, message: t('carbonUpload.selectReportType'), trigger: 'change' }],
  emissionData: [{ validator: validateEmissionData, trigger: 'blur' }],
  // 组件1：配额发放依据。这三项不填，审核会失败（配额算不出来），
  // 所以在提交期就拦住，而不是等审核员点了「通过」才报错。
  outputQuantity: [{ required: true, message: t('carbonUpload.enterOutputQuantity'), trigger: 'blur' }],
  unitType: [{ required: true, message: t('carbonUpload.selectUnitType'), trigger: 'change' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const data = await getMyReports({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      title: searchForm.title || undefined,
      accountingPeriod: searchForm.accountingPeriod || undefined,
    })
    tableData.value = data.items || []
    total.value = data.total || 0
  } catch {
    ElMessage.error(t('carbonUpload.loadFailed'))
  } finally {
    loading.value = false
  }
}

const onQuery = () => { currentPage.value = 1; fetchData() }

const onResetQuery = () => {
  searchForm.title = ''
  searchForm.accountingPeriod = ''
  currentPage.value = 1
  fetchData()
}

/* ---------- 批量送审：勾选草稿报告后一次性提交 ---------- */
const selectedIds = ref<number[]>([])
const batchSubmitting = ref(false)

function toggleRowSelect(id: number, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value.push(id)
  } else {
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
  }
}

/** 页头右上角按钮：索引 0 = 批量送审（索引 1 = 创建，由页面内按钮承担） */
async function onHeaderAction(index: number) {
  if (index === 0) await onBatchSubmit()
  else openAddDialog()
}

async function onBatchSubmit() {
  if (batchSubmitting.value) return
  if (selectedIds.value.length === 0) {
    ElMessage.warning(t('carbonUpload.batchNoneSelected'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('carbonUpload.batchConfirm', { count: selectedIds.value.length }),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  if (!ensureAdmitted()) return
  batchSubmitting.value = true
  let ok = 0
  for (const id of [...selectedIds.value]) {
    try {
      await submitReport(id)
      ok += 1
    } catch {
      // 单条失败不阻断其余条目，最终按成功数汇总提示
    }
  }
  batchSubmitting.value = false
  selectedIds.value = []
  if (ok > 0) ElMessage.success(t('carbonUpload.batchSuccess', { count: ok }))
  if (ok < 1) ElMessage.error(t('carbonUpload.batchFailed'))
  await fetchData()
}
const onSizeChange = (size: number) => { pageSize.value = size; currentPage.value = 1; fetchData() }
const onCurrentChange = (page: number) => { currentPage.value = page; fetchData() }

const openAddDialog = () => {
  editingReportId.value = null
  formModel.accountingPeriod = ''
  formModel.title = ''
  formModel.reportType = 1
  formModel.calculationMethod = ''
  formModel.attachments = ''
  attachmentList.value = []
  emissionMode.value = 'structured'
  resetStructuredScopes()
  dialogVisible.value = true
}

/**
 * D13：打开编辑对话框，预填已有报告内容。
 *
 * 仅 DRAFT / REJECTED 可进入（后端也会二次校验）。
 * emissionData 原文直接回填并切到 JSON 模式——避免把已存的 JSON 强行解析成结构化行。
 *
 * ## 为什么还要再取一次详情
 *
 * 列表行（`my-reports`）理论上带 emissionData，但它同时服务看板、统计等多处，
 * 随时可能被裁剪字段，也可能因为列宽/分页策略只取摘要。
 * 编辑是「覆盖写」：一旦回填到空串，用户随手保存就会把原报告数据清空（GUI-03）。
 * 因此这里以详情接口为准，列表值只作为渲染前的兜底占位。
 */
const openEditDialog = async (row: CarbonReportResponse) => {
  editingReportId.value = row.id
  formModel.accountingPeriod = row.accountingPeriod || ''
  formModel.title = row.title || ''
  formModel.reportType = row.reportType ?? 1
  formModel.calculationMethod = row.calculationMethod || ''
  formModel.attachments = row.attachments || ''
  attachmentList.value = []
  formModel.emissionData = row.emissionData || ''
  emissionMode.value = 'json'
  resetStructuredScopes()
  dialogVisible.value = true

  // 详情兜底：列表行没有排放数据时补全，避免覆盖写把数据清空
  if (!formModel.emissionData) {
    try {
      const detail = await getReport(row.id)
      if (detail) {
        formModel.accountingPeriod = formModel.accountingPeriod || detail.accountingPeriod || ''
        formModel.title = formModel.title || detail.title || ''
        formModel.calculationMethod = formModel.calculationMethod || detail.calculationMethod || ''
        formModel.attachments = formModel.attachments || detail.attachments || ''
        formModel.emissionData = detail.emissionData || ''
      }
    } catch (e) {
      console.warn('[carbon] 加载报告详情失败，编辑将基于列表数据', e)
    }
  }
}

const onSubmitForm = async () => {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      accountingPeriod: formModel.accountingPeriod.trim(),
      title: formModel.title.trim(),
      reportType: formModel.reportType,
      emissionData: formModel.emissionData,
      calculationMethod: formModel.calculationMethod || undefined,
      attachments: formModel.attachments || undefined,
      // 组件1：配额发放依据。核算周期拆结构化字段（唯一约束建在它们上面），
      // 发电量与机组类型决定配额量。
      periodYear: formModel.periodYear ?? undefined,
      periodQuarter:
        formModel.periodQuarter && formModel.periodQuarter > 0 ? formModel.periodQuarter : undefined,
      outputQuantity: formModel.outputQuantity ?? undefined,
      outputUnit: formModel.outputUnit,
      unitType: formModel.unitType || undefined,
    }
    if (editingReportId.value !== null) {
      // D13：修改模式——走 PUT，后端会重算四列并在提交时重新签名
      await updateReport(editingReportId.value, payload)
      ElMessage.success(t('carbonUpload.reviseSuccess'))
    } else {
      await createReport(payload)
      ElMessage.success(`${t('carbonUpload.createSuccess')} ${t('nextSteps.afterCreateReport')}`)
    }
    dialogVisible.value = false
    await fetchData()
  } catch (error: unknown) {
    // D13：修改失败的原因往往很具体（如「需重新数字签名」「状态不允许修改」），
    // 不能统一显示成「创建失败」——用户会不知道该去做什么。
    const backendMsg =
      error && typeof error === 'object' && 'response' in error
        ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(
      backendMsg ||
        (editingReportId.value !== null
          ? t('carbonUpload.reviseFailed')
          : t('carbonUpload.createFailed')),
    )
  } finally {
    submitting.value = false
  }
}

/**
 * 准入证书门禁前置检查。
 *
 * 后端对「无有效准入证书的企业提交报告」返回 7001。前端在三个提交入口
 * （单条提交 / 批量送审 / 表单提交）统一前置拦截，避免用户填完再被拒。
 * 注意：**创建草稿不拦**——草稿不产生对外证据，允许先起草。
 */
const ensureAdmitted = (): boolean => {
  if (admitted.value) return true
  ElMessage.warning(admissionBlockMessage.value)
  return false
}

const admissionBlockMessage = computed(() => {
  if (blockReason.value === 'expired') return t('admissionBanner.bodyExpired', { date: '-' })
  if (blockReason.value === 'revoked') return t('admissionBanner.bodyRevoked')
  return t('admissionBanner.bodyNone')
})

const onSubmitReport = async (row: CarbonReportResponse) => {
  if (!ensureAdmitted()) return
  try {
    await ElMessageBox.confirm(t('carbonUpload.confirmSubmit') + ' ' + row.reportNo + '？', t('common.confirm'), { type: 'warning' })
    await submitReport(row.id)
    ElMessage.success(`${t('carbonUpload.submitSuccess')} ${t('nextSteps.afterSubmitReport')}`)
    await fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('carbonUpload.submitFailed'))
  }
}

/**
 * D13：被驳回报告的「修改重提」入口。
 *
 * 后端只允许 DRAFT/REJECTED 编辑，修改后会重算四列并清空旧签名，
 * 提交时按最新内容重新签名。这里给用户一个轻量确认，主体编辑仍在详情抽屉完成。
 */
const openReviseDialog = async (row: CarbonReportResponse) => {
  try {
    await ElMessageBox.confirm(
      t('carbonUpload.reviseConfirm', { reportNo: row.reportNo }),
      t('carbonUpload.reviseTitle'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') },
    )
  } catch {
    return
  }
  // 打开编辑弹窗，预填该报告内容
  await openEditDialog(row)
}

/**
 * 从**详情抽屉**发起「修改重提」。
 *
 * 用户看完驳回意见后就在详情里，要求他关掉抽屉、回到列表、再找那一行是多余的；
 * 这里先关抽屉再走同一套确认与预填流程（AC-B10）。
 */
const reviseFromDetail = async () => {
  const row = detailData.value
  if (!row || row.status !== 4) return
  detailDialogVisible.value = false
  await openReviseDialog(row)
}

const onDeleteReport = async (row: CarbonReportResponse) => {
  try {
    await ElMessageBox.confirm(t('carbonUpload.confirmDelete') + ' ' + row.reportNo + '？', t('common.confirm'), { type: 'warning' })
    await deleteReport(row.id)
    ElMessage.success(t('carbonUpload.deleteSuccess'))
    await fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('carbonUpload.deleteFailed'))
  }
}

/** 首屏统计条：全部由已加载的本页数据推导，不新增接口请求 */
const overviewStats = computed(() => {
  const rows = tableData.value
  const totalEmission = rows.reduce((sum, r) => sum + (Number(r.totalEmission) || 0), 0)
  const pending = rows.filter((r) => r.status === 1 || r.status === 2).length
  const onChain = rows.filter((r) => r.status === 5).length
  const signed = rows.filter((r) => !!r.signatureData).length

  return [
    {
      label: t('carbonUpload.statTotalEmission'),
      value: Number(totalEmission.toFixed(1)),
      unit: 'tCO2e',
      delta: t('carbonUpload.statRowsCount', { count: total.value }),
      tone: 'neu' as const,
      icon: DataAnalysis,
    },
    {
      label: t('carbonUpload.statPending'),
      value: pending,
      unit: t('carbonUpload.statUnitReports'),
      delta: t('carbonUpload.statPendingHint'),
      tone: pending > 0 ? ('neu' as const) : ('pos' as const),
      icon: Clock,
    },
    {
      label: t('carbonUpload.statOnChain'),
      value: onChain,
      unit: t('carbonUpload.statUnitReports'),
      delta: t('carbonUpload.statOnChainHint'),
      tone: 'pos' as const,
      icon: Link,
    },
    {
      label: t('carbonUpload.statSigned'),
      value: `${signed}/${rows.length || 0}`,
      unit: '',
      delta: t('carbonUpload.statSignedHint'),
      tone: signed > 0 ? ('pos' as const) : ('neu' as const),
      icon: Key,
    },
  ]
})

const viewDetail = async (row: CarbonReportResponse) => {
  detailLoading.value = true
  detailDialogVisible.value = true
  try {
    detailData.value = await getReport(row.id)
  } catch {
    ElMessage.error(t('carbonUpload.loadDetailFailed'))
    detailDialogVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchData()
  const cachedDraft = localStorage.getItem('oaiss_prefilled_carbon_draft')
  if (cachedDraft) {
    try {
      const parsed = JSON.parse(cachedDraft)
      localStorage.removeItem('oaiss_prefilled_carbon_draft')
      openAddDialog()
      formModel.title = parsed.title || ''
      formModel.accountingPeriod = parsed.accountingPeriod || ''
      formModel.calculationMethod = parsed.calculationMethod || ''
      if (parsed.emissionData) {
        emissionMode.value = 'json'
        formModel.emissionData = parsed.emissionData
      }
      ElMessage.success(t('carbonUpload.prefillLoadedTip'))
    } catch {
      // ignore
    }
  }
})
</script>

<template>
  <section class="upload-page pen-enter">
    <PageHeader
      :title="t('carbonUpload.pageTitle')"
      :description="t('pageHints.carbonUpload')"
      :crumbs="[t('carbonUpload.breadcrumbAccounting'), t('carbonUpload.breadcrumbUpload')]"
      :actions="[
        { label: t('carbonUpload.btnBatchSubmit'), variant: 'default', icon: Document, disabled: batchSubmitting },
        { label: t('common.create'), variant: 'primary' },
      ]"
      @action="onHeaderAction"
    />

    <StatStrip :items="overviewStats" :loading="loading" />

    <FilterBar :aria-label="t('carbonUpload.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('carbonUpload.colReportTitle') }}</span>
        <el-input v-model="searchForm.title" :placeholder="t('carbonUpload.enterReportTitle')" clearable @keyup.enter="onQuery" />
      </label>
      <label class="pen-field">
        <span class="pen-field__label">{{ t('carbonUpload.colAccountingPeriod') }}</span>
        <el-input v-model="searchForm.accountingPeriod" :placeholder="t('carbonUpload.enterAccountingPeriod')" clearable @keyup.enter="onQuery" />
      </label>
      <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
      <el-button @click="onResetQuery">{{ t('common.reset') }}</el-button>
    </FilterBar>

    <DataPanel
      :title="t('carbonUpload.panelTitle')"
      :meta="t('carbonUpload.panelMeta', { total })"
    >
      <el-table :data="tableData" v-loading="loading" @row-dblclick="viewDetail">
        <template #empty>
          <EmptyHint
            :description="t('carbonUpload.emptyText')"
            :action-text="t('common.create')"
            @action="openAddDialog"
          />
        </template>
        <el-table-column prop="reportNo" :label="t('carbonUpload.colReportNo')" min-width="180" />
        <el-table-column prop="title" :label="t('carbonUpload.colReportTitle')" min-width="200" />
        <el-table-column prop="accountingPeriod" :label="t('carbonUpload.colAccountingPeriod')" min-width="120" />
        <el-table-column prop="totalEmission" :label="t('carbonUpload.colTotalEmission')" min-width="140">
          <template #header>
            <span>{{ t('carbonUpload.colTotalEmission') }}<TermTip term="tco2e" /></span>
          </template>
          <template #default="{ row }">{{ formatNumber(row.totalEmission) }}</template>
        </el-table-column>
        <el-table-column prop="statusText" :label="t('common.status')" min-width="100">
          <template #default="{ row }">
            <StatusTag domain="report" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column :label="t('carbonUpload.colSignature')" min-width="110">
          <template #header>
            <span>{{ t('carbonUpload.colSignature') }}<TermTip term="digitalSignature" /></span>
          </template>
          <template #default="{ row }">
            <el-tag v-if="row.signatureData" type="success"><el-icon><Key /></el-icon> {{ t('carbonUpload.signed') }}</el-tag>
            <el-tag v-else type="info">— {{ t('carbonUpload.unsigned') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" :label="t('carbonUpload.colReviewer')" min-width="120" />
        <el-table-column prop="createdAt" :label="t('common.createTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="200" fixed="right">
          <template #default="{ row }">
            <span v-if="selectedIds.includes(row.id)" class="row-selected-mark" aria-hidden="true" />
            <el-checkbox
              v-if="row.status === 0"
              :model-value="selectedIds.includes(row.id)"
              :aria-label="`${t('carbonUpload.selectRow')} ${row.reportNo}`"
              @change="(v: boolean) => toggleRowSelect(row.id, v)"
            />
            <span v-else class="row-selected-placeholder" aria-hidden="true" />
            <el-button link type="primary" @click="viewDetail(row)">{{ t('carbonUpload.viewDetail') }}</el-button>
            <!-- 提交入口：草稿(0) 与 已驳回(4) 都可提交。
                 后端 ReportStatusEnum.isSubmittable() 本就允许 REJECTED，
                 早前后端写死 status===0，导致被驳回报告改完无处送审（GUI-04）。 -->
            <el-button
              v-if="row.status === 0 || row.status === 4"
              link
              type="primary"
              @click="openSubmitConfirm(row)"
            >{{ t('common.submit') }}</el-button>
            <!-- D13：被驳回的报告可「修改重提」，不再只能删除重建 -->
            <el-button
              v-if="row.status === 4"
              link
              type="warning"
              @click="openReviseDialog(row)"
            >{{ t('carbonUpload.reviseAndResubmit') }}</el-button>
            <el-button v-if="row.status === 0" link type="danger" @click="onDeleteReport(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
        :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onCurrentChange"
        />
      </template>
    </DataPanel>

    <el-dialog
      v-model="dialogVisible"
      :title="editingReportId !== null ? t('carbonUpload.dialogEdit') : t('carbonUpload.dialogCreate')"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="120px" scroll-to-error>
        <el-form-item :label="t('carbonUpload.labelAccountingPeriod')" prop="accountingPeriod">
          <div class="period-row">
            <el-select v-model="formModel.periodYear" :placeholder="t('carbonUpload.placeholderYear')" style="width:120px" data-testid="create-period-year" @change="syncAccountingPeriod">
              <el-option v-for="y in periodYearOptions" :key="y" :label="String(y)" :value="y" />
            </el-select>
            <el-select v-model="formModel.periodQuarter" :placeholder="t('carbonUpload.placeholderQuarter')" style="width:140px" data-testid="create-period-quarter" @change="syncAccountingPeriod">
              <el-option :label="t('carbonUpload.quarterAnnual')" :value="0" />
              <el-option v-for="q in [1, 2, 3, 4]" :key="q" :label="`Q${q}`" :value="q" />
            </el-select>
            <span class="period-preview">{{ formModel.accountingPeriod || t('carbonUpload.periodNotSelected') }}</span>
          </div>
        </el-form-item>
        <el-form-item :label="t('carbonUpload.labelOutputQuantity')" prop="outputQuantity">
          <el-input-number v-model="formModel.outputQuantity" :min="0.0001" :precision="4" :step="100" style="width:220px" data-testid="create-output-quantity" />
          <span class="field-hint">{{ t('carbonUpload.outputQuantityHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('carbonUpload.labelUnitType')" prop="unitType">
          <el-select v-model="formModel.unitType" :placeholder="t('carbonUpload.placeholderUnitType')" style="width:280px" data-testid="create-unit-type">
            <el-option v-for="u in unitTypeOptions" :key="u.value" :label="u.label" :value="u.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('carbonUpload.labelReportTitle')" prop="title">
          <el-input v-model="formModel.title" :placeholder="t('carbonUpload.enterReportTitle')" :maxlength="200" show-word-limit data-testid="create-title" />
        </el-form-item>
        <el-form-item :label="t('carbonUpload.labelReportType')" prop="reportType">
          <el-select v-model="formModel.reportType" style="width:100%">
            <el-option v-for="item in reportTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('carbonUpload.labelEmissionData')" prop="emissionData">
          <div class="emission-editor">
            <el-radio-group v-model="emissionMode" size="small" class="emission-mode">
              <el-radio-button value="structured">{{ t('structuredForm.modeStructured') }}</el-radio-button>
              <el-radio-button value="json">{{ t('structuredForm.modeJson') }}</el-radio-button>
            </el-radio-group>

            <div v-if="emissionMode === 'structured'" class="scope-list" data-testid="structured-emission">
              <p class="structured-intro">{{ t('carbonUpload.placeholderEmissionData') }}</p>
              <div v-for="scope in scopeMeta" :key="scope.key" class="scope-block">
                <div class="scope-head">
                  <span class="scope-title">
                    {{ scope.title }}<TermTip :term="scope.tip" />
                  </span>
                  <span class="scope-subtotal">
                    {{ t('structuredForm.subtotal') }}: {{ formatEmissionNumber(scopeSubtotal(structuredScopes[scope.key])) }} tCO2e
                  </span>
                </div>

                <div v-for="(row, index) in structuredScopes[scope.key]" :key="index" class="scope-row">
                  <el-select
                    v-if="presetSources[scope.key]?.length"
                    placeholder="常见预设"
                    style="width: 140px;"
                    @change="(val: string) => onSelectPreset(scope.key, index, val)"
                  >
                    <el-option
                      v-for="item in presetSources[scope.key]"
                      :key="item.name"
                      :label="item.name"
                      :value="item.name"
                    >
                      <span>{{ item.name }}</span>
                      <small style="color: var(--color-text-muted); margin-left: 8px;">({{ item.factor }} {{ item.unit }})</small>
                    </el-option>
                  </el-select>
                  <el-input
                    v-model="row.name"
                    class="row-name"
                    :placeholder="t('structuredForm.namePlaceholder')"
                    :aria-label="t('structuredForm.colName')"
                  />
                  <el-input-number
                    v-model="row.activity"
                    class="row-num"
                    :min="0"
                    :controls="false"
                    :aria-label="t('structuredForm.colActivity')"
                    :placeholder="t('structuredForm.colActivity')"
                  />
                  <span class="row-times">×</span>
                  <el-input-number
                    v-model="row.factor"
                    class="row-num"
                    :min="0"
                    :step="0.01"
                    :controls="false"
                    :aria-label="t('structuredForm.colFactor')"
                    :placeholder="t('structuredForm.colFactor')"
                  />
                  <el-button link type="danger" class="row-remove" @click="removeRow(scope.key, index)">
                    {{ t('structuredForm.removeRow') }}
                  </el-button>
                </div>
                <p v-if="structuredScopes[scope.key].length === 0" class="scope-empty">
                  {{ t('structuredForm.emptyScope') }}
                </p>
                <el-button link type="primary" class="add-row" @click="addRow(scope.key)">
                  ＋ {{ t('structuredForm.addRow') }}
                </el-button>
              </div>

              <div class="scope-total">
                <span>{{ t('structuredForm.totalLabel') }}</span>
                <strong data-testid="structured-total">{{ formatEmissionNumber(grandTotal) }}</strong>
                <span>tCO2e</span>
              </div>

              <!-- D0：总量构成清单——用户填完就能逐项核对总量怎么加出来的 -->
              <EmissionComposition :data="formModel.emissionData" :authoritative-total="grandTotal" />
            </div>

            <template v-else>
              <el-input
                v-model="formModel.emissionData"
                type="textarea"
                :rows="6"
                :placeholder="t('carbonUpload.placeholderEmissionData')"
                data-testid="create-emission"
              />
              <p class="json-hint">{{ t('structuredForm.jsonHint') }}</p>
            </template>
          </div>
        </el-form-item>
        <el-form-item :label="t('carbonUpload.labelMethod')">
          <el-input v-model="formModel.calculationMethod" type="textarea" :rows="2" :placeholder="t('carbonUpload.placeholderMethod')" data-testid="create-method" />
        </el-form-item>
        <el-form-item :label="t('carbonUpload.labelAttachments')">
          <div class="attachment-field" data-testid="create-attachments">
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              :on-change="(file: { raw: File }) => onAttachmentUpload({ file: file.raw })"
              :disabled="attachmentUploading"
            >
              <el-button :loading="attachmentUploading" :icon="Paperclip">
                {{ t('carbonUpload.uploadAttachment') }}
              </el-button>
            </el-upload>
            <span class="attachment-hint">{{ t('carbonUpload.attachmentHint') }}</span>
            <div v-if="attachmentList.length > 0" class="attachment-list">
              <div
                v-for="(item, index) in attachmentList"
                :key="item.url"
                style="margin-bottom: 6px;"
              >
                <el-tag
                  closable
                  type="info"
                  @close="removeAttachment(index)"
                >
                  {{ item.name }} ({{ formatAttachmentSize(item.size) }})
                </el-tag>
                <div v-if="item.hash" style="font-size: 11px; color: var(--color-text-muted); margin-top: 2px; display: inline-flex; align-items: center; gap: 4px;">
                  <el-icon :size="12"><Lock /></el-icon>
                  <span>SHA-256 存证指纹: <code>{{ item.hash.slice(0, 16) }}...{{ item.hash.slice(-8) }}</code></span>
                </div>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="submitting">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmitForm">
          {{ editingReportId !== null ? t('carbonUpload.dialogSaveEdit') : t('common.create') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" :title="t('carbonUpload.detailTitle')" width="720px" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <!-- 区块链4步流转时间轴 (Stepper) -->
          <div style="margin: 0 10px 24px;">
            <el-steps :active="getReportStepIndex(detailData.status)" :process-status="detailData.status === 4 ? 'error' : 'process'" finish-status="success" align-center>
              <el-step :title="t('carbonUpload.stepDraft')" :description="formatDateTime(detailData.createdAt)" />
              <el-step :title="t('carbonUpload.stepReview')" :description="detailData.status >= 3 ? (detailData.reviewedAt ? formatDateTime(detailData.reviewedAt) : t('carbonUpload.stepDone')) : (detailData.status === 1 ? t('carbonUpload.stepReviewing') : '')" />
              <el-step :title="t('carbonUpload.stepCertify')" :description="detailData.status === 5 ? (detailData.onChainAt ? formatDateTime(detailData.onChainAt) : t('carbonUpload.stepDone')) : ''" />
              <el-step :title="t('carbonUpload.stepBlockchain')" :description="detailData.status === 5 ? t('carbonUpload.stepOnChainSuccess') : ''" />
            </el-steps>
          </div>

          <!-- 存证状态卡片：只保留「数据完整性」信号，不再暴露哈希与链上原始快照 -->
          <div
            v-if="detailData.chainMark === 'HISTORICAL_MOCK' || detailData.onChainAt"
            style="margin-bottom: 16px; padding: 12px 16px; background: var(--pen-success-soft); border: 1px solid var(--color-status-success-soft); border-radius: 6px;"
          >
            <div style="margin-bottom: 6px;">
              <span style="font-weight: 600; color: var(--color-status-success); font-size: 13px; display: inline-flex; align-items: center; gap: 5px;">
                <el-icon><Link /></el-icon>
                {{ t('carbonUpload.blockchainCertifiedTitle') }}
              </span>
            </div>
            <div v-if="detailData.onChainAt" style="font-size: 11px; color: var(--color-text-muted); margin-top: 4px;">
              {{ t('carbonUpload.onChainAtLabel') }}: {{ formatDateTime(detailData.onChainAt) }}
            </div>

            <!-- 历史 Mock 欠账：哈希在库里、链上没有。必须显式告知，否则企业会误以为存证有效 -->
            <el-alert
              v-if="detailData.chainMark === 'HISTORICAL_MOCK'"
              class="chain-mark-alert"
              type="warning"
              :closable="false"
              :title="t('carbonUpload.chainMarkHistoricalMockTitle')"
              :description="t('carbonUpload.chainMarkHistoricalMockBody')"
            />
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('carbonUpload.colReportNo')">{{ detailData.reportNo }}</el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.colReportTitle')">{{ detailData.title }}</el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.colAccountingPeriod')">{{ detailData.accountingPeriod }}</el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.colReportType')">{{ reportTypeLabel(detailData.reportType) }}</el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.colTotalEmission')">{{ formatNumber(detailData.totalEmission) }} tCO2e</el-descriptions-item>
            <el-descriptions-item :label="t('common.status')">{{ detailData.statusText }}</el-descriptions-item>
            <!-- 配额发放结果：审核通过后才有值。未发放时整行不显示——
                 显示「0 吨」会被读成「发了 0 吨」，与「还没发」是两件事。 -->
            <template v-if="detailData.quotaAllocatedAmount != null">
              <el-descriptions-item :label="t('carbonUpload.detailQuotaAllocated')">
                {{ formatNumber(detailData.quotaAllocatedAmount) }} tCO2e
              </el-descriptions-item>
              <el-descriptions-item :label="t('carbonUpload.detailQuotaJudgement')">
                <el-tag :type="quotaTone(detailData.quotaJudgement)" size="small">
                  {{ t(`carbonUpload.quotaJudgement.${detailData.quotaJudgement}`) }}
                </el-tag>
                <template v-if="detailData.quotaSurplusOrGap != null">
                  （{{ detailData.quotaSurplusOrGap >= 0 ? '+' : '' }}{{ formatNumber(detailData.quotaSurplusOrGap) }} tCO2e）
                </template>
              </el-descriptions-item>
            </template>
            <el-descriptions-item :label="t('carbonUpload.detailEmissionData')" :span="2">
              <EmissionDataPanel :data="detailData.emissionData" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.detailMethod')" :span="2">{{ detailData.calculationMethod || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.colReviewer')">{{ detailData.reviewerName || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.detailReviewComment')">{{ detailData.reviewComment || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.createTime')">{{ formatDateTime(detailData.createdAt) }}</el-descriptions-item>
            <el-descriptions-item :label="t('carbonUpload.detailUpdateTime')">{{ formatDateTime(detailData.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">{{ t('common.close') }}</el-button>
        <!-- 被驳回的报告在详情页也要能直接改重提：用户往往是从详情看驳回意见，
             只在列表提供入口会让「看完意见 → 去列表找那一行」成为多余一步（AC-B10）。 -->
        <el-button
          v-if="detailData?.status === 4"
          type="warning"
          plain
          data-testid="detail-revise"
          @click="reviseFromDetail"
        >{{ t('carbonUpload.reviseAndResubmit') }}</el-button>
        <el-button type="success" plain :icon="DataAnalysis" :loading="aiAnalysisLoading" @click="onGenerateAiAnalysis(detailData)">
          {{ t('carbonUpload.btnAiAnalysis') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- AI 智能碳盘查与排放归因分析报告弹窗 -->
    <el-dialog
      v-model="aiAnalysisDialogVisible"
      :title="t('carbonUpload.aiAnalysisTitle')"
      width="780px"
      destroy-on-close
    >
      <div v-loading="aiAnalysisLoading" style="min-height: 200px;">
        <!-- 数据不足：明确告知无法诊断，不渲染空报告 -->
        <el-alert
          v-if="aiAnalysisData?.insufficient_data"
          data-testid="ai-insufficient-data"
          type="warning"
          :closable="false"
          show-icon
          :title="t('carbonUpload.aiInsufficientTitle')"
          :description="aiAnalysisData.insufficient_data_reason || aiAnalysisData.summary"
        />

        <!-- 请求失败：给出可重试的提示 -->
        <el-alert
          v-else-if="aiAnalysisError"
          data-testid="ai-analysis-error"
          type="error"
          :closable="false"
          show-icon
          :title="aiAnalysisError"
        />

        <template v-else-if="aiAnalysisData">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding: 10px 14px; background: var(--color-status-info-soft); border: 1px solid var(--color-status-info-soft); border-radius: 6px;">
            <span style="font-size: 13px; color: var(--color-chart-1); display: inline-flex; align-items: center; gap: 6px;">
              <el-icon><Cpu /></el-icon>
              <span>{{ t('carbonUpload.aiModelUsed') }}: <strong>{{ aiAnalysisData.model_used }}</strong></span>
            </span>
            <el-tag type="success" effect="plain" round>GB/T 32150-2015 准则</el-tag>
          </div>

          <!-- 执行概要 -->
          <el-card shadow="never" style="margin-bottom: 14px; border-radius: 8px;">
            <template #header>
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-chart-1)" :size="15"><Document /></el-icon>
                <strong style="color: var(--color-text);">{{ t('carbonUpload.aiSummaryTitle') }}</strong>
              </div>
            </template>
            <p style="font-size: 13.5px; line-height: 1.8; color: var(--color-text-muted); margin: 0;">
              {{ aiAnalysisData.summary }}
            </p>
          </el-card>

          <!-- 关键诊断发现 -->
          <el-card shadow="never" style="margin-bottom: 14px; border-radius: 8px;">
            <template #header>
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-accent)" :size="15"><Search /></el-icon>
                <strong style="color: var(--color-text);">{{ t('carbonUpload.aiKeyFindingsTitle') }}</strong>
              </div>
            </template>
            <ul style="padding-left: 20px; margin: 0; font-size: 13.5px; line-height: 1.8; color: var(--color-text-muted);">
              <li v-for="(finding, i) in aiAnalysisData.key_findings" :key="i">
                {{ finding }}
              </li>
            </ul>
          </el-card>

          <!-- 节能减排落地建议 -->
          <el-card shadow="never" style="margin-bottom: 14px; border-radius: 8px;">
            <template #header>
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-status-warning)" :size="15"><TrendCharts /></el-icon>
                <strong style="color: var(--color-text);">{{ t('carbonUpload.aiSuggestionsTitle') }}</strong>
              </div>
            </template>
            <ul style="padding-left: 20px; margin: 0; font-size: 13.5px; line-height: 1.8; color: var(--color-text-muted);">
              <li v-for="(sug, i) in aiAnalysisData.reduction_suggestions" :key="i">
                {{ sug }}
              </li>
            </ul>
          </el-card>

          <!-- 区块链存证摘要哈希 -->
          <div style="padding: 10px 14px; background: var(--color-status-warning-soft); border: 1px solid var(--color-status-warning-soft); border-radius: 6px; font-size: 12px; color: var(--color-status-warning); word-break: break-all;">
            <span style="display: inline-flex; align-items: center; gap: 4px;"><el-icon><Lock /></el-icon>{{ t('carbonUpload.aiHashTip') }}: </span>
            <code>{{ aiAnalysisData.analysis_hash }}</code>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="aiAnalysisDialogVisible = false">{{ t('common.close') }}</el-button>
        <el-button @click="onGenerateAiAnalysis(detailData)" :loading="aiAnalysisLoading" :disabled="!detailData">
          {{ t('carbonUpload.retryAiAnalysis') }}
        </el-button>
        <el-button
          type="primary"
          :icon="CopyDocument"
          :disabled="!aiAnalysisData || aiAnalysisData.insufficient_data"
          @click="copyAiAnalysisText"
        >
          {{ t('carbonUpload.btnCopyAiAnalysis') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 送审与数字签名确认弹窗 (ELI5 可视化签名确认) -->
    <el-dialog
      v-model="submitConfirmVisible"
      :title="t('submitConfirm.dialogTitle')"
      width="560px"
      destroy-on-close
    >
      <div v-loading="keyPairLoading" class="submit-confirm-body">
        <p class="submit-confirm-text">
          {{ t('submitConfirm.confirmText', { title: pendingSubmitReport?.title || '' }) }}
        </p>

        <!-- 证书状态卡片：有私钥证书 -->
        <div v-if="enterpriseKeyPair" class="cert-status-card cert-status-card--active">
          <div class="cert-status-header">
            <span class="cert-icon"><el-icon><Key /></el-icon></span>
            <strong>{{ t('submitConfirm.hasKeyTitle') }}</strong>
          </div>
          <p class="cert-desc">{{ t('submitConfirm.hasKeyDesc') }}</p>
          <div class="cert-meta">
            <div><span>{{ t('submitConfirm.certAlgorithm') }}:</span> <code>RSA-2048 / SHA256</code></div>
            <div><span>{{ t('submitConfirm.certKeyId') }}:</span> <code>#{{ enterpriseKeyPair.id }}</code></div>
          </div>
        </div>

        <!-- 证书状态卡片：尚未生成私钥证书 -->
        <div v-else class="cert-status-card cert-status-card--warning">
          <div class="cert-status-header">
            <span class="cert-icon"><el-icon><Warning /></el-icon></span>
            <strong>{{ t('submitConfirm.noKeyTitle') }}</strong>
          </div>
          <p class="cert-desc">{{ t('submitConfirm.noKeyDesc') }}</p>
          <router-link to="/enterprise/user/profile" class="cert-link" style="display: inline-flex; align-items: center; gap: 4px;">
            <span>{{ t('submitConfirm.noKeyAction') }}</span>
            <el-icon :size="12"><ArrowRight /></el-icon>
          </router-link>
        </div>
      </div>

      <template #footer>
        <el-button @click="submitConfirmVisible = false" :disabled="submitProcessing">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitProcessing"
          @click="confirmExecuteSubmit"
        >
          {{ t('submitConfirm.confirmBtn') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.upload-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.section-card {
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
}

/* 操作列：勾选框与操作链接同一行基线对齐 */
.row-selected-mark {
  display: none;
}

.row-selected-placeholder {
  display: inline-block;
  width: 14px;
}

/* ---------- 排放数据结构化编辑器 ---------- */
.emission-editor {
  width: 100%;
}

.emission-mode {
  margin-bottom: 10px;
}

.structured-intro {
  margin: 0 0 10px;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.scope-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scope-block {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface-elevated);
}

.scope-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 8px;
}

.scope-title {
  font-weight: 600;
  color: var(--color-text);
  font-size: var(--text-base);
}

.scope-subtotal {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

/* 排放行：用 flex 而非固定列数的 grid——"常见预设"下拉是可选渲染的，
   写死 5 条轨道时第 6 个子元素会掉到第二行，把删除按钮挤到错误位置 */
.scope-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.scope-row .el-select {
  flex: 0 0 140px;
  width: 140px;
}

.scope-row .row-name {
  flex: 1 1 120px;
  min-width: 0;
}

/* Element Plus 给 el-input-number 写死了 width:150px，会撑破轨道并压住 × */
.scope-row .row-num {
  flex: 1 1 110px;
  width: auto;
  min-width: 0;
}

.row-times {
  flex: 0 0 auto;
  text-align: center;
  color: var(--color-text-muted);
}

.row-remove {
  flex: 0 0 auto;
  margin-left: auto;
  white-space: nowrap;
}

.scope-empty {
  margin: 4px 0 8px;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.add-row {
  padding-left: 0;
}

.scope-total {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(20, 167, 154, 0.08);
  color: var(--color-text);
  font-size: var(--text-base);
}

.scope-total strong {
  font-size: 18px;
  color: var(--el-color-primary);
}

.json-hint {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  line-height: 1.6;
}

@media (max-width: 768px) {
  .upload-page {
    gap: 12px;
  }

  .search-form :deep(.el-input) {
    width: 100%;
  }

  .scope-row {
    flex-wrap: wrap;
  }

  .scope-row .el-select,
  .scope-row .row-name,
  .scope-row .row-num {
    flex: 1 1 100%;
    width: 100%;
  }

  .row-times {
    display: none;
  }
}

/* ---------- 送审数字签名确认卡片 ---------- */
.submit-confirm-body {
  padding: 4px 0;
}

.submit-confirm-text {
  margin: 0 0 14px;
  font-size: var(--text-base);
  color: var(--color-text);
  line-height: 1.6;
}

.cert-status-card {
  padding: 14px 16px;
  border-radius: 10px;
  margin-bottom: 8px;
}

.cert-status-card--active {
  background: rgba(20, 167, 154, 0.08);
  border: 1px solid rgba(20, 167, 154, 0.35);
}

.cert-status-card--warning {
  background: rgba(230, 162, 60, 0.08);
  border: 1px solid rgba(230, 162, 60, 0.35);
}

.cert-status-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  margin-bottom: 6px;
}

.cert-desc {
  margin: 0 0 10px;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  line-height: 1.6;
}

.cert-meta {
  display: flex;
  gap: 16px;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.cert-meta code {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--el-color-primary);
}

.cert-link {
  display: inline-block;
  color: var(--el-color-primary);
  font-weight: 500;
  text-decoration: none;
  font-size: var(--text-sm);
}

.cert-link:hover {
  text-decoration: underline;
}

.attachment-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.attachment-hint {
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
