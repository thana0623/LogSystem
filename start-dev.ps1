#Requires -Version 5.1
<#
.SYNOPSIS
    LogSystem 开发环境启动脚本
.DESCRIPTION
    同时启动后端 (Spring Boot) 和前端 (Next.js) 开发服务器
.PARAMETER BackendOnly
    仅启动后端
.PARAMETER FrontendOnly
    仅启动前端
.PARAMETER Build
    启动前先构建后端
.EXAMPLE
    .\start-dev.ps1
    .\start-dev.ps1 -BackendOnly
    .\start-dev.ps1 -FrontendOnly -Build
#>

param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot

# 颜色输出
function Write-Step { param([string]$Msg) Write-Host "`n==> $Msg" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Msg) Write-Host "    [OK] $Msg" -ForegroundColor Green }
function Write-Err  { param([string]$Msg) Write-Host "    [ERR] $Msg" -ForegroundColor Red }

# 检查依赖
function Test-Prerequisites {
    Write-Step "检查环境依赖"

    if (-not $FrontendOnly) {
        $java = Get-Command java -ErrorAction SilentlyContinue
        if (-not $java) { Write-Err "未找到 Java"; exit 1 }
        $mvn = Get-Command mvn -ErrorAction SilentlyContinue
        if (-not $mvn) { Write-Err "未找到 Maven"; exit 1 }
        Write-Ok "Java + Maven"
    }

    if (-not $BackendOnly) {
        $node = Get-Command node -ErrorAction SilentlyContinue
        if (-not $node) { Write-Err "未找到 Node.js"; exit 1 }
        Write-Ok "Node.js"
    }
}

# 启动后端
function Start-Backend {
    Write-Step "启动后端 (logsys-api)"

    $apiDir = Join-Path $ProjectRoot "logsys-api"
    if (-not (Test-Path $apiDir)) { Write-Err "logsys-api 目录不存在"; exit 1 }

    if ($Build) {
        Write-Step "构建后端"
        Push-Location $apiDir
        mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) { Write-Err "Maven 构建失败"; Pop-Location; exit 1 }
        Pop-Location
        Write-Ok "构建完成"
    }

    # 设置环境变量（开发模式默认值）
    $env:SPRING_PROFILES_ACTIVE = "dev"
    $env:POSTGRES_PASSWORD = $env:POSTGRES_PASSWORD ?? "logsys"
    $env:CLICKHOUSE_PASSWORD = $env:CLICKHOUSE_PASSWORD ?? "logsys"

    $backendJob = Start-Job -Name "logsys-api" -ScriptBlock {
        param($dir)
        Set-Location $dir
        mvn spring-boot:run 2>&1
    } -ArgumentList $apiDir

    Write-Ok "后端已启动 (Job: $($backendJob.Id))"
    return $backendJob
}

# 启动前端
function Start-Frontend {
    Write-Step "启动前端 (logsys-ui)"

    $uiDir = Join-Path $ProjectRoot "logsys-ui"
    if (-not (Test-Path $uiDir)) { Write-Err "logsys-ui 目录不存在"; exit 1 }

    # 检查依赖是否安装
    $nodeModules = Join-Path $uiDir "node_modules"
    if (-not (Test-Path $nodeModules)) {
        Write-Step "安装前端依赖"
        Push-Location $uiDir
        npm install
        if ($LASTEXITCODE -ne 0) { Write-Err "npm install 失败"; Pop-Location; exit 1 }
        Pop-Location
        Write-Ok "依赖安装完成"
    }

    $frontendJob = Start-Job -Name "logsys-ui" -ScriptBlock {
        param($dir)
        Set-Location $dir
        npm run dev 2>&1
    } -ArgumentList $uiDir

    Write-Ok "前端已启动 (Job: $($frontendJob.Id))"
    return $frontendJob
}

# 主流程
Write-Host @"

  _                   ___
 | |   ___  ___  _ _ / __| __ __ _ _ _  _ _  ___ _ _
 | |__/ _ \/ _ \| ' \\__ \/ _/ _` | ' \| ' \/ -_) '_|
 |____\___/\___/|_||_|___/\__\__,_|_||_|_||_\___|_|
                                        开发环境启动

"@ -ForegroundColor Yellow

Test-Prerequisites

$jobs = @()

if (-not $FrontendOnly) { $jobs += Start-Backend }
if (-not $BackendOnly)  { $jobs += Start-Frontend }

Write-Step "服务启动完成"
Write-Host ""
Write-Host "  后端 API:  http://localhost:8080" -ForegroundColor White
Write-Host "  前端 UI:   http://localhost:3000" -ForegroundColor White
Write-Host ""
Write-Host "  按 Ctrl+C 停止所有服务" -ForegroundColor DarkGray
Write-Host ""

# 等待并输出日志
try {
    while ($true) {
        foreach ($job in $jobs) {
            $output = Receive-Job $job -ErrorAction SilentlyContinue
            if ($output) {
                $prefix = if ($job.Name -eq "logsys-api") { "[API]" } else { "[UI] " }
                $color  = if ($job.Name -eq "logsys-api") { "Cyan" } else { "Magenta" }
                foreach ($line in $output) {
                    Write-Host "$prefix $line" -ForegroundColor $color
                }
            }
        }

        # 检查是否有 Job 异常退出
        foreach ($job in $jobs) {
            if ($job.State -eq "Failed") {
                Write-Err "$($job.Name) 异常退出"
                $jobs | Remove-Job -Force
                exit 1
            }
        }

        Start-Sleep -Milliseconds 200
    }
}
finally {
    Write-Step "正在停止服务..."
    $jobs | Stop-Job -PassThru | Remove-Job -Force
    Write-Ok "已停止"
}
