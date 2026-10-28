param(
    [switch]$WithFabric
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$backendBatch = Join-Path $scriptDir 'start-backend.bat'

$cmdArgs = "/c `"$backendBatch`""
if ($WithFabric) {
    $cmdArgs = "/c `"$backendBatch`" --with-fabric"
}

$startProcessParams = @{
    FilePath = 'cmd.exe'
    ArgumentList = $cmdArgs
    WorkingDirectory = $projectRoot
    WindowStyle = 'Hidden'
}

Start-Process @startProcessParams