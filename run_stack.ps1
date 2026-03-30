param(
  [int]$BackendPort = 5931,
  [int]$FrontendPort = 5932,
  [int]$TimeoutSeconds = 120,
  [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "code\scripts\run_stack.ps1"

if (-not (Test-Path $scriptPath)) {
  throw "Stack script not found: $scriptPath"
}

$argsList = @(
  "-NoLogo",
  "-NoProfile",
  "-ExecutionPolicy",
  "Bypass",
  "-File",
  $scriptPath,
  "-BackendPort",
  $BackendPort,
  "-FrontendPort",
  $FrontendPort,
  "-TimeoutSeconds",
  $TimeoutSeconds
)

if ($OpenBrowser.IsPresent) {
  $argsList += "-OpenBrowser"
}

& powershell @argsList
