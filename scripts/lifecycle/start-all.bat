@echo off
setlocal enabledelayedexpansion

:: =============================================
:: OAISS CHAIN - Windows start-all script
:: Usage: scripts\start-all.bat [options]
::   --with-fabric    start Fabric network too
::   --skip-frontend  skip frontend dev server
::   --skip-backend   skip backend dev server
::   --infra-only     start infra only
:: =============================================

for %%I in ("%~dp0..\..") do set "PROJECT_ROOT=%%~fI"
cd /d "%PROJECT_ROOT%"
set "SCRIPT_DIR=%~dp0"

set "WITH_FABRIC=false"
set "SKIP_FRONTEND=false"
set "SKIP_BACKEND=false"
set "INFRA_ONLY=false"

:parse_args
if "%~1"=="" goto :args_done
if "%~1"=="--with-fabric" (
    set "WITH_FABRIC=true"
    shift
    goto :parse_args
)
if "%~1"=="--skip-frontend" (
    set "SKIP_FRONTEND=true"
    shift
    goto :parse_args
)
if "%~1"=="--skip-backend" (
    set "SKIP_BACKEND=true"
    shift
    goto :parse_args
)
if "%~1"=="--infra-only" (
    set "INFRA_ONLY=true"
    shift
    goto :parse_args
)
echo [ERROR] Unknown argument: %~1
exit /b 1

:args_done

set "ENV_ARG="
if exist "%PROJECT_ROOT%\.env" (
    set "ENV_ARG=--env-file "%PROJECT_ROOT%\.env""
    for /f "usebackq tokens=1,* delims==" %%a in ("%PROJECT_ROOT%\.env") do (
        set "line=%%a"
        if not "!line!"=="" if not "!line:~0,1!"=="#" (
            set "%%a=%%b"
        )
    )
)

for /f "delims=" %%I in ('wsl.exe wslpath -a "%PROJECT_ROOT%\scripts\bootstrap-fabric.sh" 2^>nul') do set "BOOTSTRAP_FABRIC_WSL=%%I"

echo.
echo ========================================
echo  OAISS CHAIN Start
echo ========================================
echo.

:: ============================================================
:: 1. Infrastructure (MySQL, Redis, S3 LocalStack, ML)
:: ============================================================
echo [START] Starting infra services...
docker compose %ENV_ARG% -f "%PROJECT_ROOT%\deploy\compose\docker-compose.infra.yml" up -d
if errorlevel 1 (
    echo [ERROR] Failed to start infra services
    exit /b 1
)

echo [START] Waiting for MySQL...
set /a MYSQL_WAIT=0
:wait_mysql
docker inspect --format="{{.State.Health.Status}}" oaiss-mysql 2>nul | findstr "healthy" >nul
if not errorlevel 1 goto :mysql_ready
set /a MYSQL_WAIT+=1
if !MYSQL_WAIT! GEQ 60 (
    echo [WARN] MySQL did not become healthy within 180 seconds, continuing anyway
    goto :mysql_ready
)
ping -n 3 127.0.0.1 >nul
goto :wait_mysql
:mysql_ready
echo [START] MySQL is ready

echo [START] Waiting for Redis...
set /a REDIS_WAIT=0
:wait_redis
docker inspect --format="{{.State.Health.Status}}" oaiss-redis 2>nul | findstr "healthy" >nul
if not errorlevel 1 goto :redis_ready
set /a REDIS_WAIT+=1
if !REDIS_WAIT! GEQ 30 (
    echo [WARN] Redis did not become healthy within 90 seconds, continuing anyway
    goto :redis_ready
)
ping -n 3 127.0.0.1 >nul
goto :wait_redis
:redis_ready
echo [START] Redis is ready

echo [START] Waiting for S3 (LocalStack)...
set /a S3_WAIT=0
:wait_s3
docker inspect --format="{{.State.Health.Status}}" oaiss-s3 2>nul | findstr "healthy" >nul
if not errorlevel 1 goto :s3_ready
set /a S3_WAIT+=1
if !S3_WAIT! GEQ 60 (
    echo [WARN] S3 did not become healthy within 180 seconds, continuing anyway
    goto :s3_ready
)
ping -n 3 127.0.0.1 >nul
goto :wait_s3
:s3_ready
echo [START] S3 (LocalStack) is ready

echo [START] Waiting for ML Service...
set /a ML_WAIT=0
:wait_ml
docker inspect --format="{{.State.Health.Status}}" oaiss-ml-service 2>nul | findstr "healthy" >nul
if not errorlevel 1 goto :ml_ready
set /a ML_WAIT+=1
if !ML_WAIT! GEQ 30 (
    echo [WARN] ML Service did not become healthy within 60 seconds, continuing anyway
    goto :ml_skip
)
ping -n 3 127.0.0.1 >nul
goto :wait_ml
:ml_ready
echo [START] ML Service is ready
:ml_skip

if "%INFRA_ONLY%"=="true" (
    echo [START] Infra-only mode enabled
    goto :done
)

:: ============================================================
:: 2. Fabric network (optional)
::    NOTE: Labels MUST NOT be inside () blocks — cmd.exe breaks
::    goto-to-label inside parenthesized blocks.
:: ============================================================
if not "%WITH_FABRIC%"=="true" goto :skip_fabric

