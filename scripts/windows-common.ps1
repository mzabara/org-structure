# Общие проверки исключают остановку чужого процесса при повторном использовании PID.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$RunDir = Join-Path $Root '.run'
[void][IO.Directory]::CreateDirectory($RunDir)

function Enter-ProjectLock {
    try {
        return [IO.File]::Open(
            (Join-Path $RunDir 'windows.lock'), [IO.FileMode]::OpenOrCreate,
            [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch {
        throw 'Another start/stop is running. Wait for it to finish.'
    }
}

function Invoke-Checked {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Program failed (exit code $LASTEXITCODE)."
    }
}

function Get-StatePath {
    param([string]$Service)
    return Join-Path $RunDir "windows.$Service.json"
}

function Save-Service {
    param([string]$Service, [Diagnostics.Process]$Process, [string]$Marker)
    @{
        id = $Process.Id
        startTicks = $Process.StartTime.ToUniversalTime().Ticks.ToString()
        marker = $Marker
    } | ConvertTo-Json | Set-Content -LiteralPath (Get-StatePath $Service) -Encoding utf8
}

function Stop-ServiceProcess {
    param([string]$Service)
    $path = Get-StatePath $Service
    if (-not (Test-Path -LiteralPath $path)) { return }
    $state = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    $process = Get-Process -Id $state.id -ErrorAction SilentlyContinue
    if ($null -ne $process) {
        $expectedMarker = if ($Service -eq 'frontend') {
            Join-Path $Root 'frontend\node_modules\@angular\cli\bin\ng.js'
        } else {
            Join-Path $Root 'backend\build\libs\org-structure-0.1.0.jar'
        }
        $command = (Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)").CommandLine
        if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne $state.startTicks -or
            $state.marker -ne $expectedMarker -or
            [string]::IsNullOrEmpty($command) -or
            -not $command.Contains($expectedMarker)) {
            throw "PID $($process.Id) does not belong to this project. Nothing was stopped."
        }
        Stop-Process -InputObject $process
        if (-not $process.WaitForExit(30000)) {
            throw "$Service did not stop. Its state file was preserved."
        }
    }
    Remove-Item -LiteralPath $path
}

function Get-FreePort {
    param([int]$First)
    if ($First -lt 1 -or $First -gt 65535) { throw "Invalid port: $First" }
    for ($port = $First; $port -le 65535; $port++) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
        try {
            $listener.Start()
            return $port
        } catch [Net.Sockets.SocketException] {
            continue
        } finally {
            $listener.Stop()
        }
    }
    throw 'No free HTTP port found.'
}

function Wait-ServiceHttp {
    param([Diagnostics.Process]$Process, [string]$Url, [string]$Service)
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $Process.Refresh()
        if ($Process.HasExited) { throw "$Service exited. Check .run/windows.$Service.*.log." }
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch {
            # Отказ соединения ожидаем, пока сервер ещё запускается.
        }
        Start-Sleep -Seconds 1
    }
    throw "Timeout waiting for $Url. Check .run/windows.$Service.*.log."
}

function Start-ServiceProcess {
    param([string]$Service, [string]$Program, [string[]]$Arguments, [string]$Directory)
    # Start-Process объединяет ArgumentList: кавычки сохраняют пути с пробелами.
    $quoted = @($Arguments | ForEach-Object { '"' + $_ + '"' })
    return Start-Process -FilePath $Program -ArgumentList $quoted -WorkingDirectory $Directory `
        -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $RunDir "windows.$Service.stdout.log") `
        -RedirectStandardError (Join-Path $RunDir "windows.$Service.stderr.log")
}
