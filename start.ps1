#Requires -Version 7.0
# Локальный запуск Windows: ключи отключены только явно выбранным профилем dev.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'Use ./start.sh on macOS/Linux.' }
. (Join-Path $PSScriptRoot 'scripts/windows-common.ps1')
$lock = Enter-ProjectLock
$started = [Collections.Generic.List[string]]::new()
$environmentBackup = @{}
$success = $false
try {
    foreach ($name in @('SPRING_PROFILES_ACTIVE', 'SERVER_PORT', 'SERVER_ADDRESS', 'BACKEND_URL')) {
        $environmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    foreach ($tool in @('docker.exe', 'java.exe', 'node.exe', 'npm.cmd')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Required command: $tool" }
    }
    foreach ($service in @('backend', 'frontend')) {
        $path = Get-StatePath $service
        if (Test-Path -LiteralPath $path) {
            $state = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
            if (Get-Process -Id $state.id -ErrorAction SilentlyContinue) {
                throw 'A recorded process is running. Run .\stop.ps1 first.'
            }
        }
    }
    Push-Location $Root
    try {
        Write-Host 'Starting PostgreSQL (Docker Desktop must be running)...'
        Invoke-Checked 'docker.exe' @('compose', 'up', '-d', '--wait', '--wait-timeout', '120', 'db')
        Push-Location (Join-Path $Root 'backend')
        try {
            Write-Host 'Building backend...'
            Invoke-Checked '.\gradlew.bat' @('bootJar', '--console=plain')
        } finally { Pop-Location }
        Push-Location (Join-Path $Root 'frontend')
        try {
            Write-Host 'Preparing frontend...'
            Invoke-Checked 'npm.cmd' @('ci')
            Invoke-Checked 'npm.cmd' @('--prefix', 'tools/openapi', 'ci')
            Invoke-Checked 'npm.cmd' @('run', 'generate:api')
        } finally { Pop-Location }
    } finally { Pop-Location }

    $backendPort = Get-FreePort $(if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 })
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = "$backendPort"
    $jar = Join-Path $Root 'backend\build\libs\org-structure-0.1.0.jar'
    $backend = Start-ServiceProcess 'backend' (Get-Command java.exe).Source @('-jar', $jar) $Root
    $started.Add('backend')
    Save-Service 'backend' $backend $jar
    Wait-ServiceHttp $backend "http://127.0.0.1:$backendPort/actuator/health" 'backend'

    # Выбор после запуска backend исключает совпадение двух запрошенных портов.
    $frontendPort = Get-FreePort $(if ($env:FRONTEND_PORT) { [int]$env:FRONTEND_PORT } else { 4204 })
    $env:BACKEND_URL = "http://127.0.0.1:$backendPort"
    $cli = Join-Path $Root 'frontend\node_modules\@angular\cli\bin\ng.js'
    $frontend = Start-ServiceProcess 'frontend' (Get-Command node.exe).Source `
        @($cli, 'serve', '--host', '127.0.0.1', '--port', "$frontendPort", '--proxy-config', 'proxy.conf.cjs') `
        (Join-Path $Root 'frontend')
    $started.Add('frontend')
    Save-Service 'frontend' $frontend $cli
    Wait-ServiceHttp $frontend "http://127.0.0.1:$frontendPort/" 'frontend'
    $tree = Invoke-RestMethod -Uri "http://127.0.0.1:$frontendPort/api/v1/departments/tree" -TimeoutSec 10
    if (-not $tree.id) { throw 'Proxy check returned an invalid department tree.' }
    $success = $true
    Write-Host "UI:      http://127.0.0.1:$frontendPort"
    Write-Host "Backend: http://127.0.0.1:$backendPort"
    Write-Host 'Stop: .\stop.ps1'
} finally {
    if (-not $success) {
        for ($index = $started.Count - 1; $index -ge 0; $index--) {
            try { Stop-ServiceProcess $started[$index] } catch { Write-Warning $_ }
        }
        Write-Warning 'Startup failed. PostgreSQL data was preserved; check .run/windows.*.log.'
    }
    foreach ($name in $environmentBackup.Keys) {
        [Environment]::SetEnvironmentVariable($name, $environmentBackup[$name], 'Process')
    }
    $lock.Dispose()
}
