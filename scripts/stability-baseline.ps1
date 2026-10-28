param(
    [switch]$WithMlHealth,
    [switch]$WithBlockchain,
    [switch]$WithDeliveryGuards,
    [switch]$WithBackendTests,
    [switch]$WithBackendVerify,
    [switch]$WithFrontendTests,
    [switch]$WithFrontendBuild,
    [switch]$WithFrontendE2E,
    [switch]$All,
    [switch]$Help
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$bashScript = Join-Path $scriptDir 'stability-baseline.sh'

function Show-Usage {
    @'
OAISS CHAIN stability-first baseline runner (PowerShell wrapper)

Usage:
  powershell -ExecutionPolicy Bypass -File .\scripts\stability-baseline.ps1 [options]

Options:
  -WithMlHealth
  -WithBlockchain
  -WithDeliveryGuards
  -WithBackendTests
  -WithBackendVerify
  -WithFrontendTests
  -WithFrontendBuild
  -WithFrontendE2E
  -All
  -Help

This wrapper forwards to scripts/stability-baseline.sh.
'@
}

if ($Help) {
    Show-Usage
    exit 0
}

$bashCommand = Get-Command bash -ErrorAction SilentlyContinue
if (-not $bashCommand) {
    throw "bash was not found in PATH. Install Git Bash or another bash-compatible shell, then rerun this script."
}

$arguments = @($bashScript)

if ($WithMlHealth) { $arguments += '--with-ml-health' }
if ($WithBlockchain) { $arguments += '--with-blockchain' }
if ($WithDeliveryGuards) { $arguments += '--with-delivery-guards' }
if ($WithBackendTests) { $arguments += '--with-backend-tests' }
if ($WithBackendVerify) { $arguments += '--with-backend-verify' }
if ($WithFrontendTests) { $arguments += '--with-frontend-tests' }
if ($WithFrontendBuild) { $arguments += '--with-frontend-build' }
if ($WithFrontendE2E) { $arguments += '--with-frontend-e2e' }
if ($All) { $arguments += '--all' }

Push-Location $projectRoot
try {
    & $bashCommand.Source @arguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
