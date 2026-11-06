#Requires -Version 7.0
# Останавливает только записанные процессы проекта, не удаляя том PostgreSQL.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'Use ./stop.sh on macOS/Linux.' }
. (Join-Path $PSScriptRoot 'scripts/windows-common.ps1')
$lock = Enter-ProjectLock
try {
    Stop-ServiceProcess 'frontend'
    Stop-ServiceProcess 'backend'
    Push-Location $Root
    try {
        Invoke-Checked 'docker.exe' @('compose', 'stop', 'db')
    } finally { Pop-Location }
    Write-Host 'Project stopped. PostgreSQL data was preserved.'
} finally {
    $lock.Dispose()
}
