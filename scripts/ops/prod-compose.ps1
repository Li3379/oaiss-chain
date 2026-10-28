[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string[]]$ComposeArgs,

    [Parameter()]
    [string]$EnvFile = ".env.prod.example",

    [Parameter()]
    [string]$ComposeFile = "docker-compose.prod.yml"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$envFilePath = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    $EnvFile
} else {
    Join-Path $projectRoot $EnvFile
}

$composeFilePath = if ([System.IO.Path]::IsPathRooted($ComposeFile)) {
    $ComposeFile
} else {
    $directPath = Join-Path $projectRoot $ComposeFile
    $deployPath = Join-Path (Join-Path $projectRoot "deploy\compose") $ComposeFile
    if (Test-Path $directPath) {
        $directPath
    } elseif (Test-Path $deployPath) {
        $deployPath
    } else {
        $directPath
    }
}

if (-not (Test-Path $envFilePath)) {
    throw "Env file not found: $envFilePath"
}

if (-not (Test-Path $composeFilePath)) {
    throw "Compose file not found: $composeFilePath"
}

$varsToClear = @(
    "SPRING_PROFILES_ACTIVE",
    "LOG_LEVEL",
    "APP_LOG_LEVEL",
    "CORS_ALLOWED_ORIGINS",
    "DB_URL",
    "DB_HOST",
    "DB_PORT",
    "DB_NAME",
    "DB_USERNAME",
    "DB_PASSWORD",
    "DB_POOL_MIN_IDLE",
    "DB_POOL_MAX_SIZE",
    "DB_POOL_CONNECTION_TIMEOUT_MS",
    "DB_POOL_LEAK_DETECTION_MS",
    "REDIS_HOST",
    "REDIS_PORT",
    "REDIS_DATABASE",
    "REDIS_PASSWORD",
    "JWT_SECRET",
    "JWT_EXPIRATION_MS",
    "JWT_REFRESH_EXPIRATION_MS",
    "RSA_KEK",
    "MINIO_ENDPOINT",
    "MINIO_ACCESS_KEY",
    "MINIO_SECRET_KEY",
    "MINIO_BUCKET",
    "MINIO_PRESIGNED_URL_EXPIRY_SECONDS",
    "ML_SERVICE_URL",
    "ML_SERVICE_SECRET",
    "ML_SERVICE_CONNECT_TIMEOUT",
    "ML_SERVICE_READ_TIMEOUT",
    "REQUIRE_OPS_SECRETS",
    "GRAFANA_ADMIN_PASSWORD",
    "FABRIC_ENABLED",
    "FABRIC_MSP_ID",
    "FABRIC_CHANNEL_NAME",
    "FABRIC_CHAINCODE_NAME",
    "FABRIC_PEER_ENDPOINT",
    "FABRIC_TLS_ENABLED",
    "FABRIC_PEER_TLS_CERT_PATH",
    "FABRIC_CERT_PATH",
    "FABRIC_KEY_PATH",
    "FABRIC_CA_ENABLED",
    "FABRIC_CA_ENDPOINT",
    "FABRIC_CA_ADMIN_NAME",
    "FABRIC_CA_ADMIN_PASSWORD"
)

$savedValues = @{}
foreach ($name in $varsToClear) {
    $existing = [System.Environment]::GetEnvironmentVariable($name, "Process")
    if ($null -ne $existing) {
        $savedValues[$name] = $existing
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}

$previousDisableEnv = [System.Environment]::GetEnvironmentVariable("COMPOSE_DISABLE_ENV_FILE", "Process")
$hadDisableEnv = $null -ne $previousDisableEnv
$env:COMPOSE_DISABLE_ENV_FILE = "1"

try {
    Push-Location $projectRoot
    & docker compose --env-file $envFilePath -f $composeFilePath @ComposeArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location

    foreach ($name in $varsToClear) {
        if ($savedValues.ContainsKey($name)) {
            [System.Environment]::SetEnvironmentVariable($name, $savedValues[$name], "Process")
        }
    }

    if ($hadDisableEnv) {
        [System.Environment]::SetEnvironmentVariable("COMPOSE_DISABLE_ENV_FILE", $previousDisableEnv, "Process")
    } else {
        Remove-Item Env:COMPOSE_DISABLE_ENV_FILE -ErrorAction SilentlyContinue
    }
}
