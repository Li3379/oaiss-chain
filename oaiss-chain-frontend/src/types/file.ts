/** 文件上传结果 */
export interface UploadResult {
  objectName: string
  url: string
  size: number
  contentType: string
}

/** 文件信息 */
export interface FileInfo {
  objectName: string
  size: number
  contentType: string
  etag: string
}

/** 文件列表结果 */
export interface FileListResult {
  files: FileInfo[]
  total: number
  page: number
  size: number
}
