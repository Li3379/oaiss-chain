#!/usr/bin/env node
/**
 * 基础设施镜像可用性门禁。
 *
 * 背景：`minio/minio` 曾长期是对象存储的事实标准镜像。2025 年上游把它从
 * Docker Hub 彻底移除（仓库 API 返回 `object not found`，任何 tag 都拉不到）。
 * 结果是 E2E 工作流在 GitHub Runner 上 100% 失败，而报错只是
 * "pull access denied ... repository does not exist"——看起来像权限问题，
 * 实际是镜像没了。
 *
 * 这类缺陷的特征：本地跑得好好的（镜像早有缓存），CI 却永远起不来；
 * 报错信息指向「权限」，真相是「上游删了」。
 *
 * 本门禁做两件事：
 *   1. 列出所有 compose / workflow 中引用的镜像
 *   2. 检查是否命中已知「已下架」清单——命中即失败，并在修复前不会溜过去
 *
 * 用法：node scripts/gates/image-availability-verify.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..', '..')

/**
 * 已知从上游下架、任何 registry 都拉不到的镜像前缀。
 *
 * 命中后必须改用替代实现（如 S3 兼容的 LocalStack），而不是继续引用。
 */
const RETIRED_IMAGES = [
  {
    prefix: 'minio/minio',
    reason: 'MinIO 已于 2025 年将 minio/minio 从 Docker Hub 移除（仓库返回 object not found），任何 tag 均无法拉取',
    replacement: 'localstack/localstack（S3 兼容，已实测支持后端使用的全部 10 项 MinIO API）',
  },
]

/** 需要检查的配置文件 */
const TARGET_FILES = [
  'docker-compose.yml',
  'deploy/compose/docker-compose.infra.yml',
  'deploy/compose/docker-compose.prod.yml',
  'deploy/compose/docker-compose.release.yml',
  'deploy/compose/docker-compose.fabric.yml',
  '.github/workflows/e2e-tests.yml',
  '.github/workflows/lint.yml',
  '.github/workflows/deploy-release.yml',
  '.github/workflows/release-images.yml',
  '.github/workflows/load-tests.yml',
]

/** 从文本中提取镜像引用 */
function extractImages(text) {
  const images = []
  // compose: "image: xxx" / workflow services: "image: xxx"
  for (const m of text.matchAll(/^\s*image:\s*['"]?([^\s'"#]+)['"]?/gm)) {
    images.push({ image: m[1], line: text.slice(0, m.index).split('\n').length })
  }
  return images
}

function main() {
  console.log('=== 基础设施镜像可用性门禁 ===\n')

  let failures = 0
  let checked = 0

  for (const rel of TARGET_FILES) {
    const path = join(ROOT, rel)
    if (!existsSync(path)) continue

    const text = readFileSync(path, 'utf8')
    const images = extractImages(text)
    if (images.length === 0) continue

    for (const { image, line } of images) {
      checked++
      const retired = RETIRED_IMAGES.find(r => image.startsWith(r.prefix))
      if (retired) {
        failures++
        console.log(`✗ ${rel}:${line}`)
        console.log(`    镜像: ${image}`)
        console.log(`    原因: ${retired.reason}`)
        console.log(`    替代: ${retired.replacement}`)
      }
    }
  }

  console.log(`\n共检查 ${checked} 处镜像引用`)

  if (failures > 0) {
    console.log(`\n❌ ${failures} 处引用了已下架镜像 — 相关 CI/部署会永久失败`)
    process.exit(1)
  }
  console.log('✅ 未发现引用已下架镜像')
}

main()
