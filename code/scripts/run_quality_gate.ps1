$ErrorActionPreference = "Stop"

$codeRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $codeRoot
$rootGate = Join-Path $repoRoot "scripts\run_quality_gate.ps1"

if (Test-Path $rootGate) {
  powershell -ExecutionPolicy Bypass -File $rootGate
  exit $LASTEXITCODE
}

Write-Host "Root quality gate script not found, fallback to local verify."
powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "run_verify.ps1")
