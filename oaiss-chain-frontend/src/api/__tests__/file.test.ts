import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn(),
}))

vi.mock('../request', () => ({
  default: {
    post: requestMocks.postMock,
    get: requestMocks.getMock,
    put: requestMocks.putMock,
    delete: requestMocks.deleteMock,
  },
}))

import {
  batchDeleteFiles,
  batchUploadFiles,
  copyFile,
  deleteFile,
  downloadFile,
  fileExists,
  getFileInfo,
  getPresignedUrl,
  getPresignedUploadUrl,
  listFiles,
  uploadFile,
} from '../file'

describe('file api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMocks.postMock.mockResolvedValue(undefined)
    requestMocks.getMock.mockResolvedValue({})
    requestMocks.deleteMock.mockResolvedValue(undefined)
  })

  it('exports all 11 functions', () => {
    expect(typeof uploadFile).toBe('function')
    expect(typeof batchUploadFiles).toBe('function')
    expect(typeof downloadFile).toBe('function')
    expect(typeof deleteFile).toBe('function')
    expect(typeof batchDeleteFiles).toBe('function')
    expect(typeof getFileInfo).toBe('function')
    expect(typeof fileExists).toBe('function')
    expect(typeof getPresignedUrl).toBe('function')
    expect(typeof getPresignedUploadUrl).toBe('function')
    expect(typeof listFiles).toBe('function')
    expect(typeof copyFile).toBe('function')
  })

  it('forwards GET/DELETE requests to correct endpoints', async () => {
    await downloadFile('doc.pdf')
    await deleteFile('doc.pdf')
    await batchDeleteFiles(['a.pdf', 'b.pdf'])
    await getFileInfo('doc.pdf')
    await fileExists('doc.pdf')
    await getPresignedUrl('doc.pdf')
    await getPresignedUploadUrl('doc.pdf')
    await listFiles({ folder: 'docs' })

    expect(requestMocks.getMock).toHaveBeenNthCalledWith(1, '/file/download', {
      params: { objectName: 'doc.pdf' },
      responseType: 'blob',
    })
    expect(requestMocks.deleteMock).toHaveBeenNthCalledWith(1, '/file', {
      params: { objectName: 'doc.pdf' },
    })
    expect(requestMocks.deleteMock).toHaveBeenNthCalledWith(2, '/file/batch', {
      data: { objectNames: ['a.pdf', 'b.pdf'] },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(2, '/file/info', {
      params: { objectName: 'doc.pdf' },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(3, '/file/exists', {
      params: { objectName: 'doc.pdf' },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(4, '/file/presigned-url', {
      params: { objectName: 'doc.pdf' },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(5, '/file/presigned-upload-url', {
      params: { objectName: 'doc.pdf' },
    })
    expect(requestMocks.getMock).toHaveBeenNthCalledWith(6, '/file/list', {
      params: { folder: 'docs' },
    })
  })

  it('forwards copy file to correct endpoint', async () => {
    await copyFile('src.pdf', 'dest.pdf')
    expect(requestMocks.postMock).toHaveBeenCalledWith('/file/copy', {
      sourceObjectName: 'src.pdf',
      destinationObjectName: 'dest.pdf',
    })
  })

  it('rejects uploadFile with no file', async () => {
    await expect(uploadFile(null as unknown as File)).rejects.toThrow('请选择文件')
  })

  it('rejects uploadFile exceeding 50MB', async () => {
    const bigFile = { size: 51 * 1024 * 1024, name: 'big.bin' } as File
    await expect(uploadFile(bigFile)).rejects.toThrow('文件大小不能超过50MB')
  })

  it('rejects batchUploadFiles with empty array', async () => {
    await expect(batchUploadFiles([])).rejects.toThrow('请选择文件')
  })

  it('rejects batchUploadFiles when any file exceeds 50MB', async () => {
    const smallFile = { size: 100, name: 'ok.txt' } as File
    const bigFile = { size: 51 * 1024 * 1024, name: 'big.bin' } as File
    await expect(batchUploadFiles([smallFile, bigFile])).rejects.toThrow('超过50MB限制')
  })
})