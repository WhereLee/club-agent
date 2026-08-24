# 本地开发启动脚本：从项目根 .env 注入环境变量后启动
# 用法：powershell -File scripts\start-dev.ps1
# 注意：Spring Boot 不读 .env，必须经本脚本注入；密钥类缺失会 fail-fast 拒绝启动

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "..\..\.env"

if (-not (Test-Path $envFile)) {
    Write-Error "未找到 .env（参考 .env.example 创建）"
    exit 1
}

# 注入 .env 到进程环境（仅取 KEY=VALUE 行，忽略注释/空行）
Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $kv = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($kv[0].Trim(), $kv[1].Trim(), "Process")
}

Write-Host "环境变量注入完成，启动 spring-boot:run ..." -ForegroundColor Green
mvn -f (Join-Path $PSScriptRoot "..\pom.xml") spring-boot:run
