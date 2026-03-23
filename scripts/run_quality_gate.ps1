param(
    [string]$Python = "python"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

Push-Location $RepoRoot
try {
    & $Python "scripts/check_artifacts.py"
    & $Python "scripts/validate_erp_extract.py" --generate-template (Join-Path $env:TEMP "erp_extract_template_ci.xlsx")
    Write-Host "P0 quality gate passed."
}
finally {
    Pop-Location
}
