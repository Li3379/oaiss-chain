/**
 * 状态呈现单一数据源（图标 + 颜色 + 文案 key）
 * 目的：消除各页面各自维护的 statusMap 漂移（历史问题：SystemCarbon 色板错位、en 界面中文直出、无图标）
 */

import type { Component } from 'vue'
import {
  CircleCheck,
  CircleClose,
  Clock,
  Document,
  Link,
  Loading,
  Remove,
  Search,
  Tools,
  Trophy,
  VideoPause,
} from '@element-plus/icons-vue'

export type StatusTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export interface StatusMeta {
  /** Element Plus tag 语义色 */
  type: StatusTagType
  /** 三重编码用图标（Element Plus SVG 图标组件；规范要求图标用 SVG，不用 emoji） */
  icon: Component
  /** i18n 键（各语言同键） */
  labelKey: string
}

/** 碳核算报告状态（对齐后端 ReportStatusEnum） */
export const REPORT_STATUS_META: Record<number, StatusMeta> = {
  0: { type: 'info', icon: Document, labelKey: 'statusMeta.draft' },
  1: { type: 'warning', icon: Clock, labelKey: 'statusMeta.submitted' },
  2: { type: 'primary', icon: Search, labelKey: 'statusMeta.underReview' },
  3: { type: 'success', icon: CircleCheck, labelKey: 'statusMeta.approved' },
  4: { type: 'danger', icon: CircleClose, labelKey: 'statusMeta.rejected' },
  5: { type: 'success', icon: Link, labelKey: 'statusMeta.onChain' },
}

/** 碳中和项目状态（对齐 PROJECT_STATUS） */
export const PROJECT_STATUS_META: Record<number, StatusMeta> = {
  0: { type: 'info', icon: Document, labelKey: 'statusMeta.projectDraft' },
  1: { type: 'warning', icon: Clock, labelKey: 'statusMeta.projectPending' },
  2: { type: 'success', icon: CircleCheck, labelKey: 'statusMeta.projectApproved' },
  3: { type: 'primary', icon: Tools, labelKey: 'statusMeta.projectImplementing' },
  4: { type: 'success', icon: Trophy, labelKey: 'statusMeta.projectCompleted' },
  5: { type: 'info', icon: VideoPause, labelKey: 'statusMeta.projectTerminated' },
  6: { type: 'danger', icon: CircleClose, labelKey: 'statusMeta.projectRejected' },
}

/** 交易状态（对齐后端 TradeStatusEnum：0 待处理 / 1 处理中 / 2 已完成 / 3 已取消 / 4 失败） */
export const TRADE_STATUS_META: Record<number, StatusMeta> = {
  0: { type: 'warning', icon: Clock, labelKey: 'statusMeta.tradePending' },
  1: { type: 'primary', icon: Tools, labelKey: 'statusMeta.tradeProcessing' },
  2: { type: 'success', icon: CircleCheck, labelKey: 'statusMeta.tradeCompleted' },
  3: { type: 'info', icon: VideoPause, labelKey: 'statusMeta.tradeCancelled' },
  4: { type: 'danger', icon: CircleClose, labelKey: 'statusMeta.tradeFailed' },
}

export const STATUS_DOMAIN_META = {
  report: REPORT_STATUS_META,
  project: PROJECT_STATUS_META,
  trade: TRADE_STATUS_META,
} as const

export type StatusDomain = keyof typeof STATUS_DOMAIN_META
