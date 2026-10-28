@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

:: =============================================
:: OAISS CHAIN - Windows backend start script
:: Usage: scripts\start-backend.bat [options]
::   --with-fabric    use local,fabric profiles
::   --port <port>    override server.port for side-by-side verification
:: =============================================

for %%I in ("%~dp0..\..") do set "PROJECT_ROOT=%%~fI"
cd /d "%PROJECT_ROOT%"

set "WITH_FABRIC=false"
set "SERVER_PORT="

:parse_args
if "%~1"=="" goto :args_done
if "%~1"=="--with-fabric" ( set "WITH_FABRIC=true" & shift & goto :parse_args )
if "%~1"=="--port" (
    if "%~2"=="" (
        echo [ERROR] 缺少 --port 的端口值
        exit /b 1
    )
    set "SERVER_PORT=%~2"
    shift
    shift
    goto :parse_args
)
echo [ERROR] 未知参数: %~1
exit /b 1

:args_done

if exist "%PROJECT_ROOT%\.env" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%PROJECT_ROOT%\.env") do (
        set "line=%%a"
        if not "!line!"=="" if not "!line:~0,1!"=="#" (
            set "%%a=%%b"
        )
    )
)

if "%WITH_FABRIC%"=="true" (
    set "SPRING_PROFILES_ACTIVE=local,fabric"
    set "SPRING_ARGS=-Dspring-boot.run.profiles=local,fabric"
) else (
    set "SPRING_PROFILES_ACTIVE=local"
    set "SPRING_ARGS=-Dspring-boot.run.profiles=local"
)

if not "%SERVER_PORT%"=="" (
    set "SPRING_ARGS=%SPRING_ARGS% -Dspring-boot.run.arguments=--server.port=%SERVER_PORT%"
)

echo.
echo ========================================
echo  OAISS CHAIN Backend
echo  Profiles: %SPRING_PROFILES_ACTIVE%
if not "%SERVER_PORT%"=="" echo  Port: %SERVER_PORT%
echo ========================================
echo.

set "PORT_TO_CHECK=8080"
if not "%SERVER_PORT%"=="" set "PORT_TO_CHECK=%SERVER_PORT%"

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT_TO_CHECK%" ^| findstr "LISTENING"') do (
    echo [WARN] Port %PORT_TO_CHECK% is already in use by PID %%p
    echo [WARN] Run scripts\stop-backend.bat first, then retry.
    endlocal
    exit /b 1
)

cd /d "%PROJECT_ROOT%\oaiss-chain-backend"
call mvn spring-boot:run %SPRING_ARGS%

endlocal
