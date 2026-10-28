@echo off
setlocal enabledelayedexpansion

:: =============================================
:: OAISS CHAIN - Windows unified stop script
:: scripts\stop-all.bat [--with-fabric]
::   --with-fabric    force stop Fabric network
:: =============================================
:: OAISS CHAIN - Windows 统一停止脚本
:: 用法: scripts\stop-all.bat [--with-fabric]
::   --with-fabric    同时停止 Fabric 网络
:: =============================================

for %%I in ("%~dp0..\..") do set "PROJECT_ROOT=%%~fI"
cd /d "%PROJECT_ROOT%"

set "FORCE_FABRIC=false"

:parse_args
if "%~1"=="" goto :args_done
if "%~1"=="--with-fabric" (
    set "FORCE_FABRIC=true"
    shift
    goto :parse_args
)
echo [ERROR] Unknown argument: %~1
exit /b 1

:args_done

set "ENV_ARG="
if exist "%PROJECT_ROOT%\.env" set "ENV_ARG=--env-file "%PROJECT_ROOT%\.env""

echo.
echo ========================================
echo  OAISS CHAIN Stop
echo ========================================
echo.

:: 1. Stop backend (Java process on :8080)
echo [STOP] Stopping backend process...
call "%PROJECT_ROOT%\scripts\stop-backend.bat" >nul 2>&1

:: 2. Stop frontend (node on :5173)
:: NOTE: Get-Process has no CommandLine property, so match via CIM
:: (2026-09-07: old Get-Process filter silently matched nothing and
::  left the vite dev server running after stop-all).
echo [STOP] Stopping frontend process...
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='node.exe'\" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*vite*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>&1

:: 3. Auto-detect and stop Fabric containers (before tearing down network)
set "FABRIC_RUNNING=false"
docker ps -a --format "{{.Names}}" 2>nul | findstr "orderer.example.com" >nul 2>&1
if not errorlevel 1 set "FABRIC_RUNNING=true"
docker ps -a --format "{{.Names}}" 2>nul | findstr "peer0.org1.example.com" >nul 2>&1
if not errorlevel 1 set "FABRIC_RUNNING=true"

if "%FABRIC_RUNNING%"=="true" goto :stop_fabric
if "%FORCE_FABRIC%"=="true" goto :stop_fabric
goto :skip_fabric_stop

:stop_fabric
echo [STOP] Stopping Fabric network...
docker compose %ENV_ARG% -f "%PROJECT_ROOT%\deploy\compose\docker-compose.fabric.yml" down
if errorlevel 1 (
    echo [WARN] Failed to stop Fabric network
)
goto :after_fabric_done

:skip_fabric_stop
echo [SKIP] Fabric containers not running

:after_fabric_done

:: 4. Stop infrastructure containers (MySQL, Redis, MinIO, ML)
echo [STOP] Stopping infrastructure containers...
docker compose %ENV_ARG% -f "%PROJECT_ROOT%\deploy\compose\docker-compose.infra.yml" down

:: 5. Stop full-stack containers (if started via docker-compose.yml)
docker compose %ENV_ARG% -f "%PROJECT_ROOT%\docker-compose.yml" down 2>nul

echo.
echo [STOP] All services stopped.
echo.

endlocal & exit /b 0