echo [START] Starting Fabric network...
docker compose %ENV_ARG% -f "%PROJECT_ROOT%\deploy\compose\docker-compose.fabric.yml" up -d
if errorlevel 1 (
    echo [WARN] Fabric start encountered stale network, retrying with --force-recreate...
    docker compose %ENV_ARG% -f "%PROJECT_ROOT%\deploy\compose\docker-compose.fabric.yml" up -d --force-recreate
    if errorlevel 1 (
        echo [ERROR] Failed to start Fabric network
        exit /b 1
    )
)

echo [START] Waiting for Fabric Orderer...
set /a ORDERER_WAIT=0
:wait_orderer
docker inspect --format="{{.State.Health.Status}}" orderer.example.com 2>nul | findstr "healthy" >nul
if not errorlevel 1 goto :orderer_ready
set /a ORDERER_WAIT+=1
if !ORDERER_WAIT! GEQ 30 (
    echo [WARN] Orderer did not become healthy within 60 seconds
    goto :orderer_ready
)
ping -n 3 127.0.0.1 >nul
goto :wait_orderer
:orderer_ready
echo [START] Orderer is ready

echo [START] Waiting for Fabric Peer...
set /a PEER_WAIT=0
:wait_peer
docker inspect --format="{{.State.Health.Status}}" peer0.org1.example.com 2>nul | findstr "healthy" >nul
if not errorlevel 1 goto :peer_ready
set /a PEER_WAIT+=1
if !PEER_WAIT! GEQ 30 (
    echo [WARN] Peer did not become healthy within 60 seconds
    goto :peer_ready
)
ping -n 3 127.0.0.1 >nul
goto :wait_peer
:peer_ready
echo [START] Peer is ready

echo [START] Waiting for Fabric CA...
set /a CA_WAIT=0
:wait_ca
docker inspect --format="{{.State.Health.Status}}" ca.org1.example.com 2>nul | findstr "healthy" >nul
if not errorlevel 1 goto :ca_ready
set /a CA_WAIT+=1
if !CA_WAIT! GEQ 30 (
    echo [WARN] Fabric CA did not become healthy within 60 seconds
    goto :ca_ready
)
ping -n 3 127.0.0.1 >nul
goto :wait_ca
:ca_ready
echo [START] Fabric CA is ready

echo [START] Bootstrapping Fabric channel and chaincode...
if not defined BOOTSTRAP_FABRIC_WSL (
    echo [ERROR] Failed to resolve WSL path for bootstrap-fabric.sh
    exit /b 1
)
wsl.exe bash "%BOOTSTRAP_FABRIC_WSL%"
if errorlevel 1 (
    echo [ERROR] Fabric bootstrap failed
    exit /b 1
)
goto :after_fabric

:skip_fabric
echo [WARN] Fabric startup skipped

:after_fabric

:: ============================================================
:: 3. Backend (Spring Boot)
:: ============================================================
if "%SKIP_BACKEND%"=="true" (
    echo [WARN] Backend startup skipped
    goto :skip_backend
)

set "BACKEND_PROFILES=local"
if "%WITH_FABRIC%"=="true" set "BACKEND_PROFILES=local,fabric"

echo [START] Starting backend (profiles: %BACKEND_PROFILES%)...
cd /d "%PROJECT_ROOT%\oaiss-chain-backend"
start "OAISS Backend" cmd /c "mvn spring-boot:run -Dspring-boot.run.profiles=%BACKEND_PROFILES%"
cd /d "%PROJECT_ROOT%"
echo [START] Backend window launched, waiting for health...

set /a BACKEND_WAIT_COUNT=0
:wait_backend
ping -n 6 127.0.0.1 >nul
curl -sf http://localhost:8080/api/v1/actuator/health >nul 2>&1
if not errorlevel 1 goto :backend_ready
curl -sf http://localhost:8080/api/v1/actuator/health/liveness >nul 2>&1
if not errorlevel 1 goto :backend_ready
set /a BACKEND_WAIT_COUNT+=1
if !BACKEND_WAIT_COUNT! GEQ 24 (
    echo [ERROR] Backend did not become healthy within 120 seconds
    echo [ERROR] Check the OAISS Backend window for error details
    exit /b 1
)
goto :wait_backend

:backend_ready
echo [START] Backend is ready

:skip_backend

:: ============================================================
:: 4. Frontend (Vue 3)
:: ============================================================
if "%SKIP_FRONTEND%"=="true" (
    echo [WARN] Frontend startup skipped
    goto :skip_frontend
)

echo [START] Starting frontend...
cd /d "%PROJECT_ROOT%\oaiss-chain-frontend"
start "OAISS Frontend" cmd /c "npm run dev"
cd /d "%PROJECT_ROOT%"
echo [START] Frontend window started

:skip_frontend

:done
echo.
echo ========================================
echo  OAISS CHAIN services started
echo    Backend:  http://localhost:8080/api/v1
echo    Frontend: http://localhost:5173
echo    ML:       http://localhost:8001
echo    S3:       http://127.0.0.1:9004 (LocalStack S3 API)
if "%WITH_FABRIC%"=="true" (
    echo    Orderer:  gRPC 127.0.0.1:7050
    echo    Peer:     gRPC 127.0.0.1:7051
)
echo ========================================
echo.
echo Use scripts\stop-all.bat to stop all services.
echo.

endlocal & exit /b 0