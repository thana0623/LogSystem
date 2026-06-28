#Requires -Version 5.1
<#
.SYNOPSIS
    启动 logsys-api 后端开发服务器
.PARAMETER Build
    启动前先构建
.EXAMPLE
    .\start-backend.ps1
    .\start-backend.ps1 -Build
#>

param([switch]$Build)

$ErrorActionPreference = "Stop"
$apiDir = Join-Path $PSScriptRoot "logsys-api"

Write-Host "`n==> 启动 logsys-api" -ForegroundColor Cyan

# 检查 Java + Maven
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "    [ERR] 未找到 Java" -ForegroundColor Red; exit 1
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "    [ERR] 未找到 Maven" -ForegroundColor Red; exit 1
}

# 构建
if ($Build) {
    Write-Host "`n==> 构建后端" -ForegroundColor Cyan
    Push-Location $apiDir
    mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { Write-Host "    [ERR] 构建失败" -ForegroundColor Red; Pop-Location; exit 1 }
    Pop-Location
    Write-Host "    [OK] 构建完成" -ForegroundColor Green
}

# 设置环境变量
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:POSTGRES_PASSWORD = $env:POSTGRES_PASSWORD ?? "logsys"
$env:CLICKHOUSE_PASSWORD = $env:CLICKHOUSE_PASSWORD ?? "logsys"

Write-Host "`n  API 地址: http://localhost:8080" -ForegroundColor White
Write-Host "  Profile:  dev`n" -ForegroundColor DarkGray

# 启动
Push-Location $apiDir
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
