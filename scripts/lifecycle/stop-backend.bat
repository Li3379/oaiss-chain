@echo off
setlocal enabledelayedexpansion

:: =============================================
:: OAISS CHAIN - Windows backend stop script
:: Stops the Java process listening on port 8080
:: =============================================

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1; " ^
  "if (-not $conn) { Write-Output '[STOP] No backend process is listening on :8080'; exit 0 } " ^
  "$proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue; " ^
  "if (-not $proc) { Write-Output ('[STOP] Port 8080 is owned by PID ' + $conn.OwningProcess + ', but the process no longer exists'); exit 0 } " ^
  "if ($proc.ProcessName -ne 'java') { Write-Output ('[STOP] Port 8080 is owned by PID ' + $proc.Id + ' (' + $proc.ProcessName + '), not java'); exit 0 } " ^
  "Write-Output ('[STOP] Stopping backend process ' + $proc.Id); " ^
  "Stop-Process -Id $proc.Id -Force; " ^
  "Write-Output '[STOP] Backend stop command completed'"

set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
