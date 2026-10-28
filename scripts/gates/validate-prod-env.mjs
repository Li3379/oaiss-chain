import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const currentFile = fileURLToPath(import.meta.url)
const root = path.resolve(path.dirname(currentFile), '../..')

function read(filePath) {
  return fs.readFileSync(filePath, 'utf8')
}

function parseEnvFile(filePath) {
  const result = new Map()
  for (const rawLine of read(filePath).split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) continue
    const idx = line.indexOf('=')
    if (idx === -1) continue
    const key = line.slice(0, idx).trim()
    const value = line.slice(idx + 1).trim()
    result.set(key, value)
  }
  return result
}

function collectPlaceholders(text) {
  const vars = new Set()
  const regex = /\$\{([A-Z0-9_]+)(?::[^}]*)?\}/g
  let match
  while ((match = regex.exec(text)) !== null) {
    vars.add(match[1])
  }
  return vars
}

function fail(message) {
  console.error(`ENV TEMPLATE VALIDATION FAILED: ${message}`)
  process.exitCode = 1
}

function warn(message) {
  console.warn(`ENV TEMPLATE VALIDATION WARNING: ${message}`)
}

function isPlaceholderValue(value) {
  return /^(change_me|replace_with|placeholder|todo|dummy)/i.test(value)
}

const args = process.argv.slice(2)
let requireRealSecrets = false
let envFileArg = '.env.prod.example'

for (const arg of args) {
  if (arg === '--require-real-secrets') {
    requireRealSecrets = true
    continue
  }
  envFileArg = arg
}

const envFilePath = path.isAbsolute(envFileArg)
  ? envFileArg
  : path.join(root, envFileArg)
const prodYamlPath = path.join(root, 'oaiss-chain-backend', 'src', 'main', 'resources', 'application-prod.yml')
const prodComposeCandidate = path.join(root, 'deploy', 'compose', 'docker-compose.prod.yml')
const prodComposePath = fs.existsSync(prodComposeCandidate) ? prodComposeCandidate : path.join(root, 'docker-compose.prod.yml')

const releaseComposeCandidate = path.join(root, 'deploy', 'compose', 'docker-compose.release.yml')
const releaseComposePath = fs.existsSync(releaseComposeCandidate) ? releaseComposeCandidate : path.join(root, 'docker-compose.release.yml')

const envMap = parseEnvFile(envFilePath)
const yamlText = read(prodYamlPath)
const composeText = read(prodComposePath)
const releaseComposeText = read(releaseComposePath)

const referencedVars = new Set([
  ...collectPlaceholders(yamlText),
  ...collectPlaceholders(composeText),
  ...collectPlaceholders(releaseComposeText),
])

const allowedMissing = new Set([
  'IMAGE_TAG',
  'HTTP_BIND_ADDRESS',
  'HTTP_PORT',
  'BACKEND_CPU_LIMIT',
  'BACKEND_MEMORY_LIMIT',
  'BACKEND_MEMORY_RESERVATION',
  'FRONTEND_CPU_LIMIT',
  'FRONTEND_MEMORY_LIMIT',
  'ML_CPU_LIMIT',
  'ML_MEMORY_LIMIT',
  'ML_MODEL_DIR',
  'SERVER_SHUTDOWN_TIMEOUT',
  'DB_POOL_IDLE_TIMEOUT_MS',
  'DB_POOL_MAX_LIFETIME_MS',
  'REDIS_TIMEOUT_MS',
  'MINIO_MAX_FILE_SIZE_BYTES',
  'FABRIC_CONNECT_TIMEOUT_SECONDS',
  'FABRIC_SUBMIT_TIMEOUT_SECONDS',
])

for (const variable of referencedVars) {
  if (!envMap.has(variable) && !allowedMissing.has(variable)) {
    fail(`${path.basename(envFilePath)} is missing required variable ${variable}`)
  }
}

const criticalNoPlaceholder = [
  'DB_PASSWORD',
  'REDIS_PASSWORD',
  'JWT_SECRET',
  'RSA_KEK',
  'MINIO_ACCESS_KEY',
  'MINIO_SECRET_KEY',
  'ML_SERVICE_SECRET',
]

for (const key of criticalNoPlaceholder) {
  const value = envMap.get(key)
  if (!value) {
    fail(`critical variable ${key} is blank`)
    continue
  }
  if (isPlaceholderValue(value)) {
    const message = `critical variable ${key} still uses a placeholder value in ${path.basename(envFilePath)}`
    if (requireRealSecrets) {
      fail(message)
    } else {
      warn(message)
    }
  }
}

const requiredReleaseImages = [
  'BACKEND_IMAGE',
  'FRONTEND_IMAGE',
  'ML_SERVICE_IMAGE',
]

if (requireRealSecrets) {
  for (const key of requiredReleaseImages) {
    const value = envMap.get(key)
    if (!value) {
      fail(`release image variable ${key} is blank`)
      continue
    }
    if (isPlaceholderValue(value)) {
      fail(`release image variable ${key} still uses a placeholder value in ${path.basename(envFilePath)}`)
    }
  }
}

