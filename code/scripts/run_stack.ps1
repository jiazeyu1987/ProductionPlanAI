param(
  [int]$BackendPort = 5931,
  [int]$FrontendPort = 5932,
  [int]$TimeoutSeconds = 120,
  [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $repoRoot "logs"
$backendScript = Join-Path $PSScriptRoot "run_backend_local.ps1"
$frontendScript = Join-Path $PSScriptRoot "run_frontend.ps1"
$backendOutLog = Join-Path $logsDir "backend-local.out.log"
$backendErrLog = Join-Path $logsDir "backend-local.err.log"
$frontendOutLog = Join-Path $logsDir "frontend-dev.out.log"
$frontendErrLog = Join-Path $logsDir "frontend-dev.err.log"

New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

function Test-PortListening {
  param([int]$Port)
  return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-PortDown {
  param(
    [int]$Port,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    if (-not (Test-PortListening -Port $Port)) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function Wait-PortUp {
  param(
    [int]$Port,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    if (Test-PortListening -Port $Port) {
      return $true
    }
    Start-Sleep -Seconds 2
  }
  return $false
}

function Stop-ServiceByPort {
  param(
    [string]$Name,
    [int]$Port,
    [int]$TimeoutSec
  )

  $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if (-not $connections) {
    Write-Host "$Name not running on port $Port."
    return
  }

  $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($procId in $pids) {
    Write-Host "Stopping $Name process PID=$procId on port $Port ..."
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
  }

  if (-not (Wait-PortDown -Port $Port -TimeoutSec $TimeoutSec)) {
    throw "Failed to stop $Name on port $Port within $TimeoutSec seconds."
  }
  Write-Host "$Name stopped on port $Port."
}

function Start-ServiceScript {
  param(
    [string]$Name,
    [string]$ScriptPath,
    [string]$OutLog,
    [string]$ErrLog
  )

  if (-not (Test-Path $ScriptPath)) {
    throw "$Name startup script not found: $ScriptPath"
  }

  Write-Host "Starting $Name ..."
  Start-Process `
    -FilePath "powershell" `
    -ArgumentList "-NoLogo -NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`"" `
    -RedirectStandardOutput $OutLog `
    -RedirectStandardError $ErrLog `
    -WindowStyle Hidden | Out-Null
}

function Show-LastLog {
  param(
    [string]$Name,
    [string]$OutLog,
    [string]$ErrLog
  )

  Write-Host "[$Name] Last stdout:"
  if (Test-Path $OutLog) {
    Get-Content $OutLog -Tail 40
  } else {
    Write-Host "(no stdout log)"
  }

  Write-Host "[$Name] Last stderr:"
  if (Test-Path $ErrLog) {
    Get-Content $ErrLog -Tail 40
  } else {
    Write-Host "(no stderr log)"
  }
}

Stop-ServiceByPort -Name "backend" -Port $BackendPort -TimeoutSec $TimeoutSeconds
Stop-ServiceByPort -Name "frontend" -Port $FrontendPort -TimeoutSec $TimeoutSeconds

Start-ServiceScript -Name "backend" -ScriptPath $backendScript -OutLog $backendOutLog -ErrLog $backendErrLog
if (-not (Wait-PortUp -Port $BackendPort -TimeoutSec $TimeoutSeconds)) {
  Show-LastLog -Name "backend" -OutLog $backendOutLog -ErrLog $backendErrLog
  throw "Backend failed to start on port $BackendPort within $TimeoutSeconds seconds."
}
Write-Host "Backend started on port $BackendPort."

Start-ServiceScript -Name "frontend" -ScriptPath $frontendScript -OutLog $frontendOutLog -ErrLog $frontendErrLog
if (-not (Wait-PortUp -Port $FrontendPort -TimeoutSec $TimeoutSeconds)) {
  Show-LastLog -Name "frontend" -OutLog $frontendOutLog -ErrLog $frontendErrLog
  throw "Frontend failed to start on port $FrontendPort within $TimeoutSeconds seconds."
}
Write-Host "Frontend started on port $FrontendPort."

$frontendUrl = "http://localhost:$FrontendPort"
$backendUrl = "http://localhost:$BackendPort"
Write-Host ""
Write-Host "Stack ready."
Write-Host "Frontend: $frontendUrl"
Write-Host "Backend:  $backendUrl"
Write-Host "Logs:     $logsDir"

if ($OpenBrowser) {
  try {
    Start-Process $frontendUrl | Out-Null
    Write-Host "Browser opened: $frontendUrl"
  } catch {
    Write-Warning "Failed to open browser automatically. Open it manually: $frontendUrl"
  }
}
