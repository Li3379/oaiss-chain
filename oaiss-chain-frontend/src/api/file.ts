/**
 * ==============================================================================
 * 【API 模块】：MinIO 对象存储证据附件上传与文件生命周期 (File API)
 * 【对应后端】：com.oaiss.chain.controller.FileController (/file)
 * 【主要服务页面】：
 *   - 碳核算填报附件上传: views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
 *   - 审核员查阅附件凭据: views/auditor/AuditList.vue (/auditor/audit/list)
 *   - 碳中和项目材料提交: views/enterprise/CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
 * ==============================================================================
 */
import request from './request'
import type { UploadResult, FileInfo, FileListResult } from '../types'
import type { AxiosProgressEvent } from 'axios'

const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB

/**
 * 【业务功能】：上传单个碳排放监测报告、化验单凭据或证书文件至 MinIO 对象存储
 * 【对应前端页面】：CarbonUpload.vue (/enterprise/carbon/upload), CarbonNeutral.vue
 * 【界面触发操作】：点击或拖拽文件到“上传凭证材料”区域触发上传
 * 【后端对应端点】：FileController -> POST /file/upload
 */
export function uploadFile(
  file: File,
  folder = '',
  onProgress?: (event: AxiosProgressEvent) => void,
): Promise<UploadResult> {
  if (!file) return Promise.reject(new Error('请选择文件'))
  if (file.size > MAX_FILE_SIZE) return Promise.reject(new Error('文件大小不能超过50MB'))

  const formData = new FormData()
  formData.append('file', file)
  if (folder) {
    formData.append('folder', folder)
  }
  return request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  })
}

/**
 * 【业务功能】：批量上传多份核查佐证附件或图片凭据
 * 【对应前端页面】：CarbonUpload.vue, CarbonNeutralDetail.vue
 * 【界面触发操作】：多选文件上传时批量并发执行
 * 【后端对应端点】：FileController -> POST /file/upload/batch
 */
export function batchUploadFiles(
  files: File[],
  folder = '',
  onProgress?: (event: AxiosProgressEvent) => void,
): Promise<UploadResult[]> {
  if (!Array.isArray(files) || files.length === 0) return Promise.reject(new Error('请选择文件'))
  for (const file of files) {
    if (file.size > MAX_FILE_SIZE) return Promise.reject(new Error(`${file.name} 超过50MB限制`))
  }

  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  if (folder) {
    formData.append('folder', folder)
  }
  return request.post('/file/upload/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  })
}

/**
 * 【业务功能】：安全下载已上传至 MinIO 的证据原件二进制流
 * 【对应前端页面】：AuditList.vue, CarbonUpload.vue
 * 【界面触发操作】：点击附件列表中的【下载原件】图标
 * 【后端对应端点】：FileController -> GET /file/download
 */
export function downloadFile(objectName: string): Promise<Blob> {
  return request.get('/file/download', {
    params: { objectName },
    responseType: 'blob',
  })
}

/**
 * 【业务功能】：从对象存储中删除指定的作废文件
 * 【对应前端页面】：CarbonUpload.vue
 * 【界面触发操作】：上传列表中点击单个文件右侧的【删除】图标
 * 【后端对应端点】：FileController -> DELETE /file
 */
export function deleteFile(objectName: string): Promise<void> {
  return request.delete('/file', { params: { objectName } })
}

/**
 * 【业务功能】：批量删除多个作废的凭证文件
 * 【对应前端页面】：CarbonUpload.vue, CarbonNeutral.vue
 * 【界面触发操作】：重置表单或批量清空上传附件列表
 * 【后端对应端点】：FileController -> DELETE /file/batch
 */
export function batchDeleteFiles(objectNames: string[]): Promise<void> {
  return request.delete('/file/batch', { data: { objectNames } })
}

/**
 * 【业务功能】：获取文件元数据（大小、哈希校验码、MIME 类型）
 * 【对应前端页面】：AuditList.vue
 * 【界面触发操作】：审核员审查附件完整性时校验文件指纹
 * 【后端对应端点】：FileController -> GET /file/info
 */
export function getFileInfo(objectName: string): Promise<FileInfo> {
  return request.get('/file/info', { params: { objectName } })
}

/**
 * 【业务功能】：检查指定对象文件是否存在于 MinIO 存储桶中
 * 【对应前端页面】：CarbonUpload.vue, AuditList.vue
 * 【界面触发操作】：预览前探测文件有效性
 * 【后端对应端点】：FileController -> GET /file/exists
 */
export function fileExists(objectName: string): Promise<boolean> {
  return request.get('/file/exists', { params: { objectName } })
}

/**
 * 【业务功能】：获取带时效安全保护的直接下载/在线预览预签名 URL
 * 【对应前端页面】：AuditList.vue, CarbonUpload.vue
 * 【界面触发操作】：点击附件进行在线预览（图片/PDF）
 * 【后端对应端点】：FileController -> GET /file/presigned-url
 */
export function getPresignedUrl(objectName: string): Promise<string> {
  return request.get('/file/presigned-url', { params: { objectName } })
}

/**
 * 【业务功能】：获取前端直传 MinIO 的预签名安全 PUT URL
 * 【对应前端页面】：大文件分片直传场景
 * 【界面触发操作】：直传通道初始化
 * 【后端对应端点】：FileController -> GET /file/presigned-upload-url
 */
export function getPresignedUploadUrl(objectName: string): Promise<string> {
  return request.get('/file/presigned-upload-url', { params: { objectName } })
}

/**
 * 【业务功能】：分页列出指定目录前缀下的所有归档文件
 * 【对应前端页面】：文件归档列表
 * 【界面触发操作】：浏览凭证库
 * 【后端对应端点】：FileController -> GET /file/list
 */
export function listFiles(params?: Record<string, unknown>): Promise<FileListResult> {
  return request.get('/file/list', { params })
}

/**
 * 【业务功能】：在对象存储桶内部复制或归档文件
 * 【对应前端页面】：报告存证归档
 * 【界面触发操作】：归档触发
 * 【后端对应端点】：FileController -> POST /file/copy
 */
export function copyFile(sourceObjectName: string, destinationObjectName: string): Promise<void> {
  return request.post('/file/copy', { sourceObjectName, destinationObjectName })
}
