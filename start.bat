@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Build and start CertMuse from the repository root.
rem Usage:
rem   start.bat        Rebuild the current workspace, verify services, and open the app.
rem Set CERTMUSE_NO_PAUSE=1 when invoking non-interactively.

set "PROJECT_ROOT=%~dp0"
if not "%~1"=="" (
    echo Usage: %~nx0
    exit /b 2
)

echo.
echo [1/4] Checking remote status and fingerprinting the current workspace...
pushd "%PROJECT_ROOT%" || exit /b 1
git fetch origin develop
set "FETCH_RESULT=%ERRORLEVEL%"
if not "%FETCH_RESULT%"=="0" (
    echo [WARN] Unable to check origin/develop. Continuing with the current workspace.
) else (
    set "REMOTE_AHEAD="
    for /f "delims=" %%I in ('git rev-list --count HEAD..origin/develop') do set "REMOTE_AHEAD=%%I"
    if not "!REMOTE_AHEAD!"=="0" echo [WARN] Current code is behind origin/develop by !REMOTE_AHEAD! commit^(s^). Continuing with the current version.
)
popd
set "SOURCE_REVISION="
for /f "usebackq delims=" %%I in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%tools\ops\get-local-source-revision.ps1"`) do set "SOURCE_REVISION=%%I"
if not defined SOURCE_REVISION (
    echo Unable to fingerprint the current workspace. Existing services were not changed.
    goto :failed
)
set "CERTMUSE_SOURCE_REVISION=%SOURCE_REVISION%"
echo Source revision: %SOURCE_REVISION%

echo.
echo [2/4] Building Docker application images with reusable caches...
pushd "%PROJECT_ROOT%infra\docker" || exit /b 1
docker compose build --pull backend frontend
set "COMPOSE_RESULT=%ERRORLEVEL%"
popd
if not "%COMPOSE_RESULT%"=="0" (
    echo Docker image build failed.
    set "FAILURE_CODE=%COMPOSE_RESULT%"
    goto :failed
)
set "SOURCE_REVISION_AFTER="
for /f "usebackq delims=" %%I in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%tools\ops\get-local-source-revision.ps1"`) do set "SOURCE_REVISION_AFTER=%%I"
if not "%SOURCE_REVISION_AFTER%"=="%SOURCE_REVISION%" (
    echo Source files changed during the build. Existing services were not changed.
    echo Run start.bat again to build one consistent revision.
    goto :failed
)

echo.
echo [3/4] Starting infrastructure and applying local database migrations...
pushd "%PROJECT_ROOT%infra\docker" || exit /b 1
docker compose up -d postgres redis minio minio-init
set "INFRA_RESULT=%ERRORLEVEL%"
if "%INFRA_RESULT%"=="0" (
    set "MINIO_INIT_ID="
    for /f "delims=" %%I in ('docker compose ps -a -q minio-init') do set "MINIO_INIT_ID=%%I"
    if not defined MINIO_INIT_ID (
        set "INFRA_RESULT=1"
    ) else (
        docker container wait !MINIO_INIT_ID! >nul
        for /f "delims=" %%I in ('docker inspect !MINIO_INIT_ID! --format "{{.State.ExitCode}}"') do set "MINIO_INIT_EXIT=%%I"
        if not "!MINIO_INIT_EXIT!"=="0" set "INFRA_RESULT=1"
    )
)
if "%INFRA_RESULT%"=="0" docker compose up -d --wait --wait-timeout 180 postgres redis minio
if "%INFRA_RESULT%"=="0" set "INFRA_RESULT=%ERRORLEVEL%"
popd
if not "%INFRA_RESULT%"=="0" (
    echo Infrastructure services did not become healthy.
    set "FAILURE_CODE=%INFRA_RESULT%"
    goto :failed
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%tools\ops\migrate-local-database.ps1"
if not "%ERRORLEVEL%"=="0" (
    echo Local database migration failed. Application startup is incomplete.
    set "FAILURE_CODE=%ERRORLEVEL%"
    goto :failed
)

echo.
echo [4/4] Replacing and verifying application services...
pushd "%PROJECT_ROOT%infra\docker" || exit /b 1
docker compose up -d --force-recreate --wait --wait-timeout 180 backend frontend
set "APP_RESULT=%ERRORLEVEL%"
popd
if not "%APP_RESULT%"=="0" (
    echo Application services did not become healthy. Run "docker compose logs backend" in infra\docker for details.
    set "FAILURE_CODE=%APP_RESULT%"
    goto :failed
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%tools\ops\verify-local-startup.ps1" -ComposeDirectory "%PROJECT_ROOT%infra\docker" -SourceRevision "%SOURCE_REVISION%" -OpenBrowser
set "VERIFY_RESULT=%ERRORLEVEL%"
if not "%VERIFY_RESULT%"=="0" (
    echo Application verification failed. The browser was not opened.
    set "FAILURE_CODE=%VERIFY_RESULT%"
    goto :failed
)

echo.
echo CertMuse is running and has been opened in your default browser.
exit /b 0

:failed
if not defined FAILURE_CODE set "FAILURE_CODE=1"
echo.
echo CertMuse startup stopped with exit code %FAILURE_CODE%.
if not defined CERTMUSE_NO_PAUSE pause
exit /b %FAILURE_CODE%
