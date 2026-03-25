param(
  [int]$BackendPort = 5931,
  [int]$FrontendPort = 5933,
  [int]$TimeoutSeconds = 120,
  [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "code\scripts\run_stack.ps1"

if (-not (Test-Path $scriptPath)) {
  throw "Stack script not found: $scriptPath"
}

powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
  -BackendPort $BackendPort `
  -FrontendPort $FrontendPort `
  -TimeoutSeconds $TimeoutSeconds `
  -OpenBrowser:$OpenBrowser