const activeProfiles = envMap.get('SPRING_PROFILES_ACTIVE') ?? ''
const profileList = activeProfiles
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean)
const fabricEnabled = /^true$/i.test(envMap.get('FABRIC_ENABLED') ?? 'false')
const isRemoteLike = profileList.some((profile) => ['prod', 'production', 'staging'].includes(profile))

if (isRemoteLike && !profileList.includes('fabric')) {
  fail('SPRING_PROFILES_ACTIVE must include fabric for staging/production deployments')
}

if (isRemoteLike && !fabricEnabled) {
  fail('FABRIC_ENABLED must be true for staging/production deployments that require real blockchain data')
}

for (const key of ['BACKEND_LOG_DIR', 'FRONTEND_LOG_DIR', 'ML_LOG_DIR']) {
  const value = envMap.get(key)
  if (!value) {
    fail(`${key} must be set so release deployments persist container logs`)
  }
}

if (fabricEnabled) {
  for (const key of ['FABRIC_SECRETS_DIR', 'FABRIC_SECRETS_MOUNT_PATH', 'FABRIC_PEER_TLS_CERT_PATH', 'FABRIC_CERT_PATH', 'FABRIC_KEY_PATH']) {
    const value = envMap.get(key)
    if (!value) {
      fail(`${key} must be set when FABRIC_ENABLED=true`)
    }
  }
}

if (requireRealSecrets && /^true$/i.test(envMap.get('REQUIRE_OPS_SECRETS') ?? 'false')) {
  const grafanaAdminPassword = envMap.get('GRAFANA_ADMIN_PASSWORD')
  if (!grafanaAdminPassword) {
    fail('GRAFANA_ADMIN_PASSWORD must be set when REQUIRE_OPS_SECRETS=true')
  } else if (isPlaceholderValue(grafanaAdminPassword)) {
    fail(`GRAFANA_ADMIN_PASSWORD still uses a placeholder value in ${path.basename(envFilePath)}`)
  }

  const alertSmtpHost = envMap.get('ALERT_SMTP_HOST') ?? ''
  if (alertSmtpHost) {
    const alertSmtpPassword = envMap.get('ALERT_SMTP_PASSWORD')
    if (!alertSmtpPassword) {
      fail('ALERT_SMTP_PASSWORD must be set when ALERT_SMTP_HOST is configured')
    } else if (isPlaceholderValue(alertSmtpPassword)) {
      fail(`ALERT_SMTP_PASSWORD still uses a placeholder value in ${path.basename(envFilePath)}`)
    }
  }

  const alertWebhookUrl = envMap.get('ALERT_WEBHOOK_URL') ?? ''
  if (alertWebhookUrl) {
    const alertWebhookSecret = envMap.get('ALERT_WEBHOOK_SECRET')
    if (!alertWebhookSecret) {
      fail('ALERT_WEBHOOK_SECRET must be set when ALERT_WEBHOOK_URL is configured')
    } else if (isPlaceholderValue(alertWebhookSecret)) {
      fail(`ALERT_WEBHOOK_SECRET still uses a placeholder value in ${path.basename(envFilePath)}`)
    }
  }
}

if (requireRealSecrets && /^true$/i.test(envMap.get('FABRIC_CA_ENABLED') ?? 'false')) {
  const fabricCaAdminPassword = envMap.get('FABRIC_CA_ADMIN_PASSWORD')
  if (!fabricCaAdminPassword) {
    fail('FABRIC_CA_ADMIN_PASSWORD must be set when FABRIC_CA_ENABLED=true')
  } else if (isPlaceholderValue(fabricCaAdminPassword)) {
    fail(`FABRIC_CA_ADMIN_PASSWORD still uses a placeholder value in ${path.basename(envFilePath)}`)
  }
}

if (requireRealSecrets && fabricEnabled) {
  const fabricCouchdbPassword = envMap.get('FABRIC_COUCHDB_PASSWORD')
  if (!fabricCouchdbPassword) {
    fail('FABRIC_COUCHDB_PASSWORD must be set when FABRIC_ENABLED=true')
  } else if (isPlaceholderValue(fabricCouchdbPassword)) {
    fail(`FABRIC_COUCHDB_PASSWORD still uses a placeholder value in ${path.basename(envFilePath)}`)
  }
}

const requiredProdHosts = [
  ['CORS_ALLOWED_ORIGINS', /localhost|127\.0\.0\.1/i],
  ['DB_URL', /localhost|127\.0\.0\.1/i],
  ['REDIS_HOST', /localhost|127\.0\.0\.1/i],
  ['MINIO_ENDPOINT', /localhost|127\.0\.0\.1/i],
]

for (const [key, pattern] of requiredProdHosts) {
  const value = envMap.get(key)
  if (value && pattern.test(value)) {
    fail(`${key} must not point to localhost/127.0.0.1 in ${path.basename(envFilePath)}`)
  }
}

const mlServiceUrl = envMap.get('ML_SERVICE_URL')
if (mlServiceUrl && !/^http:\/\/ml-service:8001$|^https?:\/\/[^/]+/i.test(mlServiceUrl)) {
  fail('ML_SERVICE_URL should be an internal service URL such as http://ml-service:8001 or a real hosted endpoint')
}

if (process.exitCode !== 1) {
  console.log(`Environment template validation passed for ${path.basename(envFilePath)}`)
}
