@echo off
REM LogSystem 开发环境启动（批处理包装器）
REM 用法: start-dev.bat [--backend-only] [--frontend-only] [--build]

setlocal

set "PS_ARGS="
if "%~1"=="--backend-only"  set "PS_ARGS=-BackendOnly"
if "%~1"=="--frontend-only" set "PS_ARGS=-FrontendOnly"
if "%~2"=="--build"         set "PS_ARGS=%PS_ARGS% -Build"

powershell -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1" %PS_ARGS%
