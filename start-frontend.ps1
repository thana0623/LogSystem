#Requires -Version 5.1
<#
.SYNOPSIS
    启动 logsys-ui 前端开发服务器
.EXAMPLE
    .\start-frontend.ps1
#>

$ErrorActionPreference = "Stop"
$uiDir = Join-Path $PSScriptRoot "logsys-ui"

Write-Host "`n==> 启动 logsys-ui" -ForegroundColor Cyan

# 检查 Node.js
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "    [ERR] 未找到 Node.js" -ForegroundColor Red; exit 1
}

# 安装依赖
$nodeModules = Join-Path $uiDir "node_modules"
if (-not (Test-Path $nodeModules)) {
    Write-Host "`n==> 安装前端依赖" -ForegroundColor Cyan
    Push-Location $uiDir
    npm install
    if ($LASTEXITCODE -ne 0) { Write-Host "    [ERR] npm install 失败" -ForegroundColor Red; Pop-Location; exit 1 }
    Pop-Location
    Write-Host "    [OK] 依赖安装完成" -ForegroundColor Green
}

Write-Host "`n  前端地址: http://localhost:3000" -ForegroundColor White
Write-Host ""

# 启动
Push-Location $uiDir
try {
    npm run dev
}
finally {
    Pop-Location
}
