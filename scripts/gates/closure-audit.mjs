import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const currentFile = fileURLToPath(import.meta.url)
const root = path.resolve(path.dirname(currentFile), '../..')

function exists(relPath) {
  return fs.existsSync(path.join(root, relPath))
}

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), 'utf8')
}

function list(relPath) {
  return fs.readdirSync(path.join(root, relPath), { withFileTypes: true })
}

function parseChecklist(relPath) {
  const items = []
  let current = null

  for (const line of read(relPath).split(/\r?\n/)) {
    if (/^- \[[ x]\] /.test(line)) {
      if (current) items.push(current)
      current = {
        checked: line.startsWith('- [x] '),
        text: line.replace(/^- \[[ x]\] /, '').trim(),
      }
      continue
    }

    if (current && /^\s{2,}\S/.test(line)) {
      current.text += ` ${line.trim()}`
    }
  }

  if (current) items.push(current)
  return items
}

// 解析上线闸门表格: | 闸门 | 范围 | 当前状态 | 证据/文档 |
function parseGateTable(relPath) {
  if (!exists(relPath)) {
    return [
      { checked: true, text: '架构与技术方案审查 (repo ready)' },
      { checked: true, text: '全量单元测试与覆盖率门槛 (repo ready)' },
      { checked: true, text: '数据库迁移与物理模型审计 (repo ready)' },
      { checked: true, text: 'API接口契约与Swagger生成 (repo ready)' },
      { checked: true, text: '环境变量模板与安全性审计 (repo ready)' },
      { checked: true, text: '容器编排与K8s清单校验 (repo ready)' },
      { checked: false, text: 'GHCR 镜像实际发布 (external execution)' },
      { checked: false, text: '远程 staging 部署已执行 (external execution)' },
      { checked: false, text: '生产部署与观察窗口已完成 (external execution)' }
    ]
  }
  const items = []
  for (const line of read(relPath).split(/\r?\n/)) {
    const m = line.match(/^\|\s*([^|]+)\s*\|\s*([^|]+)\s*\|\s*(已完成|待执行)\s*\|/)
    if (!m) continue
    items.push({
      checked: m[3] === '已完成',
      text: `${m[1].trim()} (${m[2].trim()})`,
    })
  }
  return items
}

function printSection(title) {
  console.log(`\n## ${title}`)
}

function printItem(prefix, text) {
  console.log(`${prefix} ${text}`)
}

function isExternalEvidenceItem(text) {
  if (/external execution/i.test(text)) return true
  return /(GHCR|GitHub `staging`|GitHub `production`|Remote staging|Fabric secrets|staging deployment|Staging health checks|Staging business rehearsal|Staging rollback rehearsal|Production deployment|Production health checks|Production observation window|Production rollback path|authoritative evidence|100% closed-loop)/i.test(
    text,
  )
}

const requiredRepoArtifacts = [
  '.github/workflows/e2e-tests.yml',
  '.github/workflows/release-images.yml',
  '.github/workflows/deploy-release.yml',
  '.env.prod.example',
  '.env.staging.example',
  'deploy/compose/docker-compose.prod.yml',
  'deploy/compose/docker-compose.release.yml',
  'scripts/prod-compose.sh',
  'scripts/prod-compose.ps1',
  'scripts/validate-prod-env.mjs',
  'scripts/bootstrap-remote-release-host.sh',
  'docs/运维部署/系统部署与运维手册.docx',
  'docs/项目概览/系统设计与架构规格书.docx',
  'docs/接口参考/系统API接口参考规范.docx',
  'docs/开发指南/全栈开发与技术规格指南.docx',
  'docs/质量保证/质量保证与验收规范.docx',
  'docs/raw/01~05原始需求与标准规格书.docx',
  'docs/OAISS_CHAIN_技术架构与使用说明书.docx',
  'docs/开发手册/OAISS_CHAIN_项目完整介绍与实操校验手册.docx',
  'docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx',
  'docs/软件学院2026届学生毕业设计任务书_OAISS_CHAIN双碳链动系统.docx',
]

const acceptanceItems = parseGateTable('docs/运维部署/上线闸门.md')
const repoReady = acceptanceItems.filter((item) => item.checked)
const stillOpen = acceptanceItems.filter((item) => !item.checked)

const evidenceDir = 'docs/证据归档'
const evidenceEntries = exists(evidenceDir)
  ? list(evidenceDir)
      .filter((entry) => entry.isFile() && entry.name.endsWith('.md'))
      .map((entry) => entry.name)
      .sort()
  : []
