/** 通用分页信息 */
export interface PaginationInfo {
  page: number
  size: number
  total: number
  totalPages: number
}

/** 通用响应元数据 */
export interface ResponseMeta {
  requestId: string
  timestamp: string
  pagination: PaginationInfo | null
}

/** 通用API响应包装 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
  meta: ResponseMeta | null
}

/** Spring Data Page 响应 */
export interface SpringPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

/** 通用分页请求参数 */
export interface PageRequest {
  pageNum?: number
  pageSize?: number
  /** admin 系接口使用 page/size 命名；两种命名均保留兼容 */
  page?: number
  size?: number
  status?: number | string
  userType?: number | string
  title?: string
  accountingPeriod?: string
  sortBy?: string
  sortOrder?: string
  keyword?: string
  startTime?: string
  endTime?: string
}

/** 通用分页响应（request 拦截器归一后的前端形态，与 PagedItems 同构） */
export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

/** Frontend-normalized paged result returned by the request interceptor. */
export interface PagedItems<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}