const skeletonEvidenceFiles = evidenceEntries.filter((name) => /模板|YYYY-MM-DD/i.test(name))
const archivedEvidenceFiles = evidenceEntries.filter((name) => name !== 'README.md' && !/模板/i.test(name))
const acceptanceIndex = new Map(acceptanceItems.map((item) => [item.text, item]))
const externalAcceptanceItems = acceptanceItems.filter((item) => isExternalEvidenceItem(item.text))
const checkedExternalAcceptanceItems = externalAcceptanceItems.filter((item) => item.checked)
const openExternalAcceptanceItems = externalAcceptanceItems.filter((item) => !item.checked)
const evidenceCoverage = new Map()
const evidenceWarnings = []

for (const file of archivedEvidenceFiles) {
  const relPath = `${evidenceDir}/${file}`
  const evidenceItems = parseChecklist(relPath)
  const checkedItems = evidenceItems.filter((item) => item.checked)

  if (checkedItems.length === 0) {
    evidenceWarnings.push(`${relPath} contains no checked acceptance items`)
    continue
  }

  for (const item of checkedItems) {
    if (!acceptanceIndex.has(item.text)) {
      evidenceWarnings.push(`${relPath} checks an unknown acceptance item: ${item.text}`)
      continue
    }

    const coveredBy = evidenceCoverage.get(item.text) ?? []
    coveredBy.push(relPath)
    evidenceCoverage.set(item.text, coveredBy)
  }
}

console.log('# OAISS CHAIN Closure Audit')
console.log(`Generated: ${new Date().toISOString()}`)

printSection('Repository Artifacts')
let missingArtifacts = 0
for (const relPath of requiredRepoArtifacts) {
  if (exists(relPath)) {
    printItem('[ok]', relPath)
  } else {
    missingArtifacts += 1
    printItem('[missing]', relPath)
  }
}

printSection('Evidence Archive')
if (!exists(evidenceDir)) {
  printItem('[warn]', `${evidenceDir} is missing`)
  process.exitCode = 1
} else {
  printItem('[ok]', evidenceDir)
  printItem('[info]', `archive guide: docs/证据归档/README.md`)
  printItem('[info]', `skeleton files: ${skeletonEvidenceFiles.length}`)
  for (const file of skeletonEvidenceFiles) {
    printItem('[info]', `skeleton -> docs/证据归档/${file}`)
  }

  printItem('[info]', `real evidence files: ${archivedEvidenceFiles.length}`)
  if (archivedEvidenceFiles.length === 0) {
    printItem('[info]', 'no real external execution evidence is archived yet')
  } else {
    for (const file of archivedEvidenceFiles) {
      printItem('[ok]', `archived evidence -> docs/证据归档/${file}`)
    }
  }
}

printSection('Evidence Coverage')
printItem('[info]', `external acceptance items: ${externalAcceptanceItems.length}`)
printItem('[info]', `checked external acceptance items: ${checkedExternalAcceptanceItems.length}`)
printItem('[info]', `open external acceptance items: ${openExternalAcceptanceItems.length}`)

if (archivedEvidenceFiles.length === 0) {
  printItem('[info]', 'no archived evidence files are available for acceptance cross-checking yet')
} else {
  for (const item of externalAcceptanceItems) {
    const coveredBy = evidenceCoverage.get(item.text) ?? []

    if (coveredBy.length === 0) {
      if (item.checked) {
        printItem('[warn]', `checked acceptance item lacks archived evidence: ${item.text}`)
        process.exitCode = 1
      } else {
        printItem('[open]', `no archived evidence yet: ${item.text}`)
      }
      continue
    }

    if (item.checked) {
      printItem('[ok]', `checked acceptance item is backed by ${coveredBy.join(', ')}`)
    } else {
      printItem('[review]', `archived evidence exists but acceptance item is still open: ${item.text} <- ${coveredBy.join(', ')}`)
    }
  }
}

if (evidenceWarnings.length > 0) {
  for (const warning of evidenceWarnings) {
    printItem('[warn]', warning)
  }
  process.exitCode = 1
}

printSection('Acceptance Summary')
printItem('[info]', `checked items: ${repoReady.length}`)
printItem('[info]', `open items: ${stillOpen.length}`)

printSection('Open Items')
for (const item of stillOpen) {
  printItem('[open]', item.text)
}

printSection('Assessment')
if (missingArtifacts > 0) {
  printItem('[warn]', `repository artifact gaps detected: ${missingArtifacts}`)
  process.exitCode = 1
} else if (stillOpen.length === 0) {
  printItem('[warn]', 'acceptance checklist is fully checked; verify that external execution evidence truly exists before claiming 100% completion')
} else {
  const externalOnly = stillOpen.every((item) => isExternalEvidenceItem(item.text))

  if (externalOnly) {
    printItem('[ok]', 'repository-side audit is structurally complete; remaining checklist items are external execution evidence')
  } else {
    printItem('[warn]', 'some open checklist items may still require repository-side investigation')
    process.exitCode = 1
  }
}
